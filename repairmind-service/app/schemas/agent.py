from pydantic import BaseModel, ConfigDict, Field

from app.services.retriever import DEFAULT_DEPT


class AgentRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    question: str = Field(..., min_length=1, max_length=2000, description="用户问题")
    tenant_id: str = Field(..., min_length=1, description="租户ID")
    # 生产环境由 Java 端验 JWT 后签发用户上下文，此处为独立演示的简化
    dept_id: str = Field(default=DEFAULT_DEPT, description="部门ID，工具调用数据隔离用")
    # 会话记忆：同一 session_id 的多轮请求共享上下文；留空则单轮无记忆
    session_id: str = Field(default="", max_length=64, description="会话ID，多轮记忆用")
    # 独立演示简化：身份可经 /ai/chat 的 JWT 传入；此处默认 demo 用户
    user_id: str = Field(default="demo-user", max_length=64, description="用户ID")


class ToolCall(BaseModel):
    """一次工具调用记录（审计痕迹）。"""

    name: str
    args: dict = Field(default_factory=dict)
    result: str = ""


class AgentResponse(BaseModel):
    answer: str
    request_id: str
    tool_trace: list[ToolCall] = Field(default_factory=list)
