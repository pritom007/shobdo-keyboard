# Privacy Model

Everything the user types or speaks is sensitive by default. This document
describes what the app and backend may and may not do with that data.

## What we treat as sensitive content

- Every keystroke
- Every audio sample
- Every transcript (raw or edited)
- Every AI-generated draft
- Every revision
- Every personal-dictionary entry
- Every handwriting stroke and recognition candidate
- The surrounding text of the field the user is editing

## What is allowed in logs

Only anonymous, non-content signals:

- Anonymous per-request UUID
- Timestamps
- Latency
- Success / failure booleans
- Coarse error category (e.g. `TIMEOUT`, `PROVIDER_MALFORMED_RESPONSE`)
- Provider name / model identifier
- Audio *duration in seconds* (not the audio)
- Transcript / draft *character count* (not the content)

## What is forbidden in logs, crash reports, analytics, and error toasts

- Any typed character
- Any speech content, in any encoding
- Any AI response body
- Personal dictionary entries
- Contact names extracted from the field
- The `EditorInfo.packageName` of the host app in analytics
  (technically visible to us at runtime, but we do not export it)

## On-device data

| Data | Where | Retention |
|---|---|---|
| Raw transcript | RAM only | Discarded on approval / cancel / new recording / IME hide / 5 min timeout |
| Draft message | RAM only | Same as above |
| Edit history for current draft | RAM only | Cleared on approval / cancel |
| Personal dictionary | Room DB (encrypted for sensitive entries) | Until user deletes it |
| User preferences | DataStore | Until user clears app data |
| Audio buffer | RAM only | Discarded immediately after upload / on cancel |
| Handwriting strokes and candidates | RAM only | Cleared after selection, clear, input change, or IME process death |

The IME never writes typed content or audio to disk.

## Google ML Kit handwriting

The optional Bengali handwriting mode uses Google ML Kit Digital Ink with the
`bn` model. Stroke coordinates, timing, and recognized text are processed on
the device and are never sent to the Shohojakkhor backend or logged. The
language model is downloaded from Google on first use and then works offline.

ML Kit may contact Google for model delivery, fixes, accelerator compatibility,
and API performance/utilization metrics. Google states that input data and
recognition output are not sent to its servers. This dependency must be named
in the public privacy disclosure before a production release.

## Backend data flow (planned M2+)

```
Client                              Backend
------                              -------
POST /v1/transcriptions
  body: audio (WAV, ≤ 60 s)
                                    → in-memory buffer
                                    → provider call (Groq Whisper)
                                    ← provider response
                                    → normalize to TranscriptResult
                                    ← 200 OK, JSON only
                                    → buffer freed immediately

POST /v1/messages/rewrite
  body: text
                                    → provider call (Groq chat)
                                    ← provider response
                                    → validate structured JSON
                                    ← 200 OK
                                    → no persistence
```

**No database on the backend.** No audio files on disk. No transcripts on disk.
No message content in access logs. Log line format explicitly redacts request
and response bodies.

## Provider (Groq) data settings

The backend is responsible for configuring the Groq client such that:

- Requests opt out of training data collection where the provider offers the
  option.
- Zero-day-retention modes are used where available.
- We monitor Groq policy changes and revisit before each release.

This is a **provider-policy dependency** and must be re-checked at every
milestone transition. If Groq changes retention terms in a way we cannot
accept, the provider abstraction (`speech-domain.SpeechToTextProvider` and
`ai-editor-domain.MessageEditor`) lets us swap it.

## Sensitive input fields

`InputPrivacyPolicy` classifies each edit target as `NORMAL`, `INCOGNITO`, or
`SENSITIVE`. In `SENSITIVE` mode:

- 🎤 voice button disabled
- ☁️ no network requests
- 📚 personal dictionary not consulted
- 🧠 no learning from user selections
- 📜 no transcript history
- 🎙️ no surrounding-text reading
- ✍️ handwriting mode unavailable
- 🪪 UI shows a clear indicator ("সুরক্ষিত মোড — কেবল সাধারণ কিবোর্ড")

Fields classified as SENSITIVE (M1):

- `InputType.TYPE_TEXT_VARIATION_PASSWORD`
- `InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`
- `InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD`
- `InputType.TYPE_NUMBER_VARIATION_PASSWORD`
- `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` present
- `EditorInfo.imeOptions & IME_MASK_ACTION` on OTP-hinted fields
- `EditorInfo.hintText` matching common OTP / PIN / CVV keywords

Fields classified as INCOGNITO (M1):

- `EditorInfo.imeOptions & IME_FLAG_NO_PERSONALIZED_LEARNING` (all other cases)

## Analytics

Deferred. No analytics SDK is added until:

1. A written privacy policy exists.
2. Consent UI exists.
3. Event schema is minimized and redacts content.
4. Opt-out is one tap.

For the initial family prototype we prefer **no analytics at all**.

## Backups

`android:allowBackup="false"` on the manifest to prevent auto-backup from
including any future personal-dictionary content until we design encrypted
backup properly.

## Deletion

Users can:

- Clear the personal dictionary from settings.
- Clear app data via Android Settings → Apps → Shohojakkhor → Storage → Clear data.
  This removes DataStore preferences, Room DB, and any temp state.

When account-based features arrive (not planned in first year), deletion must
also include a server-side wipe endpoint.

## Threat categories cross-referenced

See [`security-threat-model.md`](security-threat-model.md) for the mitigation
matrix.
