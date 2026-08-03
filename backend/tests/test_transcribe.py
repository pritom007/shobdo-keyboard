"""Tests for POST /v1/transcriptions.

Covers the full error-code table the Android client maps to Bengali messages,
plus WAV validation edge cases (extra chunks, duration vs. byte limits).
"""

from __future__ import annotations

import struct

import pytest
from fastapi.testclient import TestClient

from app.api.transcribe import get_rate_limiter
from app.main import app
from app.security import ProviderError
from app.settings import settings
from tests.conftest import CountingRateLimiter, make_client, make_wav


def _post(client: TestClient, wav: bytes, *, language: str | None = None, device_id: str = "dev1"):
    data = {}
    if language is not None:
        data["language"] = language
    files = {"audio": ("audio.wav", wav, "audio/wav")}
    headers = {"X-Device-Id": device_id}
    return client.post("/v1/transcriptions", files=files, data=data, headers=headers)


def test_transcribe_happy_path(client, fake_provider):
    wav = make_wav(duration_ms=1500)
    resp = _post(client, wav, language="bn")
    assert resp.status_code == 200
    body = resp.json()
    assert body["text"] == "আমি ভালো আছি"
    assert body["language"] == "bn"
    assert body["duration_ms"] == 1500
    # Provider received the raw bytes, the filename, and the language.
    assert len(fake_provider.calls) == 1
    audio_bytes, filename, language = fake_provider.calls[0]
    assert audio_bytes == wav
    assert filename == "audio.wav"
    assert language == "bn"


def test_transcribe_default_language_is_bengali(client, fake_provider):
    wav = make_wav(duration_ms=500)
    resp = _post(client, wav, language=None)
    assert resp.status_code == 200
    assert resp.json()["language"] == "bn"
    assert fake_provider.calls[0][2] == "bn"


def test_transcribe_no_audio(client):
    resp = _post(client, b"")
    assert resp.status_code == 400
    assert resp.json()["error"]["code"] == "NO_AUDIO"


def test_transcribe_bad_audio_not_wav(client):
    resp = _post(client, b"this is not a wav file at all")
    assert resp.status_code == 400
    assert resp.json()["error"]["code"] == "BAD_AUDIO"


def test_transcribe_rejects_unexpected_content_type(client):
    response = client.post(
        "/v1/transcriptions",
        files={"audio": ("audio.txt", make_wav(), "text/plain")},
        headers={"X-Device-Id": "dev1"},
    )
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "BAD_AUDIO"


def test_transcribe_rejects_missing_configured_secret(client, monkeypatch):
    monkeypatch.setattr(settings, "shohojakkhor_shared_secret", "configured-for-test")
    resp = _post(client, make_wav(duration_ms=100))
    assert resp.status_code == 401
    assert resp.json()["error"]["code"] == "UNAUTHORIZED"


def test_transcribe_accepts_matching_configured_secret(client, monkeypatch):
    monkeypatch.setattr(settings, "shohojakkhor_shared_secret", "configured-for-test")
    resp = client.post(
        "/v1/transcriptions",
        files={"audio": ("audio.wav", make_wav(duration_ms=100), "audio/wav")},
        headers={
            "X-Device-Id": "dev1",
            "X-Shohojakkhor-Secret": "configured-for-test",
        },
    )
    assert resp.status_code == 200


def test_transcribe_too_long(client):
    # 65s exceeds the 60s cap but stays under the 2 MB byte cap.
    wav = make_wav(duration_ms=65_000)
    assert len(wav) < 2_097_152  # sanity: it's the duration, not size, that trips
    resp = _post(client, wav)
    assert resp.status_code == 413
    assert resp.json()["error"]["code"] == "TOO_LONG"


def test_transcribe_too_large(client):
    # 2.1 MB of non-WAV bytes triggers the bounded read before WAV validation.
    big = b"\x00" * (2_097_152 + 1024)
    resp = _post(client, big)
    assert resp.status_code == 413
    assert resp.json()["error"]["code"] == "TOO_LARGE"


def test_transcribe_rate_limited(fake_provider):
    limiter = CountingRateLimiter(limit=1)
    app.dependency_overrides[get_rate_limiter] = lambda: limiter
    try:
        client = make_client()
        wav = make_wav(duration_ms=500)
        first = _post(client, wav)
        second = _post(client, wav)
        assert first.status_code == 200
        assert second.status_code == 429
        assert second.json()["error"]["code"] == "RATE_LIMIT"
        assert limiter.allowed == 1
        assert limiter.refused == 1
    finally:
        app.dependency_overrides.pop(get_rate_limiter, None)


