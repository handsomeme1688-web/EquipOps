"""报修确认（对应手册 Day32 写操作二次确认）。

流程：create_repair 生成一次性确认 Token（不落库）→ 用户确认 → /repair/confirm
校验通过后才真正落库。重放 / 过期 / 跨用户确认一律 409 拒绝。
"""
from fastapi import APIRouter, Depends, HTTPException

from app.core.deps import get_user_ctx
from app.schemas.repair import ConfirmRequest
from app.services.repair_write import ConfirmationError, repair_write

router = APIRouter(tags=["repair"])


@router.post("/repair/confirm")
async def confirm_repair(req: ConfirmRequest, user: dict = Depends(get_user_ctx)) -> dict:
    try:
        record = repair_write.confirm_and_execute(
            req.confirm_token, user["user_id"], req.idempotency_key
        )
    except ConfirmationError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from None
    return {"record": record, "user_id": user["user_id"]}
