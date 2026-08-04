"""工具层数据源门面：本地样例 or EquipOps Java 接口（对应手册 Day32）。

user_ctx 形如 {user_id, dept_id, token?}，由上层（JWT 或请求体）提供。
- RM_ENABLE_JAVA=0（默认）：走本地样例 device_repo，按 dept 隔离；
- RM_ENABLE_JAVA=1：走 java_client 调 Java 接口，携带用户上下文做二次鉴权。
"""
from __future__ import annotations

from app.core.config import settings
from app.services import device_repo
from app.services.java_client import java_client


def query_device(keyword: str, user_ctx: dict) -> dict:
    if settings.enable_java:
        return java_client.query_device(keyword, user_ctx)
    return device_repo.query_device(keyword, user_ctx["dept_id"])


def query_repair_history(device_id: str, user_ctx: dict) -> dict:
    if settings.enable_java:
        return java_client.query_repair_history(device_id, user_ctx)
    return device_repo.query_repair_history(device_id, user_ctx["dept_id"])
