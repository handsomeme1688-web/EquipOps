import uuid

from fastapi import APIRouter

from app.schemas.agent import AgentRequest, AgentResponse, ToolCall
from app.services.agent import run_agent

router = APIRouter(tags=["agent"])


@router.post("/agent/chat")
def agent_chat(request: AgentRequest) -> AgentResponse:
    rid = str(uuid.uuid4())
    result = run_agent(
        request.question, request.dept_id, rid,
        session_id=request.session_id, user_id=request.user_id,
    )
    return AgentResponse(
        answer=result["answer"],
        request_id=rid,
        tool_trace=[ToolCall(**t) for t in result["tool_trace"]],
    )
