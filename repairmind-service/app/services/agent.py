"""受控工具调用 Agent（对应手册 P2 · Day32【AI岗王牌】）。

核心思路：
- LLM 只负责"决定调哪个工具、传什么参数"，不直接访问数据；
- 工具在服务端执行，且身份（user_id / dept_id）由用户上下文决定，不由 LLM 决定；
- 数据访问走 data_source：本地样例 or EquipOps Java 接口（带 JWT 二次鉴权）；
- 写操作（create_repair）先生成一次性确认 Token，**不落库**，确认后才真正落库；
- 所有工具调用记录进 tool_trace，构成审计痕迹。

stub 模式（RM_LLM_STUB=1）走确定性 stub_agent，无需 API Key 即可演示完整循环。
"""
from __future__ import annotations

import json
import re

from app.core.config import settings
from app.services import data_source
from app.services.llm_client import llm_client
from app.services.memory import memory
from app.services.repair_write import ConfirmationError, repair_write
from app.services.retriever import retriever

MAX_AGENT_ROUNDS = 3

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "search_service_manual",
            "description": "在设备维修手册中检索故障排查、拆卸/安装步骤、维修参数（扭矩、电压、型号、螺钉规格等）。",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "description": "要检索的维修问题或关键词"},
                },
                "required": ["query"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "query_device",
            "description": "按设备编号/名称/型号关键字查询本部门设备信息。",
            "parameters": {
                "type": "object",
                "properties": {
                    "keyword": {"type": "string", "description": "设备关键字，如设备ID、名称或型号"},
                },
                "required": ["keyword"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "query_repair_history",
            "description": "查询某台设备的维修历史记录（仅限本部门设备，跨部门会拒绝）。",
            "parameters": {
                "type": "object",
                "properties": {
                    "device_id": {"type": "string", "description": "设备ID，如 EQ-1001"},
                },
                "required": ["device_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_repair",
            "description": "为本部门某台设备发起报修。生成一次性确认 Token（不落库），需用户调用 /repair/confirm 确认后才真正创建报修单。",
            "parameters": {
                "type": "object",
                "properties": {
                    "device_id": {"type": "string", "description": "设备ID，如 EQ-1001"},
                    "fault_desc": {"type": "string", "description": "故障描述"},
                },
                "required": ["device_id", "fault_desc"],
            },
        },
    },
]

AGENT_SYSTEM = """你是 RepairMind 设备维修智能助手，服务于模具中心。
规则：
1. 先判断需要哪个工具：故障排查/维修步骤 → search_service_manual；
   设备信息 → query_device；维修历史 → query_repair_history；发起报修 → create_repair。
2. 只能依据工具返回的内容回答；工具返回空/无权限/需确认时，如实告知用户，禁止编造。
3. 涉及维修参数时标注来源（文档/页码）。
4. 所有回答使用中文，分步骤、简洁。
5. 最多调用 3 轮工具，之后必须给出最终回答。"""


def execute_tool(name: str, args: dict, user_ctx: dict) -> str:
    """服务端执行工具。user_ctx = {user_id, dept_id, token?}，身份不由 LLM 决定。"""
    dept_id = user_ctx["dept_id"]

    if name == "search_service_manual":
        docs = retriever.search(args.get("query", ""), top_k=3, dept_id=dept_id)
        if not docs:
            return "手册中未检索到相关内容（可能超出知识库范围或部门无权限）。"
        return "\n".join(
            f"[{d['chunk_id']} 第{d.get('page_number')}页] {d['text'][:400]}" for d in docs
        )

    if name == "query_device":
        result = data_source.query_device(args.get("keyword", ""), user_ctx)
        return json.dumps(result, ensure_ascii=False)

    if name == "query_repair_history":
        result = data_source.query_repair_history(args.get("device_id", ""), user_ctx)
        return json.dumps(result, ensure_ascii=False)

    if name == "create_repair":
        try:
            result = repair_write.create_confirmation(
                user_ctx["user_id"], dept_id,
                args.get("device_id", ""), args.get("fault_desc", ""),
            )
            return json.dumps(result, ensure_ascii=False)
        except ConfirmationError as exc:
            return f"报修发起失败：{exc}"

    return f"未知工具: {name}"


def _trace(entry: dict) -> dict:
    """截断工具结果，避免日志与响应过大。"""
    e = dict(entry)
    e["result"] = e.get("result", "")[:200]
    return e


def run_real_agent(question: str, user_ctx: dict, request_id: str, session_id: str = "") -> dict:
    """真实模式：DeepSeek Function Calling 循环。"""
    history = memory.get_history(session_id) if session_id else []
    messages = [{"role": "system", "content": AGENT_SYSTEM}]
    messages += history[-6:]
    messages.append({"role": "user", "content": question})
    trace = []

    for _ in range(MAX_AGENT_ROUNDS):
        resp = llm_client.chat(messages, request_id, tools=TOOLS)
        tool_calls = resp.get("tool_calls")

        if not tool_calls:
            return {
                "answer": resp.get("content") or "",
                "tool_trace": trace,
                "request_id": request_id,
            }

        for tc in tool_calls:
            fn = tc.get("function", {})
            name = fn.get("name", "")
            try:
                args = json.loads(fn.get("arguments") or "{}")
            except json.JSONDecodeError:
                args = {}
            result = execute_tool(name, args, user_ctx)
            trace.append(_trace({"name": name, "args": args, "result": result}))
            messages.append({"role": "assistant", "content": None, "tool_calls": [tc]})
            messages.append({
                "role": "tool",
                "tool_call_id": tc.get("id", ""),
                "content": result,
            })

    return {
        "answer": "已达到最大工具调用轮次，请收窄问题后重试。",
        "tool_trace": trace,
        "request_id": request_id,
    }


def run_stub_agent(question: str, user_ctx: dict, request_id: str, session_id: str = "") -> dict:
    """stub 模式：确定性模拟"模型决策 → 调工具 → 作答"，无需 API Key。"""
    history = memory.get_history(session_id) if session_id else []
    trace = []
    dept_id = user_ctx["dept_id"]

    docs = retriever.search(question, top_k=3, dept_id=dept_id)
    trace.append(_trace({
        "name": "search_service_manual",
        "args": {"query": question[:50]},
        "result": f"检索到 {len(docs)} 条维修知识",
    }))

    # 若问题里出现设备ID，追加一次维修历史查询，演示多工具编排与数据隔离
    m = re.search(r"(EQ-\d{4})", question)
    device_id = m.group(1) if m else None
    if device_id:
        hist = data_source.query_repair_history(device_id, user_ctx)
        trace.append(_trace({
            "name": "query_repair_history",
            "args": {"device_id": device_id},
            "result": json.dumps(hist, ensure_ascii=False),
        }))

    if not docs:
        answer = "[stub-agent] 已调用工具检索，未找到相关内容，判定为知识库外问题，拒绝作答。"
    else:
        lines = "\n".join(
            f"[{d['chunk_id']} 第{d.get('page_number')}页] {d['text'][:80]}" for d in docs[:2]
        )
        answer = (
            f"[stub-agent] 已通过工具调用获取 {len(docs)} 条维修知识"
            f"（真实模式下 LLM 将依据这些上下文作答）。\n引用依据：\n{lines}"
        )

    if history:
        answer = answer.replace(
            "[stub-agent]", f"[stub-agent]（结合前文 {len(history)} 条对话）", 1
        )

    return {"answer": answer, "tool_trace": trace, "request_id": request_id}


def run_agent(
    question: str,
    dept_id: str,
    request_id: str,
    session_id: str = "",
    user_id: str = "demo-user",
) -> dict:
    user_ctx = {"user_id": user_id, "dept_id": dept_id}
    if settings.llm_stub:
        result = run_stub_agent(question, user_ctx, request_id, session_id)
    else:
        result = run_real_agent(question, user_ctx, request_id, session_id)
    if session_id:
        memory.append(session_id, question, result["answer"])
    return result
