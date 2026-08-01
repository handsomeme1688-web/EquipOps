import httpx

from app.core.config import settings


class LLMClient:
    def __init__(self):
        self.api_key = settings.llm_api_key
        self.base_url = settings.llm_base_url
        self.model = settings.llm_model
        self.stub = settings.llm_stub
    def call(self,question:str, request_id:str):
        if self.stub:
            return f"[stub] 这是对{question}的模拟回复"

        # 真实调用
        with httpx.Client(timeout=httpx.Timeout(connect=3.0,read=30.0)) as client:
            response = client.post(self.base_url,)
        return  response.json()["choices"][0]["message"]["content"]