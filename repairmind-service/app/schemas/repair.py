from pydantic import BaseModel, ConfigDict, Field


class ConfirmRequest(BaseModel):
    """报修确认请求（Day32 写操作二次确认）。"""

    model_config = ConfigDict(extra="forbid")

    confirm_token: str = Field(..., min_length=1, description="create_repair 生成的一次性确认 Token")
    idempotency_key: str | None = Field(
        default=None, max_length=128, description="幂等键：同一业务请求重试只落库一次"
    )
