# Session Handoff — Read This First (Next Chat)

**Last updated:** 2026-08-01, session 3 (server leg built; live test pending).
**Current state:** M0, M1, M1.1, M2A, M2B, M3A, M3B complete. **Session 3 delivered the hybrid voice architecture and it is BUILT and INSTALLED.** (1) On-device model upgraded tiny → **Whisper base** (tiny collapsed Bengali to English — too weak). (2) **Server leg wired**: the IME now tries **Groq Whisper large-v3** via the `backend/` FastAPI proxy first (auto-detect Bengali/English), falling back to on-device base offline with a friendly Bengali "ইন্টারনেট নেই, অফলাইনে লিখছি…" message. **150 Android unit tests + 15 backend pytest tests pass.** The live voice-quality test on the Samsung is the one remaining gate — pending the user.

---

## TL;DR

Shobdo Keyboard (শব্দ কিবোর্ড) is an elderly-friendly Bengali + English Android IME. M2 delivered Banglish transliteration. Session 2 delivered on-device STT (M3B) with Whisper tiny. **Session 3:** tiny's Bengali quality was too poor (it ignored the `bn` prompt and fell back to English — confirmed by inspecting the ONNX graph; tiny is genuinely multilingual, just too small), so the on-device model was upgraded to **Whisper base**, and the user chose a **hybrid** architecture: server Groq Whisper large-v3 (online, high quality, auto-detect) + on-device Whisper base (offline fallback). Both legs are now wired and on the phone. The `backend/` and `android/speech` modules are no longer dead code — they are the online leg.

## The hybrid voice architecture (session 3)

**`HybridSpeechRecognizer`** (`keyboard-ime` voice package) is the orchestrator:
- **Primary (online):** `RemoteSpeechRepository` (OkHttp) → `backend/` FastAPI → Groq Whisper large-v3. Sends `language=""` for **auto-detect** (speak Bengali → Bengali script, English → English). Best quality (~10–15% WER). Needs internet.
- **Fallback (offline):** `OnDeviceSpeechRecognizer` (sherpa-onnx Whisper base, `language="bn"`). Used on ANY remote error (NoNetwork, Timeout, Provider, RateLimit, …) or blank remote text. Less accurate, Bengali-only, but always available offline.
- **`onFallback` callback:** fires before the on-device pass so the UI swaps "লিখছি…" → "ইন্টারনেট নেই, অফলাইনে লিখছি…" during the slower fallback.
- **`HybridResult`:** `Online(text)` / `OfflineFallback(text)` / `Failed`. 19 unit tests cover every branch.

The decision is deliberately "fall back on any remote failure" — we never punish the user for a server hiccup; the on-device model is the safety net. The product rule is "friendly fallback, never an error" for the offline case.

## Repository facts

