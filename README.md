# Shobdo Keyboard — শব্দ কিবোর্ড

A patient Bengali & English Android keyboard for elderly users. Type Bengali
phonetically (Banglish), tap the 🎙️ mic key to **speak** and let the keyboard
transcribe it, and get safe, private typing — sensitive fields never touch
the cloud.

> **Status:** working prototype. Voice + Bengali typing verified on a real
> Samsung device. See [`docs/roadmap.md`](docs/roadmap.md) for what's next.
>
> 📎 **Picking up the project in a new chat / session?** Read
> [`docs/session-handoff.md`](docs/session-handoff.md) first.

---

## What works

- **Banglish typing** — type Latin (`ami` → `আমি`), see up to 5 Bengali
  candidates, tap to commit or space to commit the top guess. Selection
  memory learns your preferred readings.
- **Avro-style shift in Bangla mode** — tap ⇧ to get uppercase Latin keys,
  so `T`→ট, `D`→ড, `N`→ণ, `R`→ড়, `Sh`→ষ, plus long vowels `A`→আ, `I`→ঈ,
  `U`→ঊ, `E`→এ, `O`→ও. One-shot (auto-releases after one char, matching
  Avro); tap ⇧ twice for caps latch.
- **Bengali symbols page** — tap `?123` in Bangla for Bengali digits ০-৯
  and punctuation । ॥ ৎ, plus common ASCII punctuation.
- **Hybrid voice** — tap 🎙️ and speak. The keyboard transcribes via:
  - **Online (primary):** a thin backend → Groq Whisper large-v3.
    Auto-detects Bengali → Bengali script, English → English. Best quality.
  - **Offline (fallback):** on-device Whisper base. Less accurate but
    always available — if the server is unreachable the panel shows a
    friendly "ইন্টারনেট নেই, অফলাইনে লিখছি…" message, never an error.
- **Privacy by default** — typed text, audio, and transcripts are **never
  logged**. Password / PIN / OTP / card fields run a strict local-only mode
  with no cloud features. See [`docs/privacy-model.md`](docs/privacy-model.md).

## Download the APK

Pre-built APKs are published on the **[GitHub Releases page](../../releases)**.
Each version (`v0.1.0`, `v0.2.0`, …) gets its own release with the APK attached.

To install on a phone:
1. Download the APK for your device — **`arm64-v8a`** for nearly all modern
   phones, `armeabi-v7a` for older ones.
2. On the phone, enable **Settings → Apps → Special access → Install unknown
   apps** for your browser/files app.
3. Open the downloaded APK and tap Install.

The APK is debug-signed (fine for a prototype). A real signing key will be
added before any Play Store distribution.

## Repository structure

```
android-keyboard/
├── README.md
├── LICENSE-NOTE.md          # License TBD — not yet chosen
├── .gitignore               # Large binaries are gitignored (fetched by script)
├── scripts/
│   └── download-dependencies.sh   # One-time: fetches the model + AAR
├── .github/workflows/       # CI (build+test) + release (APK on every tag)
├── docs/                    # Product spec, architecture, privacy, roadmap
├── android/                 # Gradle Kotlin DSL Android project
│   ├── app/                 # Setup / onboarding activity + model assets*
│   ├── keyboard-ime/        # The IME service + voice wiring
│   ├── transliteration/     # Pure-Kotlin Banglish → Bengali engine
│   ├── voice-capture/       # Mic → in-memory WAV
│   ├── speech/              # OkHttp client → backend (online voice leg)
│   ├── speech-ondevice/     # sherpa-onnx Whisper base (offline voice leg)
│   └── local-repo/          # Local Maven repo for the sherpa-onnx AAR*
└── backend/                 # FastAPI speech-to-text proxy → Groq large-v3
```
\* marked paths are **gitignored** (>100 MB / keep clones fast) and are
**created by `scripts/download-dependencies.sh`**.

## One-time setup

