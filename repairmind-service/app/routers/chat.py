"""/ai/chat：JWT 认证 + RAG/Agent + 流式（对应手册 Day31）。

认证要点：
- 必须带 Authorization: Bearer <JWT>，无/坏/过期一律 401；
- dept 从 JWT 用户上下文取，不信任请求体——这正是数据隔离的前提。
"""
from __future__ import annotations

import asyncio
import uuid

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from app.core.config import settings
from app.core.deps import get_user_ctx
from app.schemas.chat import ChatRequest, ChatResponse, LoginRequest
from app.services import rag
from app.services.agent import run_agent
from app.services.memory import memory
from app.services.retriever import retriever
from app.services.stream import sse, stream_llm, stream_text
from app.core.auth import mint_jwt

router = APIRouter(tags=["chat"])


@router.post("/dev/login", include_in_schema=False)
def dev_login(req: LoginRequest) -> dict:
    """仅本地演示用：签发一个测试 JWT。生产环境由 Java 认证服务签发。"""
    token = mint_jwt(settings.jwt_secret, req.user_id, req.dept_id, ttl_seconds=req.ttl)
    return {"token": token, "expires_in": req.ttl, "user_id": req.user_id, "dept_id": req.dept_id}


def _rag_answer_text(
    question: str, dept_id: str, session_id: str, history: list[dict]
) -> tuple[str, list[dict]]:
    """流式前的同步准备：检索 + 带历史拼接 prompt。"""
    sources = retriever.search(question, top_k=5, dept_id=dept_id)
    return rag.build_prompt(question, sources, history=history), sources


@router.post("/ai/chat", response_model=None)
async def ai_chat(req: ChatRequest, user: dict = Depends(get_user_ctx)) -> dict | StreamingResponse:
    dept_id = user["dept_id"]  # dept 来自 JWT，不信任前端
    rid = str(uuid.uuid4())

    # ---- 非流式 ----
    if not req.stream:
        if req.mode == "agent":
            result = run_agent(
                req.question, dept_id, rid,
                session_id=req.session_id, user_id=user["user_id"],
            )
            return ChatResponse(answer=result["answer"], request_id=rid,
                                tool_trace=result["tool_trace"]).model_dump()
        result = rag.build_rag_answer(req.question, rid, dept_id=dept_id, session_id=req.session_id)
        return ChatResponse(answer=result["answer"], request_id=rid,
                            sources=result["sources"], refused=result["refused"]).model_dump()

    # ---- 流式（仅 rag 模式）----
    history = memory.get_history(req.session_id) if req.session_id else []
    prompt, sources = await asyncio.to_thread(_rag_answer_text, req.question, dept_id, req.session_id, history)

    async def generate():
        yield sse("meta", {"request_id": rid, "dept_id": dept_id, "mode": "rag"})

        if settings.llm_stub:
            if sources:
                s0 = sources[0]
                text = (
                    f"[stub]（流式）已检索到 {len(sources)} 条维修知识。"
                    f"真实模式下 LLM 将逐字流式返回依据上下文生成的回答。\n"
                    f"〔{s0['chunk_id']} 第{s0.get('page_number')}页〕{s0['text'][:120]}…"
                )
            else:
                text = "[stub]（流式）未检索到相关内容。"
            async for chunk in stream_text(text):
                yield sse("delta", {"text": chunk})
        else:
            messages = [{"role": "user", "content": prompt}]
            async for ev in stream_llm(messages, rid):
                if ev["type"] == "delta":
                    yield sse("delta", {"text": ev["data"]})
                else:
                    yield sse("error", ev["data"])
                    return

        if req.session_id:
            memory.append(req.session_id, req.question, prompt[:100] + "…")
        yield sse("done", {"request_id": rid})

    return StreamingResponse(generate(), media_type="text/event-stream")
