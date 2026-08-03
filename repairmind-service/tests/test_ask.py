from logging import LogRecord

from fastapi.testclient import TestClient
from app.main import app
import app.routers.ask as ask_router
from app.services.llm_client import LLMUnavailableError
client = TestClient(app)


def test_ask_stub_response():
    resp = client.post("/ask", json={"question": "水泵不转了怎么办？", "tenant_id": "dept-2"})
    assert resp.status_code == 200
    data = resp.json()
    assert data["answer"].startswith("[stub]")
    assert data["request_id"] != ""


def test_ask_missing_question_returns_422():
    resp = client.post("/ask", json={"tenant_id": "dept-2"})
    assert resp.status_code == 422


def test_ask_empty_question_returns_422():
    resp = client.post("/ask", json={"question": "", "tenant_id": "dept-2"})
    assert resp.status_code == 422


def test_ask_too_long_question_returns_422():
    resp = client.post("/ask", json={"question": "x" * 2001, "tenant_id": "dept-2"})
    assert resp.status_code == 422


def test_ask_unknown_field_rejected():
    resp = client.post("/ask", json={
        "question": "test",
        "tenant_id": "dept-2",
        "hacker_field": "should be rejected"
    })
    assert resp.status_code == 422

def test_ask_return_503_when_llm_unavailable(monkeypatch):
    captured={}
    def fail_call(question, request_id):
        captured["request_id"] = request_id
        raise LLMUnavailableError(request_id)
    monkeypatch.setattr(
        ask_router.llm_client,
        "call",
        fail_call
    )

    resp = client.post("/ask",json={
        "question": "水泵不转了怎么办？",
        "tenant_id": "dept-2"
    })
    assert resp.status_code == 503
    data = resp.json()
    assert captured["request_id"] == data["request_id"]
    assert data["code"] == 10001
    assert data["msg"] == "维修助手暂时不可用，请稍后再试"
