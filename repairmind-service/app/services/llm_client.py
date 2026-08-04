import logging
import time

import httpx
from tenacity import (retry, retry_if_exception_type, stop_after_attempt,
                      wait_exponential)

from app.core.config import settings

logger = logging.getLogger(__name__)


class RetryableLLMError(Exception):
    """可重试的瞬时错误：超时 / 5xx。"""


class LLMUnavailableError(Exception):
    """重试仍失败后的降级错误，携带 request_id 供用户反馈定位。"""

    def __init__(self, request_id: str):
        super().__init__("LLM 服务暂时不可用")
        self.request_id = request_id


class LLMClient:
    """DeepSeek 聊天补全封装：超时 / 有限重试 / 降级 / stub 模式。

    对应手册 P2 · Day27【核心】：
    - connect 与 read 超时分开设置（connect 3s / read 30s）；
    - 有限重试：仅对超时与 5xx、指数退避、最多重试 2 次（共 3 次尝试）；
    - 重试仍失败 → 抛 LLMUnavailableError，由路由层转成结构化降级响应，
      绝不裸抛堆栈给用户；
    - stub 模式（RM_LLM_STUB=1）返回固定答案，供测试与无 Key 演示使用。
    """

    def __init__(self):
        self.api_key = settings.llm_api_key
        self.base_url = settings.llm_base_url
        self.model = settings.llm_model
        self.stub = settings.llm_stub
        self.connect_timeout = settings.llm_connect_timeout
        self.read_timeout = settings.llm_read_timeout

    @retry(
        retry=retry_if_exception_type((httpx.TimeoutException, RetryableLLMError)),
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=0.2, min=0.2, max=2),
        reraise=True,
    )
    def _request_once(self, messages: list[dict], request_id: str, tools: list | None = None):
        """单次真实的 HTTP 调用。tools 传入时按 OpenAI Function Calling 格式下发。"""
        url = self.base_url.rstrip("/") + "/chat/completions"
        timeout = httpx.Timeout(
            timeout=self.read_timeout,
            connect=self.connect_timeout,
            read=self.read_timeout,
        )
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        request_json = {
            "model": self.model,
            "messages": messages,
            "stream": False,
        }
        if tools:
            request_json["tools"] = tools

        with httpx.Client(timeout=timeout) as client:
            response = client.post(url, headers=headers, json=request_json)

        if 500 <= response.status_code < 600:
            raise RetryableLLMError(f"LLM 服务暂时不可用，status={response.status_code}")
        response.raise_for_status()  # 4xx 抛出 HTTPStatusError，不可重试

        data = response.json()
        message = data["choices"][0]["message"]
        return message.get("content"), data.get("usage"), message.get("tool_calls")

    def _chat(self, messages: list[dict], request_id: str, tools: list | None = None) -> dict:
        """统一入口：处理 stub / 缺 Key / 降级 / 日志。"""
        start = time.perf_counter()
        status = "unknown"
        token_usage = None
        try:
            if self.stub:
                status = "stub"
                return {
                    "content": f"[stub] 这是对{messages[-1]['content']}的模拟回复",
                    "tool_calls": None,
                    "usage": None,
                }
            if not self.api_key:
                status = "config_error"
                raise RuntimeError("LLM API Key 未配置")
            content, token_usage, tool_calls = self._request_once(messages, request_id, tools=tools)
            status = "success"
            return {"content": content, "tool_calls": tool_calls, "usage": token_usage}
        except (httpx.TimeoutException, RetryableLLMError) as exc:
            status = "degraded"
            raise LLMUnavailableError(request_id) from exc
        finally:
            duration_ms = round((time.perf_counter() - start) * 1000, 2)
            logger.info(
                "llm_call",
                extra={
                    "event": "llm_call",
                    "request_id": request_id,
                    "model": self.model,
                    "status": status,
                    "duration_ms": duration_ms,
                    "token_usage": token_usage,
                },
            )

    def call(self, question: str, request_id: str, system: str | None = None) -> str:
        """单轮文本问答（RAG 用它）。可选 system 提示词。"""
        messages = []
        if system:
            messages.append({"role": "system", "content": system})
        messages.append({"role": "user", "content": question})
        return self._chat(messages, request_id)["content"]

    def chat(self, messages: list[dict], request_id: str, tools: list | None = None) -> dict:
        """多轮对话 + 工具调用（Agent 用它）。返回 content 与 tool_calls。"""
        return self._chat(messages, request_id, tools=tools)


# 共享单例：ask / rag / agent 都复用同一个实例，
# 测试里 monkeypatch 该实例的 .call / .chat 即可统一模拟 LLM。
llm_client = LLMClient()
