"""BGE-Reranker 重排（对应手册 Day29）。

流程：hybrid 先取 top-20 候选 → reranker 逐对打分 → 取 top-5。
模型懒加载；未下载（或加载失败/无网络）时优雅回退到 hybrid 原序，
不影响检索。下载脚本见 scripts/download_reranker.py。
"""
from __future__ import annotations

import logging

from app.core.config import settings

logger = logging.getLogger("app.reranker")

_RERANKER = None
_DISABLED = False


def _load():
    global _RERANKER, _DISABLED
    if _RERANKER is not None or _DISABLED:
        return _RERANKER
    try:
        from sentence_transformers import CrossEncoder
        _RERANKER = CrossEncoder(settings.rerank_model, device=settings.rerank_device)
        logger.info("reranker_loaded", extra={"event": "reranker_loaded", "model": settings.rerank_model})
    except Exception as exc:  # 未下载 / 无网络 / 缺依赖 → 优雅回退
        _DISABLED = True
        logger.warning("reranker 加载失败，回退 hybrid 排序：%s", exc)
    return _RERANKER


def reranker_available() -> bool:
    return _load() is not None


def rerank(query: str, candidates: list[dict], top_k: int = 5) -> list[dict]:
    """对候选打分重排，返回前 top_k。模型不可用则原序截断。"""
    model = _load()
    if model is None or not candidates:
        return candidates[:top_k]
    pairs = [(query, c["text"]) for c in candidates]
    scores = model.predict(pairs)
    ranked = [c for _, c in sorted(zip(scores, candidates), key=lambda x: -float(x[0]))]
    return ranked[:top_k]
