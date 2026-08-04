from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    model_config = {"env_prefix": "RM_", "case_sensitive": False}

    # LLM
    llm_api_key: str = ""
    llm_base_url: str = "https://api.deepseek.com"
    llm_model: str = "deepseek-chat"

    # Stub mode：测试时不调真 API
    llm_stub: bool = True

    # 超时（秒）
    llm_connect_timeout: float = 3.0
    llm_read_timeout: float = 30.0

    # JWT（Day31 真实认证）：与 Java 端共享的签名密钥，生产必须换强密钥
    jwt_secret: str = "dev-secret-change-me"
    jwt_audience: str = "repairmind"

    # EquipOps Java 接口（Day32）：开启后工具层走 Java 二次鉴权，否则回退本地样例
    enable_java: bool = False
    java_base_url: str = "http://localhost:8080"

    # Reranker（Day29）：懒加载，未下载时自动回退 hybrid
    rerank_model: str = "BAAI/bge-reranker-base"
    rerank_device: str = "cpu"


settings = Settings()
