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


settings = Settings()
