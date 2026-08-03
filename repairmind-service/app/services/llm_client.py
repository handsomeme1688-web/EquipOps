import time
import logging

import httpx

from tenacity import (retry, retry_if_exception_type, wait_exponential, stop_after_attempt)
from app.core.config import settings

logger = logging.getLogger(__name__)

class RetryableLLMError(Exception):
    pass


class LLMUnavailableError(Exception):
    def __init__(self, request_id: str):
        super().__init__("LLM 服务暂时不可用")
        self.request_id = request_id

class LLMClient:
    def __init__(self):
        self.api_key = settings.llm_api_key
        self.base_url = settings.llm_base_url
        self.model = settings.llm_model
        self.stub = settings.llm_stub
        self.connect_timeout = settings.llm_connect_timeout
        self.read_timeout = settings.llm_read_timeout


    @retry(
        retry=retry_if_exception_type((httpx.TimeoutException,RetryableLLMError)),
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=0.2, min = 0.2,max=2),
        reraise=True
    )
    def _request_once(self,question:str, request_id:str)->tuple[str,dict[str,int]]|None:
        url = self.base_url.rstrip("/") + "/chat/completions"

        timeout = httpx.Timeout(
            timeout=self.read_timeout,
            connect=self.connect_timeout,
            read=self.read_timeout
        )

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        request_json = {
            "model": self.model,
            "messages": [
                {
                    "role": "user",
                    "content": question
                }
            ],
            "stream": False
        }

        # 真实调用
        with httpx.Client(timeout=timeout) as client:
            response = client.post(
                url,
                headers=headers,
                json=request_json
            )
        if 500 <= response.status_code < 600:
            raise RetryableLLMError(
                f"LLM 服务暂时不可用，status={response.status_code}"
            )
        response.raise_for_status() # 4xx/5xx 抛出 HTTPStatusError
        data = response.json()
        return (data["choices"][0]["message"]["content"],data.get("usage"))

    def call(self,question:str, request_id:str)-> str:
        start = time.perf_counter()
        status="unknown"
        token_usage = None
        try:
            if self.stub:
                status = "stub"
                return f"[stub] 这是对{question}的模拟回复"
            if not self.api_key:
                status = "config_error"
                raise RuntimeError("LLM API Key 未配置")
            answer,token_usage = self._request_once(question, request_id)
            status = "success"
            return answer
        except (httpx.TimeoutException, RetryableLLMError) as exc:
            status  = "degraded"
            raise LLMUnavailableError(request_id) from exc # 表示保留原始异常原因
        finally:
            duration_ms = round((time.perf_counter() - start) * 1000,2)
            logger.info(
                "llm_call",
                extra={
                    "event": "llm_call",
                    "request_id": request_id,
                    "model": self.model,
                    "status": status,
                    "duration_ms": duration_ms,
                    "token_usage": token_usage
                }
            )
