"""Speech-to-text provider abstraction.

The `SpeechProvider` protocol is the only thing the API layer knows about.
`OpenAICompatSpeechProvider` is the default concrete implementation and works
against any OpenAI-compatible transcription endpoint (Groq, OpenAI, ...).
To add a different backend (e.g. local whisper.cpp), implement the protocol
and wire it in `app/main.py` — the endpoint code is untouched.
"""

from __future__ import annotations

from typing import Protocol

from ..security import ProviderError


class SpeechProvider(Protocol):
    def transcribe(
        self,
        audio_bytes: bytes,
        filename: str,
        language: str | None,
    ) -> str:
        """Return the transcribed text for the given WAV audio bytes.

        Raise `ProviderError` on any failure so the API layer can map it to a
        stable error code. Never log the audio bytes or the resulting text.
        """
        ...


class OpenAICompatSpeechProvider:
    """OpenAI-compatible transcription via the `openai` SDK.

    Configure with `OPENAI_API_KEY`, `OPENAI_BASE_URL`, and
    `OPENAI_TRANSCRIBE_MODEL`. Groq is the default base URL. Works against
    OpenAI, DeepSeek, Together, vLLM, LM Studio, Ollama, etc.
    """

    def __init__(
        self,
        api_key: str,
        base_url: str,
        model: str,
        timeout_ms: int,
    ) -> None:
        # Import locally so the module imports cleanly without the SDK
        # installed (useful for static analysis and some test setups).
        # Note: a missing key is NOT fatal here — it surfaces as a clean
        # PROVIDER_NOT_CONFIGURED error at call time, which the API layer maps
        # to a 503 instead of crashing at dependency-injection time.
        from openai import OpenAI

        self._api_key = api_key
        self._client = OpenAI(
            api_key=api_key or "missing",
            base_url=base_url,
            timeout=timeout_ms / 1000.0,
        ) if api_key else None
        self._model = model

    def transcribe(
        self,
        audio_bytes: bytes,
        filename: str,
        language: str | None,
    ) -> str:
        if not self._api_key or self._client is None:
            raise ProviderError(
                "PROVIDER_NOT_CONFIGURED",
                "Provider API key is not set on the server",
                status=503,
            )
        from openai import APIError, APITimeoutError, RateLimitError

        try:
            resp = self._client.audio.transcriptions.create(
                model=self._model,
                file=(filename, audio_bytes, "audio/wav"),
                language=language,
                response_format="text",
            )
        except APITimeoutError as exc:
            raise ProviderError(
                "PROVIDER_TIMEOUT",
                "Speech provider timed out",
                status=504,
            ) from exc
        except RateLimitError as exc:
            raise ProviderError(
                "PROVIDER_RATE_LIMIT",
                "Speech provider rate-limited the server",
                status=503,
            ) from exc
        except APIError as exc:
            # Deliberately do NOT include exc body (may echo audio metadata).
            raise ProviderError(
                "PROVIDER_ERROR",
                f"Speech provider returned an error: {type(exc).__name__}",
                status=502,
            ) from exc
        except Exception as exc:
            # Catch-all so a provider hiccup never 500s with a stack trace
            # that could leak audio metadata. Type name only — no body.
            raise ProviderError(
                "PROVIDER_ERROR",
                f"Unexpected provider failure: {type(exc).__name__}",
                status=502,
            ) from exc

        # `response_format="text"` returns a string-like object.
        return resp if isinstance(resp, str) else str(resp).strip()
