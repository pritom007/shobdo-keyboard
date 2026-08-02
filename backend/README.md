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

- Python 3.10+, FastAPI, Pydantic v2
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
- **Header:** `X-Device-Id` — opaque install-local ID used with the source
  address for coarse abuse prevention. It is hashed in memory and is not an
  authentication credential.
- **200:** `{ "text": "...", "language": "bn", "duration_ms": 4500 }`
- **Errors** carry a stable `code` the Android client maps to a Bengali
  message:

  | Status | Code | Meaning |
  |---|---|---|
  | 400 | `NO_AUDIO` | empty upload |
  | 400 | `BAD_AUDIO` | not a parseable WAV |
  | 413 | `TOO_LARGE` | exceeds `MAX_AUDIO_BYTES` |
  | 413 | `TOO_LONG` | exceeds `MAX_AUDIO_SECONDS` |
  | 429 | `RATE_LIMIT` | request-rate budget exceeded |
  | 401 | `UNAUTHORIZED` | shared-secret mismatch (when configured) |
  | 502/503/504 | `PROVIDER_*` | upstream speech-provider failure |

## Non-negotiable rules

1. **No user content in any log.** No audio bytes, no transcripts, no drafts.
   The access log shows only the path; bodies are never logged.
2. **No persistence of audio, transcripts, or drafts.** Audio is streamed to
   the provider and dropped; the response is returned and dropped.
3. **Provider keys live only here**, never in the Android APK.
4. **Strict request-size and duration limits** (env-configurable).
5. **Production fails closed.** `ENVIRONMENT=production` requires a provider
   key and client-auth secret and rejects wildcard CORS.

## Scaling plan (the "100k users" path — app doesn't change)

1. **Now (v1):** one process, bounded in-memory rate limit, no DB. Suitable
   for controlled prototype traffic behind HTTPS.
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
# Set OPENAI_API_KEY locally. Never paste the value into source or docs.
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

For deployment, set `ENVIRONMENT=production`, use HTTPS, configure
`SHOBDO_SHARED_SECRET`, and restrict network access at a gateway. A static
secret embedded in a public APK can be extracted, so it is only a prototype
control; production distribution needs short-lived per-install credentials or
platform attestation.

## Render deployment

The repository root contains `render.yaml`, a backend-only Render Blueprint.
It configures a free Python service in Singapore with:

- `backend/` as the service root directory
- automatic deployment only after GitHub checks pass
- `/health` deployment health checks
- a generated backend authentication secret
- a dashboard-supplied provider key (`OPENAI_API_KEY`)
- build filtering so Android-only changes do not redeploy the backend

Create the service from the Blueprint and enter `OPENAI_API_KEY` directly in
Render when prompted. Never put its value in Git, CI configuration, issues, or
chat. Subsequent backend commits deploy automatically after CI succeeds.

The generated `SHOBDO_SHARED_SECRET` deliberately is not embedded in Android.
Until the client has short-lived authentication, production transcription calls
will receive `401`; this is a secure fail-closed state, not a deployment error.

## See also

- [`../docs/privacy-model.md`](../docs/privacy-model.md)
- [`../docs/security-threat-model.md`](../docs/security-threat-model.md)
