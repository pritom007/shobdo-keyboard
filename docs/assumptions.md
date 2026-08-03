# Assumptions

Documented per master brief §1.2. Items here are reasonable defaults chosen
without explicit user direction. Anything that materially affects privacy,
cost, licensing, or Play Store compliance is instead escalated in-thread.

## Product

- **Product name**: Shohojakkhor Keyboard / সহজাক্ষর কিবোর্ড.
- **Package / applicationId**: `com.shohojakkhor.keyboard`. Chosen because the user
  had no preference and no external domain to claim. Trivially changeable via
  `libs.versions.toml` before first Play Store upload.
- **Primary user language**: Bengali (Bangladesh). English is a supported
  secondary language.
- **Target audience device profile**: budget-to-mid-range Android phones
  common among elderly Bangladeshi users (Xiaomi Redmi, Samsung A-series,
  Realme). This drives the minSdk and Compose-in-IME decisions.

## License

- **Not yet chosen.** `LICENSE-NOTE.md` explicitly says "All rights reserved"
  as a safe default. To be revisited before public distribution.

## Android build

| Setting | Value | Rationale |
|---|---|---|
| `minSdk` | 24 (Android 7.0) | Broad reach on older devices; still covers ~99 % of active devices. |
| `targetSdk` | 34 | Play Store policy floor. |
| `compileSdk` | 34 | Match `targetSdk`. |
| JVM target | 17 | Required by AGP 8.x. |
| Kotlin | 1.9.24 | Stable, matches Compose Compiler 1.5.x. |
| AGP | 8.5.x | Current stable. |
| Gradle | 8.7 | Current stable at time of writing. |
| Version catalog | `gradle/libs.versions.toml` | Single source of truth. |
| DSL | Kotlin | Per master brief §11. |
| Compose in IME | **No** in M1 | See `architecture.md`. Compose is only used in `app`. |
| DI framework | **None** in M1 | Hilt introduced at M2. Premature DI hurts more than it helps for one activity + one service. |
| Hilt / Room / DataStore | Deferred | Introduced when a real second consumer appears. |

## Voice / audio (planned for M2)

- Sample rate: **16 kHz mono PCM**. Chosen for Whisper-family compatibility and
  small upload size.
- Encoding on the wire: **WAV** in M2 (simple); switch to `flac` or `opus` when
  bandwidth becomes a concern.
- Max single utterance: **60 seconds**. Enforced client- *and* server-side.
- Local VAD: simple RMS-based silence detection on the recording thread. No
  ML model in the IME process — latency and battery cost too high.

## Backend (planned for M2)

- Language: **Python 3.11+**.
- Framework: **FastAPI**.
- **Provider client: OpenAI-compatible.** The backend uses the official
  `openai` Python SDK configured with `base_url` + `api_key` + `model`
  from environment variables. This keeps us portable across Groq, OpenAI,
  DeepSeek, Together, vLLM, LM Studio, Ollama, and any other OpenAI-compat
  service without code changes.
  - Default `OPENAI_BASE_URL=https://api.groq.com/openai/v1`
  - Default transcription model: `whisper-large-v3`
  - Default chat model: `llama-3.1-70b-versatile`
  - Provider abstraction (`SpeechToTextProvider`, `MessageEditor`) still
    exists so we can plug in a non-OpenAI-compat provider later, but the
    OpenAI-compat implementation is the only concrete adapter shipped in M2.
- Deployment: **Docker** container; single stateless service.
- Data retention: **zero** — see `privacy-model.md`.
- Auth: shared secret header on M2, upgrade to per-install token at M4.

## Personal dictionary (planned for M6)

- Local Room database, `androidx.security.crypto` for at-rest encryption where
  the entry is user-marked sensitive.
- Never uploaded automatically.
- When included in an AI request, only a small relevant subset is sent (top-N
  by search relevance).

## Bengali linguistic defaults

- Default writing mode: **ORGANIZED** (একটু গুছিয়ে).
- Default mixed-language mode: **স্বাভাবিক মিশ্র ভাষা** (natural mix).
- Never automatically convert তুমি ↔ আপনি.
- Preserve religious phrases and names verbatim.
- Preserve numbers, dates, amounts, and addresses verbatim unless the user's
  self-correction is explicit and high-confidence.

## Text-to-speech (planned for M3)

- Bengali TTS availability is a **soft warning, not a hard blocker.** If the
  system does not have Bengali TTS data installed, the keyboard remains fully
  usable and a dismissible Bengali banner offers a one-tap shortcut to system
  TTS settings. Users who never enable read-back are never nagged twice.

## Non-decisions (still open)

The following need product / owner input before the relevant milestone starts:

- Play Store listing name and screenshot copy.
- Whether to ship a paid tier at all.
- Whether the backend will be self-hosted or on a managed platform.
- Analytics / metrics vendor (currently deferred entirely; §24).
- Handwriting recognition provider (ML Kit vs. custom vs. deferred forever).
