"""Application configuration via environment variables (12-factor).

All runtime knobs live here so the rest of the code never reads os.environ
directly. Swap providers / limits / ports by editing .env — no code change.
"""

from __future__ import annotations

from typing import Literal

from pydantic import Field, model_validator
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
    environment: Literal["development", "test", "production"] = "development"
    host: str = "0.0.0.0"
    port: int = Field(default=8000, ge=1, le=65535)
    log_level: str = "INFO"

    # --- CORS ----------------------------------------------------------------
    # Native Android clients do not need CORS. Enable only for a known web UI.
    allowed_origins: str = ""

    # --- Limits --------------------------------------------------------------
    max_audio_bytes: int = Field(default=2_097_152, ge=44, le=25_000_000)
    max_audio_seconds: int = Field(default=60, ge=1, le=600)
    rate_limit_per_minute: int = Field(default=20, ge=1, le=10_000)
    rate_limit_max_clients: int = Field(default=10_000, ge=100, le=1_000_000)

    # --- Timeouts ------------------------------------------------------------
    provider_timeout_ms: int = Field(default=20_000, ge=1_000, le=120_000)

    @model_validator(mode="after")
    def validate_production_security(self) -> "Settings":
        """Refuse a production boot with unsafe or incomplete configuration."""
        if self.environment != "production":
            return self

        missing: list[str] = []
        if not self.openai_api_key.strip():
            missing.append("OPENAI_API_KEY")
        if missing:
            raise ValueError(f"production requires: {', '.join(missing)}")

        origins = {value.strip() for value in self.allowed_origins.split(",") if value.strip()}
        if "*" in origins:
            raise ValueError("ALLOWED_ORIGINS cannot contain '*' in production")
        return self


settings = Settings()