def test_transcribe_rate_limit_is_per_device(fake_provider):
    limiter = CountingRateLimiter(limit=1)
    app.dependency_overrides[get_rate_limiter] = lambda: limiter
    try:
        client = make_client()
        wav = make_wav(duration_ms=500)
        a1 = _post(client, wav, device_id="device-a")
        b1 = _post(client, wav, device_id="device-b")
        a2 = _post(client, wav, device_id="device-a")
        assert a1.status_code == 200
        assert b1.status_code == 200  # different device, independent budget
        assert a2.status_code == 429
    finally:
        app.dependency_overrides.pop(get_rate_limiter, None)


@pytest.mark.parametrize(
    "exc",
    [
        ProviderError("PROVIDER_TIMEOUT", "timed out", 504),
        ProviderError("PROVIDER_RATE_LIMIT", "rl", 503),
        ProviderError("PROVIDER_ERROR", "boom", 502),
    ],
)
def test_transcribe_provider_error_status_mapping(client, fake_provider, exc):
    fake_provider.error = exc
    resp = _post(client, make_wav(duration_ms=500))
    assert resp.status_code == exc.status
    assert resp.json()["error"]["code"] == exc.code


def test_transcribe_wav_with_extra_junk_chunk_still_parses(client, fake_provider):
    """A recorder that prepends a JUNK chunk must still yield the right duration."""
    data = b"\x00\x00" * 8000  # 0.5s of 16kHz mono silence
    data_size = len(data)
    junk = b"JUNK" + struct.pack("<I", 8) + b"\x00" * 8
    fmt_chunk = b"fmt " + struct.pack("<I", 16) + struct.pack("<H", 1) + struct.pack("<H", 1) + struct.pack("<I", 16000) + struct.pack("<I", 32000) + struct.pack("<H", 2) + struct.pack("<H", 16)
    data_chunk = b"data" + struct.pack("<I", data_size) + data
    body = b"RIFF" + struct.pack("<I", 4 + len(fmt_chunk) + len(junk) + len(data_chunk)) + b"WAVE" + fmt_chunk + junk + data_chunk

    resp = _post(client, body)
    assert resp.status_code == 200
    assert resp.json()["duration_ms"] == 500


def test_transcribe_stereo_wav_duration(client, fake_provider):
    # 0.5s of 16kHz stereo (2x samples per frame) -> 500ms
    data = b"\x00\x00" * (8000 * 2)
    data_size = len(data)
    fmt_chunk = b"fmt " + struct.pack("<I", 16) + struct.pack("<H", 1) + struct.pack("<H", 2) + struct.pack("<I", 16000) + struct.pack("<I", 64000) + struct.pack("<H", 4) + struct.pack("<H", 16)
    data_chunk = b"data" + struct.pack("<I", data_size) + data
    body = b"RIFF" + struct.pack("<I", 4 + len(fmt_chunk) + len(data_chunk)) + b"WAVE" + fmt_chunk + data_chunk
    resp = _post(client, body)
    assert resp.status_code == 200
    assert resp.json()["duration_ms"] == 500


def test_transcribe_rejects_truncated_data_chunk(client):
    wav = bytearray(make_wav(duration_ms=100))
    data_size_offset = wav.index(b"data") + 4
    struct.pack_into("<I", wav, data_size_offset, 1_000_000)
    resp = _post(client, bytes(wav))
    assert resp.status_code == 400
    assert resp.json()["error"]["code"] == "BAD_AUDIO"


def test_transcribe_rejects_non_pcm_wav(client):
    wav = bytearray(make_wav(duration_ms=100))
    struct.pack_into("<H", wav, 20, 3)  # IEEE float, not PCM
    resp = _post(client, bytes(wav))
    assert resp.status_code == 400
    assert resp.json()["error"]["code"] == "BAD_AUDIO"


def test_security_headers_disable_sniffing_and_storage(client):
    resp = _post(client, make_wav(duration_ms=100))
    assert resp.headers["x-content-type-options"] == "nosniff"
    assert resp.headers["cache-control"] == "no-store"
    assert resp.headers["referrer-policy"] == "no-referrer"
