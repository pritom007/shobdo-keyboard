"""Application configuration via environment variables (12-factor).

All runtime knobs live here so the rest of the code never reads os.environ
directly. Swap providers / limits / ports by editing .env — no code change.
"""

from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # --- Provider (OpenAI-compatible client) ---------------------------------
    # Works against Groq, OpenAI, DeepSeek, Together, vLLM, LM Studio, Ollama.
    # Swap base_url + model to switch providers — no code change.
    openai_api_key: str = ""
    openai_base_url: str = "https://api.groq.com/openai/v1"
    openai_transcribe_model: str = "whisper-large-v3"

    # --- Server --------------------------------------------------------------
    host: str = "0.0.0.0"
    port: int = 8000
    log_level: str = "INFO"

    # --- CORS ----------------------------------------------------------------
    # Comma-separated origins. "*" (default) is fine for dev; restrict in prod.
    allowed_origins: str = "*"

    # --- Auth ----------------------------------------------------------------
    # Optional shared secret. If set, clients must send `X-Shobdo-Secret`.
    # Empty = no auth (dev only). Per-install tokens land in a later milestone.
    shobdo_shared_secret: str = ""

    # --- Limits --------------------------------------------------------------
    max_audio_bytes: int = 2_097_152      # 2 MB
    max_audio_seconds: int = 60
    rate_limit_per_minute: int = 20

    # --- Timeouts ------------------------------------------------------------
    provider_timeout_ms: int = 20_000


settings = Settings()
