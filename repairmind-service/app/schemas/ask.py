from pydantic import BaseModel, ConfigDict, Field

from app.services.retriever import DEFAULT_DEPT


class AskRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    # TODO min_length=1 能阻止空字符串，但不能阻止只有空格的字符串,后面改
    question: str = Field(..., min_length=1, max_length=2000, description="用户问题")
    tenant_id: str = Field(..., min_length=1, description="租户ID，数据隔离用")
    # 生产环境由 Java 端验 JWT 后签发用户上下文(deptId 来自那里)，此处为独立演示的简化
    dept_id: str = Field(default=DEFAULT_DEPT, description="部门ID，数据隔离用")
    # 会话记忆：同一 session_id 的多轮请求共享上下文；留空则单轮无记忆
    session_id: str = Field(default="", max_length=64, description="会话ID，多轮记忆用")


class Source(BaseModel):
    """RAG 返回的引用来源（对应手册 Day30：回答带 chunk/页码引用）。"""

    chunk_id: str
    page_number: int | None = None
    source: str = ""


class AskResponse(BaseModel):
    answer: str
    request_id: str
    sources: list[Source] = Field(default_factory=list)
    refused: bool = Field(default=False, description="知识库外问题被拒答时置为 true")
