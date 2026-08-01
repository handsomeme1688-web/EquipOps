import uuid
from fastapi import FastAPI
from app.schemas.ask import AskRequest, AskResponse

app = FastAPI(title="RepairMind", version="0.1.0")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/ask")
def ask(req: AskRequest) -> AskResponse:
    rid = str(uuid.uuid4())
    return AskResponse(
        answer=f"[echo] {req.question}",
        request_id=rid,
    )
