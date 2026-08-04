"""EquipOps Java 后端 HTTP 客户端（对应手册 Day32）。

工具层数据源的"生产实现"：调 Java 接口，并携带 JWT 用户上下文做二次鉴权，
Java 端再次做权限与数据隔离（绝不只信固定 X-API-Key）。

默认 RM_ENABLE_JAVA=0 时工具回退本地样例数据（device_repo）；
置 1 并配置 RM_JAVA_BASE_URL 后，query_device / query_repair_history / create_repair
走 Java 接口。transport 参数供测试注入 httpx.MockTransport。
"""
from __future__ import annotations

from urllib.parse import quote

import httpx

from app.core.config import settings


class JavaClient:
    def __init__(self, transport: httpx.AsyncBaseTransport | None = None):
        self.base_url = settings.java_base_url.rstrip("/")
        self.transport = transport

    def _headers(self, user_ctx: dict) -> dict:
        token = user_ctx.get("token", "")
        return {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "X-User-Id": user_ctx.get("user_id", ""),
        }

    def query_device(self, keyword: str, user_ctx: dict) -> dict:
        return self._get(f"/api/devices?keyword={quote(keyword or '')}", user_ctx)

    def query_repair_history(self, device_id: str, user_ctx: dict) -> dict:
        return self._get(f"/api/devices/{quote(device_id)}/repairs", user_ctx)

    def create_repair(self, params: dict, user_ctx: dict) -> dict:
        return self._post("/api/repairs", params, user_ctx)

    def _get(self, path: str, user_ctx: dict) -> dict:
        with httpx.Client(transport=self.transport, timeout=10.0) as client:
            resp = client.get(self.base_url + path, headers=self._headers(user_ctx))
        resp.raise_for_status()
        return resp.json()

    def _post(self, path: str, payload: dict, user_ctx: dict) -> dict:
        with httpx.Client(transport=self.transport, timeout=10.0) as client:
            resp = client.post(self.base_url + path, json=payload, headers=self._headers(user_ctx))
        resp.raise_for_status()
        return resp.json()


java_client = JavaClient()
