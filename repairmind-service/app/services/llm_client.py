import httpx

from app.core.config import settings


class LLMClient:
    def __init__(self):
        self.api_key = settings.llm_api_key
        self.base_url = settings.llm_base_url
        self.model = settings.llm_model
        self.stub = settings.llm_stub
        self.connect_timeout = settings.llm_connect_timeout
        self.read_timeout = settings.llm_read_timeout
    def call(self,question:str, request_id:str):
        if self.stub:
            return f"[stub] 这是对{question}的模拟回复"

        timeout = httpx.Timeout(
            timeout=self.read_timeout,
            connect=self.connect_timeout,
            read=self.read_timeout
        )
        # 真实调用
        with httpx.Client(timeout=timeout) as client:
            response = client.post(self.base_url,)
        return  response.json()["choices"][0]["message"]["content"]