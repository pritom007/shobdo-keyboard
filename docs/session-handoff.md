# Session Handoff

This file contains project context only. Keep machine paths, account details,
device identifiers, API keys, email addresses, and other personal information
out of the repository.

## Current state

Shohojakkhor Keyboard is an elderly-friendly Bengali and English Android IME. The
current prototype includes:

- English QWERTY and Banglish-to-Bengali typing with candidates.
- Avro-style shifted Bengali input and Bengali symbol pages.
- Tap-to-toggle, in-memory voice capture.
- Online transcription through the FastAPI proxy and an OpenAI-compatible provider.
- On-device Whisper fallback through sherpa-onnx.
- Sensitive-field classification that hides voice and cloud features.

The LLM draft organization, conversational revision, read-back, undo, and
explicit review-before-insert flow are still planned.

## Repository layout

```text
android/app/              Setup application and model assets
android/keyboard-ime/     IME, layouts, privacy policy, and voice UI
android/transliteration/  Pure-Kotlin Banglish transliteration
android/voice-capture/    In-memory microphone capture and WAV encoding
android/speech/           Online transcription client
android/speech-ondevice/  Offline Whisper integration
backend/                  FastAPI transcription proxy
docs/                     Product, architecture, privacy, and security docs
```

## Local setup

Use repository-relative commands and configure machine-specific paths only in
ignored local files or environment variables.

```bash
./scripts/download-dependencies.sh

cd android
./gradlew :app:assembleDebug \
  :keyboard-ime:testDebugUnitTest \
  :transliteration:testDebugUnitTest \
  :voice-capture:testDebugUnitTest \
  :speech:testDebugUnitTest \
  :speech-ondevice:testDebugUnitTest \
  --console=plain

cd ../backend
python -m pytest -q
```

The Android SDK location belongs in the ignored `android/local.properties`.
Backend credentials belong in the ignored `backend/.env`; never copy their
values into documentation, source, tests, logs, issues, or chat transcripts.

## Locked product decisions

- Bengali input prioritizes Banglish phonetics; a native layout may come later.
- Voice is tap-to-start and tap-to-stop, with visible cancellation.
- Online transcription is primary; Bengali on-device transcription is fallback.
- Typed text, recordings, and transcripts are not intentionally persisted.
- Sensitive fields never use voice or cloud processing.
- The keyboard inserts text only; it never presses Send in another application.
- LLM editing must preserve important entities and require user review.
- No analytics are added without a privacy policy, consent, and redaction review.
- The repository remains all-rights-reserved until a license is chosen.

## Next priorities

1. Verify Bengali, English, and offline voice flows on a supported physical device.
2. Add Play Integrity-backed client attestation before raising backend quotas.
3. Add the reviewable LLM draft and conversational correction workflow.
4. Persist transliteration preferences without storing sensitive-field input.
5. Reduce APK size by delivering the offline model separately.
6. Test accessibility with elderly Bengali-speaking users.
