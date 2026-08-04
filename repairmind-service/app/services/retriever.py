"""混合检索：向量（BGE-M3 → Chroma）+ BM25（jieba）+ RRF 融合。

对应手册 P2 · Day29【核心】。核心思路：
- 向量通道：语义相似，适合"问题表述和文档用词不同"的情况；
- BM25 通道：jieba 分词后做词频匹配，适合型号 / 错误码这类精确词；
- RRF 融合：对两个通道的排名求倒序加权，弥补单一通道的盲区；
- dept_id 过滤：检索前强制按部门过滤，多租户数据隔离的第一道闸。

模型 / 索引都是模块级单例，避免每个请求重复加载。
"""
from __future__ import annotations

import json
import os
from collections import defaultdict
from pathlib import Path

import jieba
import torch
from chromadb import PersistentClient
from rank_bm25 import BM25Okapi
from sentence_transformers import SentenceTransformer

from app.services.embedding_cache import embedding_cache
from app.services.reranker import rerank

PROJECT_ROOT = Path(__file__).resolve().parents[2]

DATA_DIR = PROJECT_ROOT / "data"
PROCESSED_JSON = DATA_DIR / "processed" / "dell-inspiron-16-chunks.json"
CHROMA_DIR = DATA_DIR / "chroma"
COLLECTION_NAME = "dell_inspiron_16"
MODEL_NAME = "BAAI/bge-m3"

DEFAULT_DEPT = "dept-mold-01"  # 模具中心。手册 Day33：向量数据携带 tenant/dept 元数据

# ---- 模块级单例缓存 ----
_EMBEDDING_MODEL = None
_COLLECTION = None
_BM25_INDEX = None
_BM25_IDS: list[str] = []
_CHUNKS_BY_ID: dict[str, dict] | None = None


def _device() -> str:
    """嵌入设备：默认 CPU。这台机器是 RTX 3060 6GB，显存常被占用，
    放不下 BGE-M3 会 OOM。可用环境变量 RM_EMBED_DEVICE=cuda 切回 GPU。"""
    dev = os.environ.get("RM_EMBED_DEVICE", "cpu")
    if dev == "cuda" and not torch.cuda.is_available():
        return "cpu"
    return dev


def _load_embedding_model() -> SentenceTransformer:
    global _EMBEDDING_MODEL
    if _EMBEDDING_MODEL is None:
        _EMBEDDING_MODEL = SentenceTransformer(
            MODEL_NAME, device=_device(), local_files_only=True
        )
    return _EMBEDDING_MODEL


def _load_chunks() -> list[dict]:
    """从分块 JSON 读取语料，chunk 为权威来源（含 dept_id 字段）。"""
    global _CHUNKS_BY_ID
    if _CHUNKS_BY_ID is None:
        with PROCESSED_JSON.open("r", encoding="utf-8") as f:
            chunks = json.load(f)
        _CHUNKS_BY_ID = {c["chunk_id"]: c for c in chunks}
    return list(_CHUNKS_BY_ID.values())


def _chunk_by_id() -> dict[str, dict]:
    _load_chunks()
    return _CHUNKS_BY_ID  # type: ignore[return-value]


def _load_collection():
    global _COLLECTION
    if _COLLECTION is None:
        client = PersistentClient(path=str(CHROMA_DIR))
        _COLLECTION = client.get_collection(COLLECTION_NAME)
    return _COLLECTION


def _load_bm25() -> BM25Okapi:
    """BM25 索引与 chunk_id 保持对齐，方便按 dept 过滤后取前 K。"""
    global _BM25_INDEX, _BM25_IDS
    if _BM25_INDEX is None:
        chunks = _load_chunks()
        _BM25_IDS = [c["chunk_id"] for c in chunks]
        tokenized = [list(jieba.cut(c["text"])) for c in chunks]
        _BM25_INDEX = BM25Okapi(tokenized)
    return _BM25_INDEX


def _invalid_query(query: str) -> bool:
    """空查询 / 纯空白 / 纯符号查询直接返回空，检索函数不能崩。"""
    q = (query or "").strip()
    if not q:
        return True
    if not any(ch.isalnum() for ch in q):
        return True
    return False


# 领域同义词扩展（第一版）：把用户口语词映射到手册术语，缓解术语不匹配。
# 这是生产 RAG 缓解"检索不到"的常规手段之一（比单靠 embedding 更可控）。
SYNONYM_MAP = {
    "后盖": "基座护盖",
    "螺丝": "螺钉",
    "拆机": "拆装计算机内部组件",
    "开机启动不了": "无法开机",
}


