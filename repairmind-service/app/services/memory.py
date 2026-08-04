"""会话记忆：短期、进程内存版（对应手册 Day33「多轮上下文维护」）。

设计：
- 以 session_id 为键保存最近 N 轮对话（user + assistant 成对）；
- 超出上限直接裁剪最旧的轮次（简单裁剪策略）；
- 超过容量上限时清理超时未活跃的会话；
- 进程内内存、重启即失——单机演示足够；企业方案是 Redis + TTL + 摘要压缩。

为什么需要记忆：Agent 多轮对话如果每轮都"失忆"，用户必须把上下文
重复一遍。保留最近几轮 + 裁剪，是"够用且可控"的第一档实现。
"""
from __future__ import annotations

import time
from collections import defaultdict, deque

MAX_TURNS = 6  # 每个会话最多保留 6 轮（12 条消息）
MAX_SESSIONS = 1000
SESSION_TTL = 3600  # 秒


class ConversationMemory:
    def __init__(self):
        self._sessions: dict[str, deque] = defaultdict(deque)
        self._updated: dict[str, float] = {}

    def get_history(self, session_id: str, max_turns: int | None = None) -> list[dict]:
        """返回最近 max_turns 轮的历史消息（[{role, content}, ...]）。"""
        if not session_id:
            return []
        q = self._sessions.get(session_id)
        if not q:
            return []
        n = max_turns or MAX_TURNS
        items = list(q)[-2 * n:]  # 每轮 2 条：user + assistant
        return [{"role": m["role"], "content": m["content"]} for m in items]

    def append(self, session_id: str, user: str, assistant: str) -> None:
        if not session_id:
            return
        q = self._sessions[session_id]
        q.append({"role": "user", "content": user})
        q.append({"role": "assistant", "content": assistant})
        while len(q) > 2 * MAX_TURNS:  # 裁剪最旧轮次
            q.popleft()
        self._updated[session_id] = time.time()
        if len(self._sessions) > MAX_SESSIONS:
            self._evict_stale()

    def clear(self, session_id: str) -> None:
        self._sessions.pop(session_id, None)
        self._updated.pop(session_id, None)

    def count(self, session_id: str) -> int:
        return len(self._sessions.get(session_id, ()))

    def _evict_stale(self) -> None:
        now = time.time()
        stale = [sid for sid, t in self._updated.items() if now - t > SESSION_TTL]
        for sid in stale:
            self.clear(sid)


# 模块级单例，routers / rag / agent 复用
memory = ConversationMemory()
