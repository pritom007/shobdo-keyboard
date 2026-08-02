"""POST /v1/transcriptions — audio in, text out.

Contract (stable across backend rewrites; the Android client depends on it):

  Request:
    multipart/form-data
      - audio:        WAV file (16 kHz, mono, 16-bit PCM preferred)
      - language:     optional ISO 639-1 code, default "bn" (Bengali first)
    header:
      - X-Device-Id:  opaque install-local ID used ONLY for rate limiting

  Response 200:
    { "text": "...", "language": "bn", "duration_ms": 4500 }

  Error responses carry a stable `code` the client maps to a Bengali message:
    400 NO_AUDIO        — empty upload
    400 BAD_AUDIO       — not a parseable WAV
    413 TOO_LARGE       — exceeds MAX_AUDIO_BYTES
    413 TOO_LONG        — exceeds MAX_AUDIO_SECONDS
    429 RATE_LIMIT      — per-device rate exceeded
    401 UNAUTHORIZED    — shared secret mismatch (when configured)
    502/503/504 PROVIDER_* — upstream speech provider failure
"""

from __future__ import annotations

import hmac
import struct
from typing import Optional

from fastapi import APIRouter, Depends, File, Header, HTTPException, Request, UploadFile, status
from starlette.concurrency import run_in_threadpool

from ..providers.speech import SpeechProvider
from ..security import InMemoryRateLimiter, ProviderError, RateLimiter, rate_limit_key
from ..settings import settings

router = APIRouter(tags=["transcribe"])

# --- Shared-secret auth (optional) -------------------------------------------


def _require_secret(
    x_shobdo_secret: Optional[str] = Header(default=None, alias="X-Shobdo-Secret"),
) -> None:
    if not settings.shobdo_shared_secret:
        return
    supplied = x_shobdo_secret or ""
    if not hmac.compare_digest(supplied, settings.shobdo_shared_secret):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={"error": {"code": "UNAUTHORIZED"}},
        )


# --- Dependency-injected collaborators (tests override these) -----------------


def get_provider() -> SpeechProvider:
    """Default provider — lazily constructed on first use.

    If the API key isn't configured, every request fails with a clean
    PROVIDER_NOT_CONFIGURED error (rather than crashing at import time).
    """
    global _default_provider
    if _default_provider is None:
        from ..providers.speech import OpenAICompatSpeechProvider

        _default_provider = OpenAICompatSpeechProvider(
            api_key=settings.openai_api_key,
            base_url=settings.openai_base_url,
            model=settings.openai_transcribe_model,
            timeout_ms=settings.provider_timeout_ms,
        )
    return _default_provider


_default_provider: SpeechProvider | None = None


def get_rate_limiter() -> RateLimiter:
    global _default_rate_limiter
    if _default_rate_limiter is None:
        _default_rate_limiter = InMemoryRateLimiter(
            settings.rate_limit_per_minute,
            settings.rate_limit_max_clients,
        )
    return _default_rate_limiter


_default_rate_limiter: RateLimiter | None = None


# --- Endpoint -----------------------------------------------------------------


@router.post("/v1/transcriptions")
async def transcribe(
    request: Request,
    audio: UploadFile = File(...),
    language: str = "bn",
    x_device_id: Optional[str] = Header(default=None, alias="X-Device-Id"),
    provider: SpeechProvider = Depends(get_provider),
    limiter: RateLimiter = Depends(get_rate_limiter),
    _auth: None = Depends(_require_secret),
) -> dict:
    # Rate-limit before reading the body — cheapest abuse guard.
    client_host = request.client.host if request.client else None
    if not limiter.check(rate_limit_key(client_host, x_device_id)):
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail={"error": {"code": "RATE_LIMIT"}},
        )

    if audio.content_type not in {"audio/wav", "audio/x-wav", "application/octet-stream"}:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": {"code": "BAD_AUDIO"}},
        )

    raw = await _read_bounded(audio, settings.max_audio_bytes)

    if not raw:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": {"code": "NO_AUDIO"}},
        )

    duration_ms = _wav_duration_ms(raw)
    if duration_ms is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"error": {"code": "BAD_AUDIO"}},
        )
    if duration_ms > settings.max_audio_seconds * 1000:
        raise HTTPException(
            status_code=413,
            detail={"error": {"code": "TOO_LONG"}},
        )

    try:
        text = await run_in_threadpool(
            provider.transcribe,
            audio_bytes=raw,
            filename=audio.filename or "audio.wav",
            language=language or None,
        )
    except ProviderError as exc:
        raise HTTPException(
            status_code=exc.status,
            detail={"error": {"code": exc.code}},
        ) from exc

    return {
        "text": text,
        "language": language,
        "duration_ms": duration_ms,
    }


# --- Helpers ------------------------------------------------------------------


async def _read_bounded(file: UploadFile, max_bytes: int) -> bytes:
    """Stream-read the upload, refusing anything larger than `max_bytes`.

    Prevents OOM and 413s before the whole body is buffered.
    """
    chunks: list[bytes] = []
    total = 0
    while True:
        chunk = await file.read(64 * 1024)
        if not chunk:
            break
        total += len(chunk)
        if total > max_bytes:
            raise HTTPException(
                status_code=413,
                detail={"error": {"code": "TOO_LARGE"}},
            )
        chunks.append(chunk)
    return b"".join(chunks)


def _wav_duration_ms(raw: bytes) -> int | None:
    """Return the WAV duration in ms, or None if `raw` is not a valid WAV.

    Walks the RIFF chunks to find the `data` chunk and computes duration from
    the fmt chunk's sample rate + channel count. Tolerant of extra chunks
    (LIST/JUNK/etc.) that some recorders prepend.
    """
    if len(raw) < 44 or raw[0:4] != b"RIFF" or raw[8:12] != b"WAVE":
        return None

    try:
        # fmt chunk is expected first; header: id(4) size(4) then payload.
        if raw[12:16] != b"fmt ":
            return None
        fmt_size = struct.unpack_from("<I", raw, 16)[0]
        if fmt_size < 16 or 20 + fmt_size > len(raw):
            return None
        audio_format = struct.unpack_from("<H", raw, 20)[0]
        channels = struct.unpack_from("<H", raw, 22)[0]
        sample_rate = struct.unpack_from("<I", raw, 24)[0]
        byte_rate = struct.unpack_from("<I", raw, 28)[0]
        block_align = struct.unpack_from("<H", raw, 32)[0]
        bits_per_sample = struct.unpack_from("<H", raw, 34)[0]
        if (
            audio_format != 1
            or channels not in {1, 2}
            or sample_rate < 8_000
            or sample_rate > 192_000
            or bits_per_sample != 16
            or block_align != channels * 2
            or byte_rate != sample_rate * block_align
        ):
            return None

        # Walk subsequent chunks to find "data".
        idx = 12 + 8 + fmt_size
        data_size: int | None = None
        while idx + 8 <= len(raw):
            chunk_id = raw[idx : idx + 4]
            chunk_size = struct.unpack_from("<I", raw, idx + 4)[0]
            chunk_end = idx + 8 + chunk_size
            if chunk_end > len(raw):
                return None
            if chunk_id == b"data":
                data_size = chunk_size
                break
            idx += 8 + chunk_size + (chunk_size & 1)  # pad to even

        if data_size is None:
            return None

        seconds = data_size / byte_rate
        return int(seconds * 1000)
    except (struct.error, IndexError):
        return None
