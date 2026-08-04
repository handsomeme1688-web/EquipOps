"""Query embedding 缓存（对应手册 Day34）。

同一句 query 反复出现时直接复用向量，避免重复 embed。
默认进程内 dict + TTL（LRU 化淘汰）；生产方案是 Redis（键 = query hash，TTL 由 Redis 管）。
只缓存查询向量（CPU 密集的是 embedding 计算），不动向量库里的文档向量。
"""
from __future__ import annotations

import hashlib
import threading
import time


class EmbeddingCache:
    def __init__(self, ttl: int = 600, max_entries: int = 2000):
        self._ttl = ttl
        self._max = max_entries
        self._data: dict[str, tuple] = {}  # key -> (vector, expire_at)
        self._lock = threading.Lock()
        self.hits = 0
        self.misses = 0

    def _key(self, query: str) -> str:
        return hashlib.sha256(query.encode("utf-8")).hexdigest()

    def get(self, query: str):
        key = self._key(query)
        with self._lock:
            item = self._data.get(key)
            if item and item[1] > time.time():
                self.hits += 1
                return item[0]
        self.misses += 1
        return None

    def set(self, query: str, vector) -> None:
        key = self._key(query)
        with self._lock:
            if len(self._data) >= self._max:
                self._evict_locked()
            self._data[key] = (vector, time.time() + self._ttl)

    def _evict_locked(self) -> None:
        now = time.time()
        expired = [k for k, v in self._data.items() if v[1] <= now]
        for k in expired:
            del self._data[k]
        if len(self._data) >= self._max:  # 还满，淘汰最旧一半
            oldest = sorted(self._data, key=lambda k: self._data[k][1])[: self._max // 2]
            for k in oldest:
                del self._data[k]

    def clear(self) -> None:
        with self._lock:
            self._data.clear()
            self.hits = self.misses = 0

    def stats(self) -> dict:
        return {"size": len(self._data), "max": self._max, "ttl": self._ttl,
                "hits": self.hits, "misses": self.misses}


embedding_cache = EmbeddingCache()
