from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_ask_echo():
    resp = client.post("/ask", json={"question": "水泵不转了怎么办？", "tenant_id": "dept-2"})
    assert resp.status_code == 200
    data = resp.json()
    assert "echo" in data["answer"]
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
