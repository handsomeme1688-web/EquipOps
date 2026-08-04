import uuid

from fastapi import APIRouter

from app.schemas.ask import AskRequest, AskResponse, Source
from app.services.llm_client import llm_client  # noqa: F401  — 共享单例，兼容既有测试 monkeypatch 目标
from app.services.rag import build_rag_answer

router = APIRouter(tags=["ask"])


@router.post("/ask")
def ask(request: AskRequest) -> AskResponse:
    rid = str(uuid.uuid4())
    result = build_rag_answer(
        request.question,
        rid,
        dept_id=request.dept_id,
        session_id=request.session_id,
    )
    return AskResponse(
        answer=result["answer"],
        request_id=rid,
        sources=[Source(**s) for s in result["sources"]],
        refused=result["refused"],
    )
