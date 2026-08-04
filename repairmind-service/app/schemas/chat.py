from pydantic import BaseModel, ConfigDict, Field

from app.schemas.agent import ToolCall
from app.schemas.ask import Source
from app.services.retriever import DEFAULT_DEPT


class ChatRequest(BaseModel):
    """/ai/chat 请求体。dept 不在此取——由 JWT 用户上下文决定（Day31）。"""

    model_config = ConfigDict(extra="forbid")

    question: str = Field(..., min_length=1, max_length=2000, description="用户问题")
    mode: str = Field(default="rag", pattern="^(rag|agent)$", description="rag 或 agent")
    stream: bool = Field(default=False, description="true 时返回 SSE 流式（仅 rag 模式）")
    session_id: str = Field(default="", max_length=64, description="多轮记忆会话ID")


class LoginRequest(BaseModel):
    """/dev/login：仅演示用，签发一个带 dept 上下文的测试 JWT。"""

    model_config = ConfigDict(extra="forbid")

    user_id: str = Field(default="demo-user", min_length=1, max_length=64)
    dept_id: str = Field(default=DEFAULT_DEPT, min_length=1, description="放入 JWT 的部门ID")
    ttl: int = Field(default=1800, ge=60, le=7200, description="Token 有效期秒数")


class ChatResponse(BaseModel):
    answer: str
    request_id: str
    sources: list[Source] = Field(default_factory=list)
    tool_trace: list[ToolCall] = Field(default_factory=list)
    refused: bool = False
