# Backend API

The backend is not yet implemented (skeleton only at M0). This document
freezes the initial contracts so the Android side can code against stable
DTOs at M2.

## Base URL

TBD per deployment. During development: `http://localhost:8000`.

## Authentication

M2: shared secret header `X-Shobdo-Auth: <token>`. Rotated out-of-band.
M4+: per-install token issued once during onboarding, stored in the Android
Keystore, sent as a bearer.

Every request must also carry:

- `X-Request-Id: <uuid v4>` — client-generated, never contains PII.
- `X-Client-Version: <appVersionName>+<versionCode>`.

## Common error envelope

```json
{
  "error": {
    "code": "PROVIDER_TIMEOUT",
    "message_bn": "কথাটি বুঝতে একটু সমস্যা হয়েছে। আবার চেষ্টা করুন।",
    "retryable": true
  }
}
```

Error codes (initial):

- `INVALID_REQUEST`, `UNAUTHORIZED`, `RATE_LIMITED`
- `AUDIO_TOO_LARGE`, `AUDIO_TOO_LONG`, `AUDIO_UNSUPPORTED_FORMAT`
- `PROVIDER_TIMEOUT`, `PROVIDER_UNAVAILABLE`, `PROVIDER_MALFORMED_RESPONSE`
- `MEANING_UNCERTAIN` (needs clarification), `SAFETY_BLOCKED`

`message_bn` is always safe to display directly to the user.

## Endpoints

### `GET /health`

Liveness probe. Returns `{"status":"ok"}`. No auth required.

### `POST /v1/transcriptions`  *(M2)*

Multipart:

- `audio` — WAV, mono, 16 kHz PCM, ≤ 60 s, ≤ 2 MB.
- `language` — `bn` or `en`.
- `hints` — optional JSON array of up to 20 short strings (names,
  place names) to bias recognition. **Not** the full personal dictionary.

Response:

```json
{
  "text": "আমি শুক্রবার বিকেল চারটায় আসব",
  "language": "bn",
  "confidence": 0.87,
  "segments": [
    {"start_ms": 0,    "end_ms": 1600, "text": "আমি"},
    {"start_ms": 1600, "end_ms": 3400, "text": "শুক্রবার বিকেল"}
  ],
  "uncertain_segments": [],
  "provider": "groq",
  "model": "whisper-large-v3"
}
```

Errors: `AUDIO_TOO_LARGE`, `AUDIO_TOO_LONG`, `AUDIO_UNSUPPORTED_FORMAT`,
`PROVIDER_TIMEOUT`, `PROVIDER_UNAVAILABLE`, `PROVIDER_MALFORMED_RESPONSE`.

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

Per-install token: 60 requests / minute across all endpoints. `429` returns
the standard error envelope with `retryable: true` and a
`Retry-After` header.

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
