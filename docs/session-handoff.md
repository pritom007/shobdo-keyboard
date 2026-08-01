# Session Handoff — Read This First (Next Chat)

**Last updated:** 2026-08-01, end of session 1.
**Current state:** Milestones **M0**, **M1**, **M1.1**, **M2A**, and **M2B** all complete, built, and running on the user's physical device. Ready for **M2C** (or a jump to M3, see below).

---

## TL;DR

Shobdo Keyboard (শব্দ কিবোর্ড) is an elderly-friendly Bengali + English Android IME. The differentiating experience is voice-assisted composition (planned M3+), but the priority pivot to **Bengali text input first** landed in M2. Banglish transliteration (`ami` → `আমি`) is working live on the user's Samsung Galaxy SM-S731B (Android 16 / API 36) with a 5-slot candidate strip, selection-memory learning, and universal-keyboard commit semantics (space commits top candidate).

72 unit tests pass. No known blockers.

## Repository facts

- **Working directory:** `/Users/pritom_binance/Desktop/office/android-keyboard`
- **Git:** not initialised (user's choice; they will `git init` when ready).
- **Package / applicationId:** `com.shobdo.keyboard` (chosen because no external domain to claim).
- **License:** intentionally not chosen. `LICENSE-NOTE.md` says "All Rights Reserved" as a safe placeholder. Do not silently pick one.

## Toolchain (user's machine)

- JDK: **Android Studio JBR 21** at `/Applications/Android Studio.app/Contents/jbr/Contents/Home` (system Java is 1.8, do not use it — AGP 8.7 needs 17+).
- Android SDK: `~/Library/Android/sdk` with `platform-36` (auto-installed by Gradle) and `build-tools/34.0.0`, `36.1.0`, `37.0.0`.
- Gradle: 8.9 (wrapper JAR committed at `android/gradle/wrapper/gradle-wrapper.jar`).
- AGP: 8.7.3. Kotlin 1.9.24. Compose Compiler 1.5.14.
- `compileSdk` / `targetSdk` = 36. `minSdk` = 24.
- Physical device connected via USB: **Samsung SM-S731B**, Android 16 (API 36), serial `R5CYA2DZC1H`. USB debugging + File Transfer mode.
- Local emulators (Pixel_7, Pixel_6a) are Android 17 preview / "AI Glasses" images that **cannot launch debug-signed apps** — always test on the physical device, not on those AVDs.

## Standard build / install commands

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
cd /Users/pritom_binance/Desktop/office/android-keyboard/android

./gradlew :app:assembleDebug \
          :keyboard-ime:testDebugUnitTest \
          :transliteration:testDebugUnitTest \
          --console=plain

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell ime set com.shobdo.keyboard/.ime.ShobdoInputMethodService
```

## Current module layout

```
android/
├── settings.gradle.kts       (:app, :keyboard-ime, :transliteration)
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/gradle-wrapper.{properties,jar}
├── app/                      Setup activity (Compose)
├── keyboard-ime/             InputMethodService + views + privacy + composition wiring
└── transliteration/          Pure Kotlin Banglish → Bengali engine

backend/                      Skeleton only (README, .env.example). No code yet.
docs/                         All product / architecture / privacy docs
```

## Verified state (72 tests green)

| Module | Suite | Tests |
|---|---|---|
| `keyboard-ime` | `KeyboardModeTransitionsTest` | 7 |
| `keyboard-ime` | `EnglishQwertyTest` | 8 |
| `keyboard-ime` | `InputPrivacyPolicyTest` | 12 |
| `transliteration` | `TokenizerTest` | 9 |
| `transliteration` | `AssemblerTest` | 14 |
| `transliteration` | `AvroLikeEngineTest` | 16 |
| `transliteration` | `SelectionMemoryTest` | 6 |
| **Total** | | **72** |

## What actually works on the phone right now

1. Install + enable + set as default IME ✅
2. English QWERTY typing, shift (LOWER→UPPER→CAPS), symbols/numbers, backspace ✅
3. Enter honours `EditorInfo.imeOptions` (Send / Search / Done / newline) ✅
4. Globe key opens Android IME picker ✅
5. Language toggle `বাং` ↔ `En` ✅
6. Bengali Banglish mode:
   - Type Latin, see live Bengali as composing text (underlined)
   - Candidate strip above keyboard with up to 5 candidates
   - Top candidate slightly tinted (visual hint for space=commit)
   - Tap any candidate → commits + records selection for future ranking
   - Space / Enter commit top candidate + separator
   - Backspace inside composing buffer trims one Latin char
   - Toggling out of Banglish commits raw Latin (never drops user input)
7. Sensitive-field detection: password / PIN / OTP / card fields force plain English keyboard with red "সুরক্ষিত মোড" banner and refuse Banglish mode ✅
8. Navigation bar / gesture inset applied as bottom padding so no key is clipped on gesture-nav ✅

## Locked-in product decisions (do NOT re-ask)

| Area | Decision | Source |
|---|---|---|
| Bengali input priority | **Banglish phonetic first**, native layout later | User Q1 in slice B kickoff |
| Candidate UX | **5 candidates, tap to commit** | User Q2 in slice B kickoff |
| Space during composition | **Commits top candidate + space** (universal IME behavior — user OK'd this after I proposed it) | User confirmation |
| Selection learning | Yes, boost user's past choices — currently **in-memory only**, DataStore persistence is M2C | Roadmap |
| Backend provider | **OpenAI-compatible client** (`openai` SDK with configurable `base_url`). Groq at `https://api.groq.com/openai/v1` for the initial deployment | User answer |
| Bengali TTS availability | **Soft warning**, keyboard remains usable | User answer |
| License | **Placeholder only**, do not silently choose | User answer |
| Git init | **User will do it themselves**, do not `git init` | User answer |
| Analytics | **None** until consent + policy + redaction exist | Master brief §24 |
| Content in logs | **Never** — no typed text, no audio, no transcripts, no drafts, no dictionary entries in any log line | Master brief §26 |
| Provider keys in APK | **Never** — keys live only on backend | Master brief §26 |
| Auto-Send in host app | **Never** — keyboard only inserts, user always presses Send | Master brief §4.5 |

## Preferences learned from working with the user

- Wants concrete progress over deliberation. Say "starting now" and start.
- Trusts reasonable defaults; only wants to be asked when a decision is irreversible or costs money.
- Says "keep going" — treat that as blanket approval for the current plan through the next milestone.
- Answers questions by number (`Q1. 1; Q2. 2`) — offer numbered options.
- Prefers Bengali-first UX (product), technical discussion in English.
- Willing to test on physical device. Expects real, testable output from each slice.

## Open bugs / limitations (user knows about these, no need to re-report)

- `ng + vowel` sequence like `angur` doesn't render আঙ্গুর correctly with the rule engine (`ng` always maps to anusvara ং). Workarounds: type `NG` for consonant ঙ, or rely on dictionary.
- Dictionary is small (~200 hand-curated words). Growing via user selections; a curated bump comes with real usage feedback.
- Shift key does nothing in `BENGALI_BANGLISH` mode. Retroflex consonants (T=ট, D=ড, N=ণ, Sh=ষ) are unreachable via keys, only via alternate candidates. Reconsider at M2C.
- Selection memory wipes on process kill (in-memory `MutableMap`). DataStore-backed persistence is next slice.
- Draggable / repositionable keyboard: acknowledged as future work at M6.5. For now user has a configurable `extraBottomGapPx` hook in `KeyboardView.setExtraBottomGapDp()` but no UI.

---

## Where to go next (menu for the next session to pick from)

Present these to the user. Do not assume; ask which they want first.

### Option A — M2C: Polish the Banglish experience *(recommended if user has been using it and has feedback)*
- Persist selection memory to DataStore (survives process kill).
- Grow dictionary based on user's actually-typed words (they've been beta-testing since end of session 1).
- Optionally enable shift in Banglish mode for retroflex capitals.
- Fix `ng + vowel` edge case.
- Add settings UI in the SetupActivity for: extra bottom gap, dictionary reset, haptic on/off.
- Add unit tests for the composition state machine (`ShobdoInputMethodService`) — it currently has none.
- Add instrumentation smoke test for the setup activity.