def _expand_query(query: str) -> str:
    """查询改写：口语词 → 手册术语。不做删除，只做映射替换。"""
    expanded = query
    for lay, term in SYNONYM_MAP.items():
        expanded = expanded.replace(lay, term)
    return expanded


def _dept_ok(chunk: dict, dept_id: str | None) -> bool:
    """按 dept 过滤单个 chunk；未指定 dept 时不过滤。"""
    if not dept_id:
        return True
    return chunk.get("dept_id", DEFAULT_DEPT) == dept_id


class Retriever:
    """业务层检索门面。mode: hybrid | vector | bm25（评测对比用）。"""

    def search(
        self,
        query: str,
        top_k: int = 5,
        dept_id: str | None = None,
        mode: str = "hybrid",
    ) -> list[dict]:
        if _invalid_query(query) or top_k <= 0:
            return []
        query = _expand_query(query)  # 同义词扩展：缓解"后盖→基座护盖"这类术语不匹配
        if mode == "vector":
            return self._vector_search(query, top_k, dept_id)
        if mode == "bm25":
            return self._bm25_search(query, top_k, dept_id)
        if mode == "hybrid_rerank":
            candidates = self._hybrid_search(query, max(top_k * 4, 20), dept_id)
            return rerank(query, candidates, top_k)
        return self._hybrid_search(query, top_k, dept_id)

    # ---- 内部通道 ----

    def _vector_search(self, query: str, top_k: int, dept_id: str | None) -> list[dict]:
        model = _load_embedding_model()
        col = _load_collection()
        where = {"dept_id": dept_id} if dept_id else None
        qv = embedding_cache.get(query)  # 复用同句查询向量（Day34）
        if qv is None:
            qv = model.encode(query, normalize_embeddings=True).tolist()
            embedding_cache.set(query, qv)
        res = col.query(
            query_embeddings=[qv],
            n_results=max(top_k * 4, 20),
            where=where,
            include=["distances"],
        )
        ids = res.get("ids") or [[]]
        dists = res.get("distances") or [[]]
        if not ids[0]:
            return []
        sims = [1.0 - d for d in dists[0]]
        ranked_ids = [cid for _, cid in sorted(zip(sims, ids[0]), reverse=True)]
        return self._chunk_rows(ranked_ids[:top_k])

    def _bm25_search(self, query: str, top_k: int, dept_id: str | None) -> list[dict]:
        bm25 = _load_bm25()
        tokens = list(jieba.cut(query))
        scores = bm25.get_scores(tokens)
        by_id = _chunk_by_id()
        scored = []
        for cid, score in zip(_BM25_IDS, scores):
            chunk = by_id[cid]
            if _dept_ok(chunk, dept_id):
                scored.append((score, cid))
        scored.sort(reverse=True)
        return self._chunk_rows([cid for _, cid in scored[:top_k]])

    def _hybrid_search(self, query: str, top_k: int, dept_id: str | None) -> list[dict]:
        # 两个通道各自取大一点的候选集，再做 RRF 融合
        vector_cands = self._vector_search(query, top_k=max(top_k * 3, 15), dept_id=dept_id)
        bm25_cands = self._bm25_search(query, top_k=max(top_k * 3, 15), dept_id=dept_id)
        if not vector_cands and not bm25_cands:
            return []

        rrf = defaultdict(float)
        for rank, row in enumerate(vector_cands, start=1):
            rrf[row["chunk_id"]] += 1.0 / (60 + rank)
        for rank, row in enumerate(bm25_cands, start=1):
            rrf[row["chunk_id"]] += 1.0 / (60 + rank)

        fused = sorted(rrf.items(), key=lambda kv: kv[1], reverse=True)
        by_id = _chunk_by_id()
        rows = []
        for cid, score in fused[:top_k]:
            chunk = by_id[cid]
            rows.append({
                "chunk_id": cid,
                "text": chunk["text"],
                "page_number": chunk.get("page_number"),
                "source": chunk.get("source") or "Dell Inspiron 16 Service Manual",
                "score": round(score, 6),
            })
        return rows

    def _chunk_rows(self, chunk_ids: list[str]) -> list[dict]:
        by_id = _chunk_by_id()
        rows = []
        for cid in chunk_ids:
            chunk = by_id.get(cid)
            if not chunk:
                continue
            rows.append({
                "chunk_id": cid,
                "text": chunk["text"],
                "page_number": chunk.get("page_number"),
                "source": chunk.get("source") or "Dell Inspiron 16 Service Manual",
            })
        return rows


# 模块级单例，供 FastAPI 路由复用
retriever = Retriever()
