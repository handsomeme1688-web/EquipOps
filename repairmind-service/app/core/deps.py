"""共享 FastAPI 依赖：从 Authorization Bearer 解析并校验 JWT。"""
from fastapi import Header, HTTPException

from app.core.auth import AuthError, TokenExpired, verify_jwt
from app.core.config import settings


def get_user_ctx(authorization: str | None = Header(None)) -> dict:
    """返回 JWT payload（user_id / dept_id / exp...），dept 只来自 Token。"""
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="缺少 Authorization: Bearer <token>")
    token = authorization[len("Bearer "):]
    try:
        return verify_jwt(token, settings.jwt_secret, settings.jwt_audience)
    except TokenExpired:
        raise HTTPException(status_code=401, detail="Token 已过期") from None
    except AuthError:
        raise HTTPException(status_code=401, detail="Token 无效") from None
