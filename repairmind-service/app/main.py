from fastapi import FastAPI, APIRouter
from app.routers.ask import router as ask_router


app = FastAPI(title="RepairMind", version="0.1.0")

app.include_router(ask_router)
@app.get("/health")
def health():
    return {"status": "ok"}