Requires:
- **JDK 17+** (Android Studio's bundled JBR 21 works)
- **Android SDK** with `platform-36` installed
- **Python 3.10+** for the backend (only if you'll run the online voice leg)
- A physical device is recommended; the newest Android preview emulator
  images reject debug-signed custom IMEs.

```bash
# 1. Fetch the large dependencies (Whisper base model ~160 MB + sherpa-onnx
#    AAR ~47 MB). Idempotent, checksum-verified. ~30-90 s on a fast link.
./scripts/download-dependencies.sh

# 2. Tell Gradle where your Android SDK is.
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 3. Build + install on a connected device.
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb shell ime set com.shobdo.keyboard/.ime.ShobdoInputMethodService
```

Then open any text field, switch to Shobdo keyboard (IME picker → Shobdo),
and follow the two on-screen prompts the first time.

## Build & test

```bash
cd android

# Build the debug APK (per-ABI splits: arm64-v8a, armeabi-v7a)
./gradlew :app:assembleDebug

# Run every JVM unit test across all modules
./gradlew :keyboard-ime:testDebugUnitTest :transliteration:testDebugUnitTest \
          :voice-capture:testDebugUnitTest :speech:testDebugUnitTest \
          :speech-ondevice:testDebugUnitTest

# Backend tests
cd ../backend && pytest -q
```

## Running the backend (online voice leg)

The online voice path needs the FastAPI backend running and reachable from
the phone. For **local development**:

```bash
cd backend

# 1. Create a virtualenv + install deps (one-time).
python3 -m venv .venv
.venv/bin/pip install -e ".[dev]"

# 2. Add your provider key to .env (one-time). Copy from .env.example.
cp .env.example .env
# Edit .env and set OPENAI_API_KEY=<your Groq key>
#   OPENAI_BASE_URL=https://api.groq.com/openai/v1  (default)
#   OPENAI_TRANSCRIBE_MODEL=whisper-large-v3      (default)

# 3. Run the server.
.venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8000

# 4. In another terminal, tunnel the phone's localhost to the Mac (dev only).
adb reverse tcp:8000 tcp:8000
```

For **real-world use** (so the phone doesn't need `adb reverse`), deploy the
backend — Render's free web-service tier fits a FastAPI app with file uploads
(it sleeps after ~15 min idle, ~30 s cold start). Set the same env vars on the
host and point the app at the deployed HTTPS URL. See
[`docs/session-handoff.md`](docs/session-handoff.md) → "Open issues" for the
deploy plan.

> ⚠️ **Never** commit `backend/.env` — it's gitignored and holds your API key.
> Never put the key in the APK.

## CI/CD

Two GitHub Actions workflows live in [`.github/workflows/`](.github/workflows):

- **`ci.yml`** — on every push / PR: runs the dependency script, builds the
  APK, and runs all Android unit tests + backend pytest. Caches Gradle and
  the large deps so most runs are fast.
- **`release.yml`** — on every `v*` tag: builds the APK and publishes a
  GitHub Release with the APK attached (the "Download the APK" flow above).

To cut a new release:

```bash
git tag v0.1.0
git push origin v0.1.0
# → the release workflow runs, and v0.1.0 appears on the Releases page.
```

## Environment variables

All backend config is in [`backend/.env`](backend/.env.example) (copy to
`backend/.env`). Key ones:

| Var | Default | Purpose |
|---|---|---|
| `OPENAI_API_KEY` | _(empty)_ | Your Groq (or OpenAI-compatible) key. **Required** for online voice. |
| `OPENAI_BASE_URL` | `https://api.groq.com/openai/v1` | Any OpenAI-compatible endpoint. |
| `OPENAI_TRANSCRIBE_MODEL` | `whisper-large-v3` | Swap to change the STT model. |
| `MAX_AUDIO_SECONDS` | `60` | Hard cap on audio length. |
| `RATE_LIMIT_PER_MINUTE` | `20` | Per-device rate limit. |

## Known limitations

- Voice quality is raw STT — the on-device base is Bengali-only and
  imperfect; the online large-v3 is near-SOTA but garbles proper nouns /
  drops punctuation. An LLM cleanup step (raw STT → LLM → cleaned text) is
  planned, not yet built.
- Banglish only (native Bengali keycap layout is a secondary track).
- Personal dictionary + selection memory are in-memory only (don't survive
  a process kill); DataStore persistence is planned.
- The standalone `VoiceActivity` (setup screen) still uses the old
  server-only path and is secondary to the in-keyboard voice.

## Privacy notice

This project treats typed text and audio as **sensitive**. Content is never
logged. The provider API key lives only on the backend, never in the APK. In
password / PIN / OTP / card fields the keyboard runs a strict local-only
mode with no cloud features. See [`docs/privacy-model.md`](docs/privacy-model.md).

## License

**Not yet chosen.** See [`LICENSE-NOTE.md`](LICENSE-NOTE.md). Treat this
repository as *All Rights Reserved* until a license is added.
