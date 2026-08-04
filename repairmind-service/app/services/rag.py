"""RAG 编排：检索 → 注入上下文 → LLM 作答 → 引用校验。

对应手册 P2 · Day30【核心】：
- 只依据检索上下文回答，无依据就拒答（不编造）；
- 维修参数（扭矩/电压/型号等）必须带来源引用；
- 防模型伪造引用：校验回答中出现的 chunk_id 必须真实存在于本次检索结果里。

stub 模式下跳过真实 LLM，返回固定前缀的模拟回答，便于测试与无 Key 演示。
"""
from __future__ import annotations

import logging
import re

from app.core.config import settings
from app.services.llm_client import llm_client
from app.services.memory import memory
from app.services.retriever import retriever

logger = logging.getLogger("app.rag")

SYSTEM_PROMPT = """你是 RepairMind 维修知识助手，服务于某大型电子制造企业的模具中心。
严格遵守以下规则：
1. 只能依据下方【检索上下文】回答，禁止使用上下文之外的任何知识。
2. 如果问题与维修知识无关，或上下文中找不到依据，必须直接回复：「抱歉，该问题不在当前维修知识库范围内，我无法回答。」严禁编造。
3. 涉及扭矩、电压、型号、螺钉规格、拆卸顺序等关键信息时，必须在句末标注引用，格式：[文档:{chunk_id} 第{page_number}页]。
4. 严禁编造任何参数、步骤、型号或页码引用。
5. 使用中文，分步骤、简洁回答。"""

CITE_RE = re.compile(r"\[文档:([A-Za-z0-9_-]+)")

REFUSAL_ANSWER = "抱歉，该问题不在当前维修知识库范围内，我无法回答。"


def build_context(sources: list[dict]) -> str:
    parts = []
    for i, s in enumerate(sources, start=1):
        parts.append(
            f"[上下文{i}] (文档:{s['chunk_id']} 第{s.get('page_number')}页)\n{s['text']}"
        )
    return "\n\n".join(parts)


def build_history(history: list[dict], max_items: int = 4) -> str:
    """把最近几轮对话拼进 prompt，让模型理解"上一句说了什么"。"""
    if not history:
        return ""
    lines = []
    for m in history[-max_items:]:
        who = "用户" if m["role"] == "user" else "助手"
        lines.append(f"{who}: {m['content']}")
    return "【对话历史】\n" + "\n".join(lines)


def build_prompt(question: str, sources: list[dict], history: list[dict] | None = None) -> str:
    ctx = build_context(sources)
    htxt = build_history(history or [])
    sections = [SYSTEM_PROMPT]
    if htxt:
        sections.append(htxt)
    sections.append(f"【检索上下文】\n{ctx}")
    sections.append(f"【问题】\n{question}")
    sections.append("请结合对话历史理解提问，但答案只能依据检索上下文回答。")
    return "\n\n".join(sections)


def cited_chunk_ids(answer: str) -> set[str]:
    """从回答中提取模型引用的 chunk_id。"""
    return set(CITE_RE.findall(answer or ""))


def has_fabricated_citation(answer: str, valid_ids: list[str]) -> bool:
    """回答中出现的引用必须都在本次检索结果里，否则视为伪造引用。"""
    valid = set(valid_ids)
    cited = cited_chunk_ids(answer)
    if not cited:
        return False
    return not cited.issubset(valid)


def build_rag_answer(
    question: str,
    request_id: str,
    dept_id: str | None = None,
    top_k: int = 5,
    session_id: str = "",
) -> dict:
    """RAG 主流程，返回 {answer, sources, refused, request_id}。session_id 开启多轮记忆。"""
    sources = retriever.search(question, top_k=top_k, dept_id=dept_id)
    history = memory.get_history(session_id) if session_id else []

    # 知识库外问题：无依据，拒答（stub 模式下仍走 LLM 占位，方便测试与演示）
    if not sources and not settings.llm_stub:
        logger.info("rag_refusal", extra={
            "event": "rag_refusal", "request_id": request_id, "question": question[:100],
        })
        return {"answer": REFUSAL_ANSWER, "sources": [], "refused": True, "request_id": request_id}

    prompt = build_prompt(question, sources, history=history)
    # SYSTEM_PROMPT 已拼接在 prompt 顶部；不额外传 system，保持 call(question, request_id) 两参签名
    answer = llm_client.call(prompt, request_id)

    if settings.llm_stub:
        # stub 模式：展示检索命中 + 记忆状态，不把整段 prompt 回显出来
        mem = f"（已有 {len(history)} 条历史）" if history else ""
        if sources:
            s0 = sources[0]
            answer = (
                f"[stub]{mem} 已检索到 {len(sources)} 条维修知识。真实模式下 LLM 将依据以下内容作答：\n"
                f"〔{s0['chunk_id']} 第{s0.get('page_number')}页〕{s0['text'][:120]}…"
            )
        else:
            answer = f"[stub]{mem} 未检索到相关内容。"

    refused = False
    if not settings.llm_stub and has_fabricated_citation(answer, [s["chunk_id"] for s in sources]):
        # 引用伪造拦截：宁可拒答，不给编造的维修参数
        answer = "抱歉，检索结果未能支撑可靠回答（检测到无法核实的引用），已拒绝输出。"
        refused = True

    if session_id:
        memory.append(session_id, question, answer)

    return {
        "answer": answer,
        "sources": sources,
        "refused": refused,
        "request_id": request_id,
    }