- **Working directory:** `/Users/pritom_binance/Desktop/office/android-keyboard`
- **Git:** not initialised (user's choice; they will `git init` when ready).
- **Package / applicationId:** `com.shobdo.keyboard`
- **License:** intentionally not chosen. `LICENSE-NOTE.md` says "All Rights Reserved". Do not silently pick one.
- **APK size:** debug build is ~263MB per ABI (arm64-v8a) — model + native libs (ONNX Runtime + JNI) are the bulk. ABI splits enabled for release. Future: download the model on first use to shrink the initial install.

## Toolchain (user's machine)

- JDK: **Android Studio JBR 21** at `/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
- Android SDK: `~/Library/Android/sdk` with `platform-36`.
- Gradle: 8.9. AGP: 8.7.3. Kotlin 1.9.24. Compose Compiler 1.5.14.
- `compileSdk` / `targetSdk` = 36. `minSdk` = 24.
- Python: **system Python is 3.10.6** (no 3.11). Backend `pyproject.toml` pinned `>=3.10`. Backend has its own `.venv` at `backend/.venv` (deps installed: fastapi, openai, uvicorn).
- Physical device: **Samsung SM-S731B**, Android 16 (API 36), serial `R5CYA2DZC1H`. Always test on this, never on the local emulators (Android 17 preview, reject debug-signed apps).

## Standard build / install / run commands

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
cd /Users/pritom_binance/Desktop/office/android-keyboard/android

# Build + test + install
./gradlew :app:assembleDebug \
          :keyboard-ime:testDebugUnitTest \
          :transliteration:testDebugUnitTest \
          :voice-capture:testDebugUnitTest \
          :speech:testDebugUnitTest \
          :speech-ondevice:testDebugUnitTest \
          --console=plain
adb -s R5CYA2DZC1H install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb -s R5CYA2DZC1H shell ime set com.shobdo.keyboard/.ime.ShobdoInputMethodService
```

**Backend (the online leg) — run locally for dev:**
```bash
cd /Users/pritom_binance/Desktop/office/android-keyboard/backend
# .env must have OPENAI_API_KEY=<groq key>. Already set up in session 3.
.venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --log-level info
# In another terminal, tunnel the phone's localhost:8000 to the Mac's:
adb -s R5CYA2DZC1H reverse tcp:8000 tcp:8000
# Health check: curl http://127.0.0.1:8000/health  → {"status":"ok",...}
# Kill: lsof -ti tcp:8000 | xargs kill
```

## Current module layout

```
android/
├── settings.gradle.kts       (:app, :keyboard-ime, :transliteration, :voice-capture, :speech, :speech-ondevice)
├── local-repo/               Local Maven repo hosting the sherpa-onnx AAR
├── app/                      Setup activity + VoiceActivity (standalone) + assets/sherpa-whisper-base/ (model)
├── keyboard-ime/             IME + voice wiring (VoiceController, HybridSpeechRecognizer, VoicePanelView, handleVoice)
├── transliteration/          Pure-Kotlin Banglish → Bengali engine
├── voice-capture/            Mic recording → in-mem WAV (tap-to-toggle, 30s cap)
├── speech/                   OkHttp client → backend (the ONLINE leg — WIRED in session 3)
└── speech-ondevice/          sherpa-onnx Whisper base on-device (the OFFLINE fallback leg)
backend/                      FastAPI proxy → Groq Whisper large-v3 (the ONLINE leg — WIRED in session 3)
docs/                         Product / architecture / privacy docs
```

## Verified state (150 Android tests + 15 backend tests green)

| Module | Suite | Tests |
|---|---|---|
| `keyboard-ime` | `KeyboardModeTransitionsTest` | 15 |
| `keyboard-ime` | `EnglishQwertyTest` | 8 |
| `keyboard-ime` | `BengaliBanglishTest` (NEW) | 7 |
| `keyboard-ime` | `SymbolsLayoutTest` (NEW) | 9 |
| `keyboard-ime` | `InputPrivacyPolicyTest` | 12 |
| `keyboard-ime` | `VoiceControllerTest` | 15 |
| `keyboard-ime` | `HybridSpeechRecognizerTest` | 19 |
| `transliteration` | `TokenizerTest` + `AssemblerTest` + `AvroLikeEngineTest` + `SelectionMemoryTest` | 45 |
| `voice-capture` | `WavWriterTest` + `RecordingSessionTest` + `AudioRecorderTest` | 20 |
| `speech` | `RemoteSpeechRepositoryTest` | 16 |
| `speech-ondevice` | `PcmToFloatConverterTest` | 9 |
| **Android total** | | **175** |
| `backend` (pytest) | `test_health` + `test_transcribe` | 15 |

## What works on the phone right now

Everything from M2 (English QWERTY, Bengali Banglish + candidates + selection learning, sensitive-field protection, nav-bar inset, IME picker) **plus the hybrid voice path AND the Bangla keyboard fixes**:

1. Mic key (🎙️) in the keyboard bottom row, between globe and space, in all layouts.
2. Tap mic → keyboard becomes a listening panel (pulsing red circle, "শুনছি…", live timer, থামুন / বাতিল করুন).
3. Tap থামুন → panel shows "লিখছি…" → **hybrid recognizer runs**:
   - **Online (default):** POST to `backend/` → Groq Whisper large-v3 → auto-detect Bengali/English → commit transcript.
   - **Offline fallback:** if the server is unreachable, panel shows "ইন্টারনেট নেই, অফলাইনে লিখছি…" → on-device Whisper base runs → commit (degraded) Bengali transcript.
4. Tap বাতিল করুন → discards, returns to normal keyboard.
5. Mic key hidden in sensitive fields (password/PIN/OTP).
6. Mic permission prompt if not granted.
7. The standalone `VoiceActivity` (from M3A) still uses the old server-only path — it's now secondary/redundant.
8. **Avro-style shift in Bangla mode (session 3, NEW):** tap ⇧ in Bangla → keycaps show uppercase Latin (Q W E R T…) → typing `T` produces ট, `D`→ড, `N`→ণ, `R`→ড়, `Sh`→ষ, plus long vowels `A`→আ, `I`→ঈ, `U`→ঊ, `E`→এ, `O`→ও. One-shot (auto-releases to lower after one char, matching Avro); a second tap latches caps for multiple retroflex in a row.
9. **Bengali symbols page (session 3, NEW):** tap `?123` in Bangla → Bengali symbols page with digits ০-৯ and punctuation । ॥ ৎ + common. A second page (`#+=`) for more symbols. The back-to-letters key reads "অ"; the language toggle reads "En". (English symbols unchanged.)
10. **Cleartext localhost allowlist (session 3):** `network_security_config.xml` permits `http://127.0.0.1:8000` for dev via `adb reverse`; production HTTPS backend is never weakened.

**Backend must be running + `adb reverse` set up for the online voice leg to work.** Without it, the IME falls back to on-device base (which is the point — offline always works). `adb reverse` can drop on USB reconnects; re-apply with `adb -s R5CYA2DZC1H reverse tcp:8000 tcp:8000`.

## NOT yet verified live (do this first in the next session)

- **The hybrid voice happy path with the real backend + Groq large-v3:**
  1. Bengali: speak "আজকের আবহাওয়া খুব ভালো" → expect **Bengali script** (not English translation).
  2. English: speak "the weather is nice today" → expect **English text** (auto-detect).
  3. Offline: kill the backend → speak Bengali → expect "ইন্টারনেট নেই, অফলাইনে লিখছি…" then degraded Bengali.
- If Bengali still comes back English: pull `adb logcat -d | grep -iE 'shobdo|sherpa|onnx|whisper'` AND `tail -50 /tmp/shobdo-backend.log` (or wherever uvicorn is logging) to see what large-v3 returned.

## Locked-in product decisions (do NOT re-ask)

| Area | Decision |
|---|---|
| Bengali input priority | Banglish phonetic first, native layout later |
| Candidate UX | 5 candidates, tap to commit; space commits top + space |
| Selection learning | Yes (in-memory now; DataStore is M2C) |
| **Voice gesture** | **Tap-to-toggle** (tap to start, tap to stop), 30s auto-cap, always-visible discard |
| **STT engine (hybrid)** | **On-device Whisper base** (offline fallback) **+** server **Groq Whisper large-v3** (online, auto-detect). Both legs BUILT (session 3). |
| **Voice language** | **Auto-detect** (`language=""`) on the server path — speak Bengali → Bengali, English → English. On-device fallback is pinned to `bn` (Bengali-only, degraded). |
| **Backend key model** | Model C (user's key on backend). Key lives in `backend/.env` (`OPENAI_API_KEY`), never in the APK. |
| **Backend scope** | v1 = transcription only. Contract: if it changes, version it (`/v2`) and keep `/v1` alive so old APKs keep working — no forced Play Store update. |
| **Backend provider** | OpenAI-compatible client (`openai` SDK, configurable `base_url`). Groq at `https://api.groq.com/openai/v1` for initial deploy. |
| **Offline UX** | Friendly Bengali message ("ইন্টারনেট নেই, অফলাইনে লিখছি…") + on-device fallback — NOT an error. BUILT. |
| **LLM cleanup step** | Planned (Q2.2): take raw Whisper transcript → short LLM prompt → guess what user meant → commit cleaned text. Not yet built. |
| No audio/keys in logs or APK | Audio never persisted; key only in backend env; nothing logged |
| Bengali TTS availability | Soft warning, keyboard remains usable |
| License | Placeholder only, do not silently choose |
| Git init | User will do it themselves |
| Analytics | None until consent + policy + redaction exist |
| Auto-Send in host app | Never — keyboard only inserts, user always Sends |

## Open issues / next steps

1. **Verify the hybrid voice happy path** (needs the user's voice + the backend running). The one remaining gate for session 3. See "NOT yet verified live" above.
2. **Deploy the backend** somewhere reachable (not just localhost + adb reverse) for real-world use. Localhost+reverse is dev-only. Options: a small VPS / Cloud Run / Fly.io. The `OPENAI_API_KEY` + `OPENAI_BASE_URL` move with the env. No code change.
3. **APK size**: 263MB debug per ABI. Future: download the model on first use (instead of bundling in assets) to shrink the initial install.
4. **LLM cleanup step** (Q2.2): take raw Whisper transcript → short LLM prompt (Groq llama-3.1 via the backend) → cleaned-up Bengali → commit that instead of raw STT.
5. **M2C**: Persist SelectionMemory to DataStore; grow dictionary; enable shift in Banglish; fix `ng+vowel`; composition state-machine tests; settings UI.
6. **M2.5**: Native Bengali keycap layout as an alternate to Banglish.
7. The standalone `VoiceActivity` still uses the old server-only path — rewire it to the hybrid path, or remove it (it's now redundant with the in-keyboard voice).

## Critical files to grep before making changes

| Concern | Where |
|---|---|
| **Hybrid orchestrator (NEW)** | `android/keyboard-ime/src/main/java/com/shobdo/keyboard/ime/voice/HybridSpeechRecognizer.kt` |
| Voice state machine (testable) | `android/keyboard-ime/src/main/java/com/shobdo/keyboard/ime/voice/VoiceController.kt` |
| Voice panel UI | `android/keyboard-ime/src/main/java/com/shobdo/keyboard/ime/view/VoicePanelView.kt` |
| Voice wiring in the IME | `android/keyboard-ime/src/main/java/com/shobdo/keyboard/ime/ShobdoInputMethodService.kt` (search `handleVoice`, `ensureVoiceRecognizer`, `RecorderListenerImpl`, `HybridResult`) |
| Bengali voice strings | `android/keyboard-ime/src/main/java/com/shobdo/keyboard/ime/voice/VoiceStrings.kt` |
| Mic key in layouts | `android/keyboard-ime/src/main/java/com/shobdo/keyboard/ime/layout/EnglishQwerty.kt`, `SymbolsLayout.kt` (search `MIC_LABEL`, `KeyAction.Voice`) |
| On-device recognizer (offline leg) | `android/speech-ondevice/src/main/java/com/shobdo/keyboard/speech/ondevice/OnDeviceSpeechRecognizer.kt` |
| Remote repository (online leg) | `android/speech/src/main/java/com/shobdo/keyboard/speech/RemoteSpeechRepository.kt` |
| PCM→float converter (testable) | `android/speech-ondevice/src/main/java/com/shobdo/keyboard/speech/ondevice/PcmToFloatConverter.kt` |
| Whisper model files | `android/app/src/main/assets/sherpa-whisper-base/` (base-encoder.int8.onnx 29MB, base-decoder.int8.onnx 130MB, base-tokens.txt 798KB) |
| sherpa-onnx AAR | `android/local-repo/com/k2fsa/sherpa/onnx/sherpa-onnx/1.13.4/sherpa-onnx-1.13.4.aar` |
| Mic recording | `android/voice-capture/src/main/java/com/shobdo/keyboard/voice/capture/AudioRecorder.kt` |
| Backend (online leg) | `backend/app/api/transcribe.py`, `backend/app/providers/speech.py`, `backend/app/settings.py`, `backend/.env` |
| Transliteration | `android/transliteration/src/main/java/com/shobdo/keyboard/translit/` |
| Privacy / sensitive fields | `android/keyboard-ime/src/main/java/com/shobdo/keyboard/ime/privacy/InputPrivacyPolicy.kt` |
| Roadmap / decisions | `docs/roadmap.md`, `docs/assumptions.md` |
| Privacy model | `docs/privacy-model.md` |

## First actions for the next session

1. Read this file. Then `docs/roadmap.md` and `docs/assumptions.md`.
2. **Start the backend** (if not already running): `cd backend && .venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8000` + `adb -s R5CYA2DZC1H reverse tcp:8000 tcp:8000`.
3. **Do the hybrid voice test** (if not already done): open a text field, tap 🎙️, speak Bengali, tap থামুন, confirm Bengali script. Then English. Then kill the backend and confirm the offline fallback message + degraded transcript.
4. Re-run tests if needed:
   ```bash
   cd /Users/pritom_binance/Desktop/office/android-keyboard/android
   ./gradlew :keyboard-ime:testDebugUnitTest :transliteration:testDebugUnitTest \
             :voice-capture:testDebugUnitTest :speech:testDebugUnitTest \
             :speech-ondevice:testDebugUnitTest --console=plain
   ```
5. Then decide: deploy the backend, build the LLM cleanup step (Q2.2), M2C polish, or M2.5 native layout.
6. Never re-ask decisions from the "Locked-in product decisions" table.
