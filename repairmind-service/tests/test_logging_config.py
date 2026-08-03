import json
import logging

from app.core.logging_config import JsonFormatter

def test_json_formatter_serializes_log_record():
    record = logging.LogRecord(
        name="app.services.llm_client",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg="llm_call",
        args=(),
        exc_info=None
    )
    record.event = "llm_call"
    record.request_id = "request-log-1"
    record.model = "deepseek-chat"
    record.status = "stub"
    record.duration_ms = 10000
    record.token_usage = None
    json_line = JsonFormatter().format(record=record)
    data = json.loads(json_line)

    assert isinstance(data["timestamp"], str)
    assert data["level"] == "INFO"
    assert data["logger"] == "app.services.llm_client"
    assert data["message"] == "llm_call"
    assert data["event"] == "llm_call"
    assert data["request_id"] =="request-log-1"
    assert data["model"] == "deepseek-chat"
    assert data["status"] == "stub"
    assert data["duration_ms"] == 10000
    assert data["token_usage"] is None
