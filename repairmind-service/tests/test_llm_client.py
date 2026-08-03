import logging

import httpx
import pytest
from app.services.llm_client import LLMClient, LLMUnavailableError


def find_llm_record(caplog):
    for record in caplog.records:
        if getattr(record, "event", None) == "llm_call":
            return record
    pytest.fail("没有捕获到 event=llm_call 的日志")


class FakeClient:
    call_count = 0

    def __init__(self, *args, **kwargs):
        pass

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        return False # 不要吞掉异常，让异常继续向外抛出

    def post(self, *args, **kwargs):
        FakeClient.call_count += 1
        return httpx.Response(
            status_code=503,
            request=httpx.Request("POST", "http://test")
        )

class SuccessClient:
    def __init__(self, *args, **kwargs):
        pass

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        return False # 不要吞掉异常，让异常继续向外抛出

    def post(self, *args, **kwargs):
        return httpx.Response(
            status_code=200,
            json={
                "choices": [
                    {
                        "message": {
                            "content": "模拟真实回答"
                        }
                    }
                ],
                "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 20,
                    "total_tokens": 30
                }
            },
            request=httpx.Request("POST", "http://test")
        )

class TimeoutClient:
    call_count = 0

    def __init__(self, *args, **kwargs):
        pass

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        return False

    def post(self, *args, **kwargs):
        TimeoutClient.call_count += 1
        raise httpx.ReadTimeout("模拟读取超时")






# 5xx重试逻辑
def test_retry_three_times_when_llm_returns_5xx(monkeypatch):
    FakeClient.call_count = 0
    monkeypatch.setattr(httpx, "Client", FakeClient) # httpx.Client = FakeClient
    client = LLMClient()
    client.stub = False
    client.api_key = "test-key"

    with pytest.raises(LLMUnavailableError):
        client.call("测试问题", "request-1")

    assert FakeClient.call_count == 3

def test_retry_three_times_when_llm_read_times_out(monkeypatch):
    TimeoutClient.call_count=0
    monkeypatch.setattr(httpx, "Client", TimeoutClient)
    client = LLMClient()
    client.stub = False
    client.api_key = "test-key"

    with pytest.raises(LLMUnavailableError):
        client.call("测试问题", "request-2")
    assert TimeoutClient.call_count == 3




def test_record_log_when_stub_mode_of_llm_client_is_true(caplog):
    caplog.set_level(
        logging.INFO,
        logger="app.services.llm_client"
    )
    client = LLMClient()
    client.stub = True

    client.call("测试问题", "request-log-1")

    llm_record = find_llm_record(caplog)
    assert llm_record.request_id == "request-log-1"
    assert llm_record.status == "stub"
    assert llm_record.duration_ms >= 0
    assert llm_record.token_usage is None


def test_successful_call_logs_token_usage(monkeypatch, caplog):
    caplog.set_level(
        logging.INFO,
        logger="app.services.llm_client"
    )
    monkeypatch.setattr(httpx, "Client", SuccessClient)
    client = LLMClient()
    client.stub = False
    client.api_key = "test-key"


    answer = client.call("测试问题", "request-2")

    assert answer == "模拟真实回答"

    llm_record = find_llm_record(caplog)
    assert llm_record.status == "success"
    assert llm_record.token_usage == {
        "prompt_tokens": 10,
        "completion_tokens": 20,
        "total_tokens": 30
    }
    assert llm_record.request_id == "request-2"
