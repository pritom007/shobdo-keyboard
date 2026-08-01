# Backend — Shobdo Keyboard

A focused, **stateless** speech-to-text proxy for the Shobdo Keyboard Android
IME. Bengali is the primary language. Audio in, text out, nothing persisted.

> **v1 scope: transcription only.** Rewrite / revise endpoints (planned in
> `docs/backend-api.md`) land in a later milestone. This keeps the surface
> small and Play Store updates trivial — the app only knows one endpoint.

## Why a backend at all?

The provider API key (Groq by default) **never ships in the APK** — it would
leak in days. So the key lives here, in environment variables. The app calls
this proxy; the proxy calls the provider. Zero friction for elderly users
(no per-user API key), key stays safe, and you can swap Groq → OpenAI → local
whisper.cpp without updating the app.

## Stack

- Python 3.11+, FastAPI, Pydantic v2
- `openai` SDK (OpenAI-compatible — works against Groq, OpenAI, DeepSeek,
  Together, vLLM, LM Studio, Ollama)
- pytest + httpx for tests
- Docker for deployment
- **No database, no persistence, no user accounts**

## Layout

```
backend/
├── app/
│   ├── main.py            FastAPI app + CORS
│   ├── settings.py        env-var config (12-factor)
│   ├── security.py        RateLimiter protocol + in-memory impl, ProviderError
│   ├── api/
│   │   ├── health.py      GET /health
│   │   └── transcribe.py  POST /v1/transcriptions
│   └── providers/
│       └── speech.py      SpeechProvider protocol + OpenAI-compatible impl
├── tests/                health + transcribe (provider & limiter mocked)
├── Dockerfile
├── pyproject.toml
└── .env.example
```

## API

### `GET /health`
Liveness probe. `{ "status": "ok", "service": "shobdo-backend", "version": "..." }`

### `POST /v1/transcriptions`
- **Request:** `multipart/form-data`
  - `audio` — WAV file (16 kHz mono 16-bit PCM preferred)
  - `language` — optional ISO 639-1 code, default `bn` (Bengali first)
- **Header:** `X-Device-Id` — opaque install-local ID, used ONLY for rate
  limiting. No accounts, no tracking.
- **200:** `{ "text": "...", "language": "bn", "duration_ms": 4500 }`
- **Errors** carry a stable `code` the Android client maps to a Bengali
  message:

  | Status | Code | Meaning |
  |---|---|---|
  | 400 | `NO_AUDIO` | empty upload |
  | 400 | `BAD_AUDIO` | not a parseable WAV |
  | 413 | `TOO_LARGE` | exceeds `MAX_AUDIO_BYTES` |
  | 413 | `TOO_LONG` | exceeds `MAX_AUDIO_SECONDS` |
  | 429 | `RATE_LIMIT` | per-device rate exceeded |
  | 401 | `UNAUTHORIZED` | shared-secret mismatch (when configured) |
  | 502/503/504 | `PROVIDER_*` | upstream speech-provider failure |

## Non-negotiable rules

1. **No user content in any log.** No audio bytes, no transcripts, no drafts.
   The access log shows only the path; bodies are never logged.
2. **No persistence of audio, transcripts, or drafts.** Audio is streamed to
   the provider and dropped; the response is returned and dropped.
3. **Provider keys live only here**, never in the Android APK.
4. **Strict request-size and duration limits** (env-configurable).

## Scaling plan (the "100k users" path — app doesn't change)

1. **Now (v1):** one process, in-memory rate limit, no DB. Handles hundreds
   of concurrent users per instance. Deploy on Fly.io / Render free tier.
2. **Growing:** run 2+ instances behind a load balancer; swap
   `InMemoryRateLimiter` for a Redis-backed `RateLimiter` (one file changes;
   the protocol and endpoint are untouched).
3. **100k users:** add Postgres for per-device quota tracking, a queue for
   audio processing, a CDN for uploads. **The app keeps calling
   `/v1/transcriptions` exactly as before.** If the contract ever changes,
   version it (`/v2/transcriptions`) and keep `/v1` alive so old APKs keep
   working — no forced Play Store update.

## Local run

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env
# fill in OPENAI_API_KEY (Groq key from https://console.groq.com)
uvicorn app.main:app --reload --port 8000
```

## Tests

```bash
cd backend
pytest -q
```

Provider and rate limiter are dependency-injected, so tests run without an API
key — the provider is faked and the limiter is replaced with a deterministic
counter.

## Docker

```bash
docker build -t shobdo-backend ./backend
docker run -p 8000:8000 --env-file backend/.env shobdo-backend
```

## See also

- [`../docs/privacy-model.md`](../docs/privacy-model.md)
- [`../docs/security-threat-model.md`](../docs/security-threat-model.md)
