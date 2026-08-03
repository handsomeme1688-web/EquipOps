from fastapi import FastAPI, APIRouter,Request
from app.routers.ask import router as ask_router
from app.core.logging_config import configure_logging
from fastapi.responses import JSONResponse

from app.services.llm_client import LLMUnavailableError


configure_logging(level="info",logger_name="app")

app = FastAPI(title="RepairMind", version="0.1.0")

app.include_router(ask_router)
@app.get("/health")
def health():
    return {"status": "ok"}

@app.exception_handler(LLMUnavailableError)
async def handle_llm_unavailable(request: Request, exc: LLMUnavailableError):
    return JSONResponse(
        status_code=503,
        content={
            "code":10001,
            "msg":"维修助手暂时不可用，请稍后再试",
            "request_id":exc.request_id
        }
    )
