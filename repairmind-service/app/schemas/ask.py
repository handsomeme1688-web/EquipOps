from pydantic import BaseModel, ConfigDict, Field


class AskRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    # TODO min_length=1 能阻止空字符串，但不能阻止只有空格的字符串,后面改
    question: str = Field(..., min_length=1, max_length=2000, description="用户问题")
    tenant_id: str = Field(..., min_length=1, description="租户ID，数据隔离用")


class AskResponse(BaseModel):
    answer: str
    request_id: str
