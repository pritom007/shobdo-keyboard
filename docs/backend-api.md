# Backend API

The transcription endpoint is implemented. Rewrite and revision endpoints in
this document are forward-looking contracts and are not available yet.

## Base URL

TBD per deployment. During development: `http://localhost:8000`.

## Authentication

The prototype backend supports `X-Shohojakkhor-Secret` when configured. This must not
be embedded in a publicly distributed APK because application secrets are
extractable. Production distribution requires short-lived per-install
credentials or platform attestation at the API gateway.

Transcription requests carry `X-Device-Id`, an opaque install-local value used
only as one input to coarse abuse prevention. It is not trusted as identity.

## Common error envelope

```json
{
  "error": {
    "code": "PROVIDER_TIMEOUT"
  }
}
```

Implemented transcription codes are `NO_AUDIO`, `BAD_AUDIO`, `TOO_LARGE`,
`TOO_LONG`, `RATE_LIMIT`, `UNAUTHORIZED`, `PROVIDER_NOT_CONFIGURED`,
`PROVIDER_TIMEOUT`, `PROVIDER_RATE_LIMIT`, and `PROVIDER_ERROR`.

The client maps stable codes to local Bengali messages. The server does not
return provider exception text to the user.

## Endpoints

### `GET /health`

Liveness probe. Returns `{"status":"ok"}`. No auth required.

### `POST /v1/transcriptions`

Multipart:

- `audio` — 16-bit PCM WAV, one or two channels, ≤ 60 s, ≤ 2 MB. The Android
  client records 16 kHz mono.
- `language` — optional language hint; defaults to `bn`. An empty value lets a
  compatible provider auto-detect.

Response:

```json
{
  "text": "আমি শুক্রবার বিকেল চারটায় আসব",
  "language": "bn",
  "duration_ms": 4500
}
```

The response deliberately omits provider identity and internal exception text.

### `POST /v1/messages/rewrite`  *(M3)*

Body:

```json
{
  "text": "উম... আমি ইয়ে শুক্রবার না না শনিবার বিকাল চারটায় আসব",
  "rewrite_mode": "ORGANIZED",
  "language": "bn",
  "mixed_language_preference": "NATURAL_MIX",
  "personal_hints": ["রহিম", "রংপুর"]
}
```

- `rewrite_mode`: `"VERBATIM" | "ORGANIZED" | "POLISHED"`.
- `mixed_language_preference`: `"MORE_BENGALI" | "NATURAL_MIX" | "KEEP_ENGLISH"`.
- `personal_hints`: ≤ 20 short strings, relevance-filtered.

Response:

```json
{
  "edited_text": "আমি শনিবার বিকেল ৪টায় আসব।",
  "meaning_preserved": true,
  "needs_clarification": false,
  "clarification_question_bn": null,
  "critical_entities": [
    {"type": "DATE", "raw_value": "শনিবার", "final_value": "শনিবার", "confidence": 0.97},
    {"type": "TIME", "raw_value": "বিকাল চারটা", "final_value": "বিকেল ৪টা", "confidence": 0.95}
  ],
  "uncertain_segments": [],
  "change_summary": [
    "উম, ইয়ে বাদ দেওয়া হয়েছে",
    "শুক্রবার → শনিবার (self-correction)"
  ]
}
```

If the model would need to change an entity to make the message coherent but
cannot resolve it confidently, it must return
`needs_clarification: true` and a single `clarification_question_bn`.

### `POST /v1/messages/revise`  *(M3)*

Body:

```json
{
  "current_draft": "আমি শনিবার বিকেল ৪টায় আসব।",
  "correction_instruction": "শেষে সালাম যোগ করো",
  "rewrite_mode": "ORGANIZED",
  "language": "bn",
  "mixed_language_preference": "NATURAL_MIX",
  "critical_entities": [ /* passed through from the previous response */ ]
}
```

Response: same shape as `/v1/messages/rewrite`. The AI must apply **only**
the requested change; every unrelated sentence must remain byte-identical
unless a strict grammatical adjustment is required.

## Rate limits

The prototype defaults to 20 requests per minute per hashed source/install
key. The in-memory limiter is bounded but process-local; production scale needs
a distributed limiter at the gateway.

## Size and duration limits

| Field | Max |
|---|---|
| Audio duration | 60 s |
| Audio file | 2 MB |
| `text` in rewrite/revise | 4000 chars |
| `personal_hints` | 20 entries × 40 chars each |
| Any request body | 3 MB |

## Content that is NOT logged

Request bodies, response bodies, transcripts, drafts, audio metadata that
could re-identify content. Only `X-Request-Id`, latency, HTTP status,
`error.code`, provider name, and model id are logged.

## Versioning

`/v1/*` is the current major. Breaking changes go to `/v2/*` with a
transition window. The `MessageEditResponse` schema is versioned in the
prompt file name (`message_cleanup_v1.txt`, `message_cleanup_v2.txt`, ...).
