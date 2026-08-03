from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.security import InMemoryRateLimiter, rate_limit_key
from app.settings import Settings


def test_production_requires_provider_key():
    with pytest.raises(ValidationError):
        Settings(
            environment="production",
            openai_api_key="",
        )


def test_production_rejects_wildcard_cors():
    with pytest.raises(ValidationError):
        Settings(
            environment="production",
            openai_api_key="configured-for-test",
            allowed_origins="*",
        )


def test_production_accepts_closed_cors_configuration():
    configured = Settings(
        environment="production",
        openai_api_key="configured-for-test",
        allowed_origins="",
    )
    assert configured.environment == "production"


def test_rate_limit_key_is_fixed_size_and_does_not_contain_inputs():
    key = rate_limit_key("192.0.2.10", "install-id")
    assert len(key) == 64
    assert "192.0.2.10" not in key
    assert "install-id" not in key


def test_rate_limiter_bounds_new_client_keys():
    limiter = InMemoryRateLimiter(per_minute=1, max_clients=100)
    assert all(limiter.check(f"client-{index}") for index in range(100))
    assert limiter.check("client-100") is False
