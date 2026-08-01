# Architecture

This document describes the target architecture. Not all modules exist yet.
See [`roadmap.md`](roadmap.md) for what is actually built.

## Guiding principles

1. **User safety > meaning preservation > privacy > elderly usability > reliability
   > recoverability > offline basics > latency > engineering simplicity > polish
   > advanced features.** (§33)
2. **Never treat the AI feature as a bolt-on.** The voice → organize → approve
   loop is the *core* interaction; the keyboard surface is designed around it.
3. **The client owns patient listening.** Silence and pause thresholds live on
   the device, not in the cloud provider. Cloud STT is called only after the
   user (or the local VAD) says the utterance is complete.
4. **Provider-independent domain layer.** No file in `keyboard-ime`,
   `voice-capture`, or `speech-domain` imports Groq / Whisper / OpenAI types.
5. **No content ever hits a log.** Audio, transcripts, drafts, dictionary
   entries — none of these appear in any structured log or crash report.
6. **Do not create modules speculatively.** The list below is the *target*.
   New modules are extracted only when a real second consumer appears.

## Module map (target)

```
android/
├── app/                    ← thin: onboarding, settings, permissions UI
├── keyboard-ime/           ← InputMethodService, keyboard views, key handling
├── keyboard-ui/            ← reusable large-key widgets (extracted at M6)
├── voice-capture/          ← AudioRecord wrapper, VAD, lifecycle (M2)
├── speech-domain/          ← SpeechToTextProvider interface, DTOs (M2)
├── ai-editor-domain/       ← MessageEditor interface, RewriteMode, DraftMessage (M3)
├── transliteration/        ← Banglish → Bengali engine (M5)
├── personal-dictionary/    ← Room-backed local dictionary (M6)
├── text-to-speech/         ← TextReader wrapper around Android TTS (M3)
├── privacy-security/       ← InputPrivacyPolicy, Keystore, redaction (M1 has stub)
├── core-model/             ← plain Kotlin domain models (M2)
├── core-network/           ← Retrofit / Ktor client, auth (M2)
└── testing/                ← test fixtures, fakes (M2)
```

Currently existing (M1): `app`, `keyboard-ime` (which contains an inline
`InputPrivacyPolicy` — will be extracted to `privacy-security` at M2).

## The core voice state machine

Lives in `ai-editor-domain` (once M3 arrives). All transitions must be
deterministic and testable.

```kotlin
sealed interface VoiceCompositionState {
    data object Idle : VoiceCompositionState
    data object Preparing : VoiceCompositionState
    data class Listening(val elapsedMs: Long, val silenceMs: Long) : VoiceCompositionState
    data object ProcessingTranscription : VoiceCompositionState
    data class ReviewingTranscript(val rawTranscript: String) : VoiceCompositionState
    data object ProcessingRewrite : VoiceCompositionState
    data class ReviewingDraft(
        val rawTranscript: String,
        val draft: DraftMessage,
        val revisionNumber: Int,
    ) : VoiceCompositionState
    data class AwaitingCorrection(val currentDraft: DraftMessage) : VoiceCompositionState
    data class AskingClarification(
        val questionBn: String,
        val currentDraft: DraftMessage,
    ) : VoiceCompositionState
    data class Error(
        val errorType: VoiceErrorType,
        val recoverableState: VoiceCompositionState?,
    ) : VoiceCompositionState
}
```

Pause thresholds (client-side, tunable):

| Silence (ms) | Behaviour                                            |
|--------------|------------------------------------------------------|
| < 2000       | Continue listening silently                          |
| 2000 – 5000  | Continue; show "waiting" indicator                   |
| 5000 – 8000  | Prompt "আর কিছু বলবেন?" (visual only in M2)          |
| > 8000       | Offer Finish / Continue — never auto-submit          |

Voice finishing phrases ("শেষ", "হয়ে গেছে", "এই পর্যন্ত", "লেখা শেষ") are
matched against the *tail* of the transcript with a confirmation prompt when
ambiguous. Never trust a voice command silently.

