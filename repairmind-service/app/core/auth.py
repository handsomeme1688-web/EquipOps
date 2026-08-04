"""HS256 JWT 签发与验证（纯标准库实现，零依赖）。

对应手册 Day31「真实认证」：Python 服务校验 Java 端签发的 JWT，
用户上下文（user_id / dept_id）从 Token 里取，不信任前端传参。
这也是数据隔离的关键一环：dept 只来自签名后的 Token。

手写而非引库的好处：能讲清 header.payload.signature 三段结构、
HMAC 校验、exp 过期判断——面试常考 JWT 原理。
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time


class AuthError(Exception):
    """Token 格式 / 签名 / 载荷错误。"""


class TokenExpired(AuthError):
    """Token 已过期。"""


def _b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def _b64url_decode(s: str) -> bytes:
    pad = "=" * (-len(s) % 4)
    return base64.urlsafe_b64decode(s + pad)


def mint_jwt(
    secret: str,
    user_id: str,
    dept_id: str,
    ttl_seconds: int = 1800,
    audience: str = "repairmind",
    extra: dict | None = None,
) -> str:
    """签发 HS256 JWT。exp = iat + ttl。"""
    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {
        "user_id": user_id,
        "dept_id": dept_id,
        "aud": audience,
        "iat": now,
        "exp": now + ttl_seconds,
    }
    if extra:
        payload.update(extra)

    h = _b64url_encode(json.dumps(header, separators=(",", ":")).encode())
    p = _b64url_encode(json.dumps(payload, separators=(",", ":")).encode())
    signing_input = f"{h}.{p}"
    sig = hmac.new(secret.encode(), signing_input.encode(), hashlib.sha256).digest()
    return signing_input + "." + _b64url_encode(sig)


def verify_jwt(token: str, secret: str, audience: str = "repairmind") -> dict:
    """校验签名 + aud + exp，返回 payload。"""
    try:
        h, p, s = token.split(".")
    except ValueError:
        raise AuthError("Token 格式错误") from None

    expected = hmac.new(
        secret.encode(), f"{h}.{p}".encode(), hashlib.sha256
    ).digest()
    try:
        sig = _b64url_decode(s)
    except Exception:
        raise AuthError("签名格式错误") from None
    if not hmac.compare_digest(sig, expected):
        raise AuthError("签名校验失败")

    try:
        payload = json.loads(_b64url_decode(p))
    except Exception:
        raise AuthError("载荷解析失败") from None
    if payload.get("aud") != audience:
        raise AuthError("aud 不匹配")
    if int(payload.get("exp", 0)) < time.time():
        raise TokenExpired("Token 已过期")
    return payload