### Option B — M3: Voice prototype
- Standalone screen in the `app` module (not yet inside the IME) that requests mic permission, records 16 kHz mono, uploads to backend, shows raw transcript.
- Create the backend: FastAPI + `openai` SDK + `GET /health` + `POST /v1/transcriptions`.
- Local voice-activity detection so long pauses don't truncate.
- Docker image + `.env` from `backend/.env.example`.
- New Android modules: `voice-capture`, `speech-domain`, `core-network`, `core-model`, `privacy-security` (extract the in-module policy).

### Option C — M2.5: Native Bengali key layout (secondary Bengali input path)
- Real Bengali keycaps (ক খ গ …). Two pages.
- Toggleable as an alternate to Banglish mode.

**Default recommendation for a "let's continue" prompt:** ask which of A / B / C. If user says "just keep going", pick **A** — polish beats new-features, and the user has been using it live so has organic feedback.

---

## Critical files to grep before making changes

| Concern | Where |
|---|---|
| Add a Bengali word to the dictionary | `android/transliteration/src/main/java/com/shobdo/keyboard/translit/Dictionary.kt` |
| Change/add a phonetic rule | `android/transliteration/src/main/java/com/shobdo/keyboard/translit/Rules.kt` |
| Change candidate ranking | `android/transliteration/src/main/java/com/shobdo/keyboard/translit/AvroLikeEngine.kt` |
| Change candidate strip look | `android/keyboard-ime/src/main/java/com/shobdo/keyboard/ime/view/CandidateStripView.kt` |
| Change composition behaviour (space, backspace, tap) | `android/keyboard-ime/src/main/java/com/shobdo/keyboard/ime/ShobdoInputMethodService.kt` |
| Add / remove privacy heuristics | `android/keyboard-ime/src/main/java/com/shobdo/keyboard/ime/privacy/InputPrivacyPolicy.kt` |
| Roadmap / milestone status | `docs/roadmap.md` |
| Product decisions log | `docs/assumptions.md` |
| Privacy model | `docs/privacy-model.md` |
| Threat matrix | `docs/security-threat-model.md` |

## First actions for the next session

1. Read this file. Then `docs/roadmap.md` and `docs/assumptions.md`.
2. Optionally re-run tests to confirm state hasn't drifted:
   ```bash
   cd android
   ./gradlew :keyboard-ime:testDebugUnitTest :transliteration:testDebugUnitTest --console=plain
   ```
3. Ask the user: "Which of A / B / C do you want next?" (see the menu above).
4. Only after user picks: start with a concrete implementation plan (§1.1 of master brief), then execute in vertical slices.
5. Never re-ask decisions from the "Locked-in product decisions" table.