## Core interfaces (target)

```kotlin
interface SpeechToTextProvider {
    suspend fun transcribe(
        audio: AudioPayload,
        language: SupportedLanguage,
        hints: List<String> = emptyList(),
    ): TranscriptResult
}

interface MessageEditor {
    suspend fun organize(
        rawTranscript: String,
        mode: RewriteMode,
        preferences: WritingPreferences,
        personalHints: List<String> = emptyList(),
    ): MessageEditResult
}

interface TextReader {
    suspend fun speak(text: String, language: SupportedLanguage): SpeakResult
    fun stop()
}

interface TransliterationEngine {
    fun transliterate(input: String): List<TransliterationCandidate>
}

interface InputPrivacyPolicy {
    fun classify(editorInfo: EditorInfo): InputPrivacyMode
}

interface PersonalDictionaryRepository {
    suspend fun searchCandidates(query: String): List<DictionaryEntry>
    suspend fun add(entry: DictionaryEntry)
    suspend fun update(entry: DictionaryEntry)
    suspend fun delete(entryId: String)
    suspend fun clearAll()
}

interface HandwritingRecognizer {   // M7 only
    suspend fun recognize(strokes: List<InkStroke>): List<HandwritingCandidate>
}
```

`InputPrivacyMode` values: `NORMAL`, `INCOGNITO`, `SENSITIVE`.

## Data flow (once M4 lands)

```
User taps 🎤
  → voice-capture starts AudioRecord + local VAD
  → user speaks with pauses (client-side patient listening)
  → user (or long-silence prompt) says "Finish"
  → speech-domain.SpeechToTextProvider.transcribe()  → RAW transcript (in-memory)
  → ai-editor-domain.MessageEditor.organize()        → DraftMessage
  → UI: review screen (large text, entities highlighted)
  → user says "ঠিক আছে"   → InputConnection.commitText → done
     user says "বদলাতে চাই" → AwaitingCorrection → revise() → new draft
     user says "পড়ে শোনান" → TextReader.speak()
     user says "বাতিল করুন" → Idle; raw transcript discarded
```

At every step, `InputPrivacyPolicy` is re-checked. If the target field became
sensitive mid-flow, the pipeline halts and the raw transcript is discarded.

## Threading model

- IME lifecycle callbacks run on the main thread.
- Audio capture uses a background `Dispatchers.IO` coroutine tied to the IME's
  lifecycle scope.
- Network calls use `Dispatchers.IO`.
- All UI state exposed as `StateFlow` from a scoped `ViewModel`-like holder
  (IME does not have Android `ViewModel`; use a plain scoped state holder).
- Cancellation flows from the IME's `onFinishInput()` and `onDestroy()`.

## Error handling

- Every error mapped to a user-facing Bengali message via a `VoiceErrorType`
  enum (see §21 of the master brief).
- Recoverable state is preserved. If rewrite fails but the raw transcript
  exists, the user can still commit the raw transcript.
- Exceptions are never toasted or logged with content.

## Compose vs. classic Views inside the IME

Compose is used freely in `app` (settings, onboarding). Inside the IME window,
M1 uses **classic `View` / `ViewGroup`** because:

- The IME window is not a normal `Activity` — Compose's `ViewTreeLifecycleOwner`
  needs manual wiring for `InputMethodService`.
- Predictable measure/layout matters for popup and cursor tracking.
- Keyboard input latency budget is small.

Compose inside the IME is reconsidered at M4 when the voice review UI needs
richer components.

## What we deliberately do *not* do

Aligned with §32 of the master brief:

- No cloud account, social login, subscriptions.
- No analytics until consent, policy, redaction, and opt-out exist (§24).
- No reading of surrounding host-app conversation content (§26).
- No treating host-app text as trusted context — always sanitize.
- No auto-Send in host apps. Ever.
- No sticker / theme marketplace.
- No iOS / desktop target.
