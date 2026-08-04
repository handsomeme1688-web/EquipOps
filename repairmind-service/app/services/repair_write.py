"""写操作安全：一次性确认 Token + 幂等（对应手册 Day32）。

流程（防诱导写入 / 防重放 / 防重复落库）：
1. create_confirmation：校验设备属于本部门，生成一次性确认 Token（绑定用户/参数/TTL），**不落库**；
2. confirm_and_execute：校验 Token（存在 / 未使用 / 未过期 / 用户匹配）→ 标记已用 → 真正落库；
3. 幂等键：同一业务请求重试返回同一条记录，不产生第二条（生产由数据库唯一索引兜底）。

Token 用 HMAC 签名，不可伪造；进程内存储，单机演示够用。
"""
from __future__ import annotations

import hashlib
import hmac
import time
import uuid

from app.core.config import settings
from app.services import device_repo


class ConfirmationError(Exception):
    """确认环节的任何失败：不存在 / 重放 / 过期 / 用户不匹配。"""


class RepairWriteService:
    def __init__(self):
        self._tokens: dict[str, dict] = {}      # token -> {user_id, params, exp, used}
        self._idempotent: dict[str, dict] = {}  # idempotency_key -> record
        self._records: list[dict] = []          # 已确认落库的报修

    # ---- 第一步：生成确认 Token（不落库）----

    def create_confirmation(
        self, user_id: str, dept_id: str, device_id: str, fault_desc: str, ttl: int = 300
    ) -> dict:
        device = device_repo.get_device(device_id, dept_id)
        if device.get("denied"):
            raise ConfirmationError(device["error"])
        if device.get("error"):
            raise ConfirmationError(device["error"])

        params = {"device_id": device_id, "fault_desc": fault_desc, "dept_id": dept_id}
        raw = f"{user_id}|{dept_id}|{time.time()}|{uuid.uuid4()}"
        token = hmac.new(
            settings.jwt_secret.encode(), raw.encode(), hashlib.sha256
        ).hexdigest()
        self._tokens[token] = {
            "user_id": user_id, "params": params, "exp": time.time() + ttl, "used": False,
        }
        return {
            "confirm_token": token,
            "device_id": device_id,
            "fault_desc": fault_desc,
            "ttl_seconds": ttl,
            "hint": "调用 /repair/confirm 确认后才会真正落库",
        }

    # ---- 第二步：确认并落库 ----

    def confirm_and_execute(
        self, confirm_token: str, user_id: str, idempotency_key: str | None = None
    ) -> dict:
        # 幂等优先：同一业务请求重试直接返回第一次的结果
        if idempotency_key:
            if idempotency_key in self._idempotent:
                return self._idempotent[idempotency_key]
            if not confirm_token:
                raise ConfirmationError("缺少确认 Token")

        params = self._consume_token(confirm_token, user_id)
        record = self._create_record(params)
        if idempotency_key:
            self._idempotent[idempotency_key] = record
        return record

    def _consume_token(self, token: str, user_id: str) -> dict:
        rec = self._tokens.get(token)
        if rec is None:
            raise ConfirmationError("确认 Token 不存在")
        if rec["used"]:
            raise ConfirmationError("确认 Token 已被使用（重放攻击）")
        if rec["exp"] < time.time():
            raise ConfirmationError("确认 Token 已过期")
        if rec["user_id"] != user_id:
            raise ConfirmationError("确认 Token 与当前用户不匹配")
        rec["used"] = True  # 一次性
        return rec["params"]

    def _create_record(self, params: dict) -> dict:
        record = {
            "repair_id": f"R-{uuid.uuid4().hex[:8]}",
            "device_id": params["device_id"],
            "dept_id": params["dept_id"],
            "fault": params["fault_desc"],
            "status": "已报修",
            "created_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        }
        self._records.append(record)
        return record


# 模块级单例
repair_write = RepairWriteService()
