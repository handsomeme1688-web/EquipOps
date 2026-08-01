import uuid

from fastapi import APIRouter

from app.schemas.ask import AskRequest, AskResponse
from app.services.llm_client import LLMClient

router = APIRouter(tags=["ask"])
llm_client = LLMClient()
@router.post("/ask")
def ask(request: AskRequest) -> AskResponse:
    rid = str(uuid.uuid4())
    return AskResponse(
        answer=llm_client.call(request.question,rid),
        request_id=rid
    )