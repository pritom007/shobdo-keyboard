"""Shared test fixtures.

The transcribe endpoint uses dependency injection for the speech provider and
rate limiter, so tests substitute fakes via FastAPI dependency overrides —
no monkeypatching of internals required.
"""

from __future__ import annotations

import struct

import pytest
from fastapi.testclient import TestClient

from app.api.transcribe import get_provider, get_rate_limiter
from app.main import app
from app.security import ProviderError


class FakeSpeechProvider:
    """Test double that returns a canned transcript or raises `ProviderError`."""

    def __init__(
        self,
        *,
        text: str = "আমি ভালো আছি",
        error: ProviderError | None = None,
    ) -> None:
        self.text = text
        self.error = error
        self.calls: list[tuple[bytes, str, str | None]] = []

    def transcribe(self, audio_bytes: bytes, filename: str, language: str | None) -> str:
        self.calls.append((audio_bytes, filename, language))
        if self.error is not None:
            raise self.error
        return self.text


class CountingRateLimiter:
    """Per-device counter matching the real InMemoryRateLimiter contract.

    Each device gets its own `limit` budget. Tracks totals for assertions.
    """

    def __init__(self, limit: int = 1000) -> None:
        self.limit = limit
        self.allowed = 0
        self.refused = 0
        self._per_device: dict[str, int] = {}

    def check(self, device_id: str) -> bool:
        used = self._per_device.get(device_id, 0)
        if used < self.limit:
            self._per_device[device_id] = used + 1
            self.allowed += 1
            return True
        self.refused += 1
        return False


@pytest.fixture()
def fake_provider():
    provider = FakeSpeechProvider()
    app.dependency_overrides[get_provider] = lambda: provider
    yield provider
    app.dependency_overrides.pop(get_provider, None)


@pytest.fixture()
def fresh_limiter():
    limiter = CountingRateLimiter(limit=1000)
    app.dependency_overrides[get_rate_limiter] = lambda: limiter
    yield limiter
    app.dependency_overrides.pop(get_rate_limiter, None)


@pytest.fixture()
def client(fake_provider, fresh_limiter):
    return TestClient(app)


def make_client() -> TestClient:
    """TestClient bound to the app, for tests that override dependencies."""
    return TestClient(app)


def make_wav(duration_ms: int = 1000, sample_rate: int = 16000, channels: int = 1) -> bytes:
    """Build a minimal valid (silent) 16-bit PCM WAV of the requested duration."""
    n_samples = int(sample_rate * channels * duration_ms / 1000)
    data = b"\x00\x00" * n_samples
    data_size = len(data)
    byte_rate = sample_rate * channels * 2
    block_align = channels * 2
    fmt_chunk = (
        b"fmt "
        + struct.pack("<I", 16)
        + struct.pack("<H", 1)            # audio_format = PCM
        + struct.pack("<H", channels)
        + struct.pack("<I", sample_rate)
        + struct.pack("<I", byte_rate)
        + struct.pack("<H", block_align)
        + struct.pack("<H", 16)           # bits per sample
    )
    data_chunk = b"data" + struct.pack("<I", data_size) + data
    riff_size = 4 + len(fmt_chunk) + len(data_chunk)
    return b"RIFF" + struct.pack("<I", riff_size) + b"WAVE" + fmt_chunk + data_chunk
