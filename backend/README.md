# Backend — Shobdo Keyboard

> ⚠️ **Skeleton only.** No FastAPI code lives here yet. The backend lands with
> **Milestone 2** (transcription) and **Milestone 3** (rewrite / revise).

## Planned stack

- Python 3.11+
- FastAPI + Pydantic v2
- Provider abstractions in `app/providers/{speech,editing}/`
- **OpenAI-compatible client** (`openai>=1.x`) as the default concrete
  adapter — configured via `OPENAI_BASE_URL` and `OPENAI_API_KEY`. Works
  against Groq, OpenAI, DeepSeek, Together, vLLM, LM Studio, Ollama, etc.
- pytest for tests
- Docker for deployment
- No database; no persistence of user content

## Planned layout

```
backend/
├── app/
│   ├── main.py
│   ├── api/
│   │   ├── health.py
│   │   ├── transcribe.py
│   │   └── rewrite.py
│   ├── domain/
│   ├── schemas/
│   ├── providers/
│   │   ├── speech/
│   │   └── editing/
│   ├── prompts/                     # message_cleanup_v1.txt, revise_v1.txt
│   ├── security/
│   ├── observability/
│   └── settings.py
├── tests/
├── Dockerfile
├── pyproject.toml
└── .env.example
```

## Endpoints (from `docs/backend-api.md`)

- `GET /health` — liveness probe (no auth).
- `POST /v1/transcriptions` — audio → transcript.
- `POST /v1/messages/rewrite` — transcript → structured draft.
- `POST /v1/messages/revise` — apply a targeted correction.

## Non-negotiable rules

1. **No user content in any log.** Ever. Bodies redacted at the middleware
   layer, not sprinkled through call sites.
2. **No persistence of audio, transcripts, or drafts.** In-memory only.
3. **Provider keys live only here**, never in the Android APK.
4. **Strict request-size and duration limits.** See `docs/backend-api.md`.
5. **Structured output validation** for every model response.

See [`../docs/privacy-model.md`](../docs/privacy-model.md) and
[`../docs/security-threat-model.md`](../docs/security-threat-model.md).

## Local run (once M2 lands)

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env
# fill in GROQ_API_KEY
uvicorn app.main:app --reload --port 8000
```

## Tests (once M2 lands)

```bash
pytest -q
```
