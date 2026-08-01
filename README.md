# Shobdo Keyboard — শব্দ কিবোর্ড

A patient Bengali & English Android keyboard for elderly users. The differentiator
is voice-assisted message composition that **listens patiently, lightly organizes
the message, reads it back, and waits for explicit approval** before inserting.

> ⚠️ **Early prototype.** This repository is at **Milestone 2B (Banglish typing
> integrated into the IME)**. Voice, AI rewriting, and handwriting are
> *planned* — not yet implemented. See [`docs/roadmap.md`](docs/roadmap.md).
>
> 📎 **Picking up the project in a new chat / session?**
> Read [`docs/session-handoff.md`](docs/session-handoff.md) first.

---

## Current milestone

**M2B — Banglish typing.** Real Bengali input via phonetic transliteration is
working end-to-end on a physical Samsung device. Type Latin, see Bengali
candidates in a strip above the keyboard, tap to commit or press space to
commit the top guess. Selection memory learns your preferred readings.

## Repository structure

```
android-keyboard/
├── README.md
├── LICENSE-NOTE.md          # License TBD — not yet chosen
├── .gitignore
├── docs/                    # Product spec, architecture, privacy, roadmap
├── android/                 # Gradle Kotlin DSL Android project
│   ├── app/                 # Setup / onboarding activity
│   └── keyboard-ime/        # The IME service itself
└── backend/                 # Skeleton only — no FastAPI code yet (comes at M2)
```

## Local Android setup

Requires:
- JDK 17+ (Android Studio's bundled JBR 21 works fine)
- Android Studio Hedgehog or newer
- Android SDK with **platform-34** or **platform-36** installed
- A stable-API emulator (API 34 recommended) — the newest Android preview
  images filter out debug-signed custom IMEs, so verify on a stable image
  or a physical device.

First-time setup:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties
# Wrapper JAR is committed in this repo since it's tiny (44 KB). If it's
# ever missing, regenerate with a system Gradle: `gradle wrapper --gradle-version 8.9`.
./gradlew :app:assembleDebug :keyboard-ime:assembleDebug
```

Install to a connected device / emulator:

```bash
./gradlew :app:installDebug
adb shell am start -n com.shobdo.keyboard/.setup.SetupActivity
```

Then follow the two on-screen prompts to enable and select the keyboard.

## Build & test commands

```bash
# Compile everything
./gradlew assembleDebug

# JVM unit tests
./gradlew testDebugUnitTest

# Lint
./gradlew lint
```

## Backend setup

Not applicable yet. The `backend/` directory contains a README and
`.env.example` only. FastAPI + provider integration land in **Milestone 2**.

## Environment variables

None required for M1. Backend variables are documented in `backend/.env.example`
for future milestones.

## Known limitations (current)

- Bengali via Banglish only (`ami` → `আমি`). Native Bengali keycaps deferred.
- No voice input yet (planned M3).
- No AI rewriting yet (planned M4).
- No handwriting.
- Personal dictionary is in-memory only (planned M2C for DataStore persistence).
- Selection-memory learning does not survive app process kill (M2C).
- Shift key does nothing in Banglish mode; retroflex capitals reachable only
  via alternate candidates for now (M2C fix).

## Privacy notice

This project treats typed text and (future) audio as **sensitive**. Content
must never be logged. In password / PIN / OTP / card fields the keyboard runs
in a strict local-only mode with no cloud features. See
[`docs/privacy-model.md`](docs/privacy-model.md).

## License

**Not yet chosen.** See [`LICENSE-NOTE.md`](LICENSE-NOTE.md). Treat this
repository as *All Rights Reserved* until a license is added.

## Next milestone

**M2 — Standalone voice prototype**: microphone capture, long-pause state
machine, backend transcription round-trip, on a separate test screen (not yet
inside the IME). See [`docs/roadmap.md`](docs/roadmap.md).
