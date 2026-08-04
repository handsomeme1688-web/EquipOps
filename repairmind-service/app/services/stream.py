"""流式响应（对应手册 Day31）。

- stub 模式：本地把一段文本切成小片逐个吐出，模拟流式，无需 API Key；
- 真实模式：httpx.AsyncClient + stream=True 逐行解析 DeepSeek 的 SSE；
- 事件格式统一为 SSE：meta / delta / error / done。
"""
from __future__ import annotations

import json

import httpx

from app.core.config import settings


def sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


async def stream_text(text: str, chunk_size: int = 8) -> None:
    """把一段文本切成小片逐个 yield（stub 演示用）。"""
    for i in range(0, len(text), chunk_size):
        yield text[i:i + chunk_size]


async def stream_llm(messages: list[dict], request_id: str):
    """真实流式：逐行解析 DeepSeek SSE，yield {"type":..., "data":...}。"""
    if not settings.llm_api_key:
        yield {"type": "error", "data": {"code": 10002, "msg": "LLM API Key 未配置"}}
        return
    url = settings.llm_base_url.rstrip("/") + "/chat/completions"
    headers = {
        "Authorization": f"Bearer {settings.llm_api_key}",
        "Content-Type": "application/json",
    }
    body = {
        "model": settings.llm_model,
        "messages": messages,
        "stream": True,
    }
    timeout = httpx.Timeout(
        timeout=settings.llm_read_timeout,
        connect=settings.llm_connect_timeout,
        read=settings.llm_read_timeout,
    )
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            async with client.stream("POST", url, headers=headers, json=body) as resp:
                if 500 <= resp.status_code < 600:
                    yield {"type": "error", "data": {"code": 10001, "msg": "服务暂时不可用"}}
                    return
                resp.raise_for_status()
                async for line in resp.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    payload = line[len("data:"):].strip()
                    if payload == "[DONE]":
                        break
                    try:
                        delta = json.loads(payload)["choices"][0]["delta"].get("content") or ""
                    except (KeyError, json.JSONDecodeError):
                        continue
                    if delta:
                        yield {"type": "delta", "data": delta}
    except httpx.TimeoutException:
        yield {"type": "error", "data": {"code": 10001, "msg": "服务暂时不可用"}}
