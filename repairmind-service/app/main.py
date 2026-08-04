from fastapi import FastAPI, APIRouter,Request
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from app.routers.ask import router as ask_router
from app.routers.agent import router as agent_router
from app.routers.chat import router as chat_router
from app.routers.repair import router as repair_router
from app.core.logging_config import configure_logging

from app.services.llm_client import LLMUnavailableError


configure_logging(level="info",logger_name="app")

app = FastAPI(title="RepairMind", version="0.4.0")

app.include_router(ask_router)
app.include_router(agent_router)
app.include_router(chat_router)
app.include_router(repair_router)


@app.get("/", include_in_schema=False)
def index():
    return FileResponse("app/static/chat.html")


app.mount("/static", StaticFiles(directory="app/static"), name="static")
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
