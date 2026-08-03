# Roadmap

Milestones follow the master brief §28. Each milestone must leave the project
compilable and testable.

---

## ✅ Milestone 0 — Repository foundation (in progress)

- [x] `README.md`
- [x] `.gitignore`
- [x] `LICENSE-NOTE.md` (placeholder — no license chosen)
- [x] `docs/roadmap.md`
- [x] `docs/architecture.md`
- [x] `docs/assumptions.md`
- [x] `docs/privacy-model.md`
- [x] `docs/security-threat-model.md`
- [x] `docs/testing-strategy.md`
- [x] `docs/third-party-licenses.md`
- [x] `docs/product-spec.md`
- [x] `docs/backend-api.md`
- [x] `docs/elderly-usability-guidelines.md`
- [x] `backend/` skeleton (README + `.env.example` only — no code)
- [ ] CI workflows (`.github/workflows/`) — deferred to first PR

## 🚧 Milestone 1 — Functional Android IME

- [x] Gradle Kotlin DSL project with version catalog
- [x] `app` module with `SetupActivity` (Compose)
  - [x] "কিবোর্ড চালু করুন" button → `Settings.ACTION_INPUT_METHOD_SETTINGS`
  - [x] "কিবোর্ড বেছে নিন" button → `InputMethodManager.showInputMethodPicker`
- [x] `keyboard-ime` module
  - [x] `ShohojakkhorInputMethodService` registered in manifest
  - [x] `method.xml`
  - [x] English QWERTY layout (letters, shift, space, backspace)
  - [x] Enter honours `EditorInfo.imeOptions`
  - [x] Number / symbol mode toggle
  - [x] Bengali placeholder layout (clearly labelled "শীঘ্রই আসছে")
  - [x] Globe key opens Android IME picker
  - [x] `InputPrivacyPolicy` — SENSITIVE / INCOGNITO / NORMAL classification
  - [x] Sensitive-field mode disables all future cloud hooks
  - [x] No content logging anywhere
- [x] Unit tests: privacy policy, keyboard mode, English layout — **27 pass, 0 fail**
- [x] Local Gradle build verified: `assembleDebug` + `testDebugUnitTest` + `lint` all green
- [x] APK installs on emulator (`adb install` success, package + components in resolver)
- [x] End-to-end launch verified on Samsung Galaxy SM-S731B (Android 16, API 36)
- [ ] Instrumentation test — deferred to a later milestone

**Operational note:** local verification on 2026-08-01 ran against the only
system image installed (`android-37.0`, an Android 17 / "AI Glasses" preview).
That preview OS refuses to resolve `am start` intents for debug-key-signed
apps (`result=-92`, no process crash) and filters custom IMEs out of
`ime list`. Every other check passed. To complete visual verification, run
the app from a stable emulator: install Android 14 (API 34) system image via
Android Studio → Device Manager, or connect a physical device.

**Success criterion:** User installs app, enables the keyboard via system
settings, selects it in the IME picker, types English into WhatsApp / Messages,
and switches modes. Sensitive password fields fall back to a local-only view.

## 🚧 Milestone 1.1 — Layout & reachability patches

Small in-flight fixes on top of M1. Not a full milestone.

- [x] **Navigation-bar inset fix.** The IME window now consumes
  `WindowInsetsCompat.Type.navigationBars() | mandatorySystemGestures()` as
  bottom padding via `WindowCompat.setDecorFitsSystemWindows(false)` in
  `ShohojakkhorInputMethodService.onCreateInputView` and an
  `OnApplyWindowInsetsListener` on `KeyboardView`. Bottom row is no longer
  eaten by gesture-nav.
- [x] `KeyboardView.setExtraBottomGapDp()` — programmatic hook for adding
  a user-configurable gap on top of the nav inset. Currently 0 by default.
- [ ] User-facing setting to adjust the extra bottom gap. Deferred to M6
  (elderly usability pass).
- [ ] **Draggable keyboard position** (user request). Deferred to a dedicated
  reachability task — see M6.5 below. Full drag-to-position requires
  window-manager-level handling, persistence, and one-handed variants; not
  a quick fix.

## Milestone 2 — Bengali input *(promoted from later milestones)*

The elderly-friendly product cannot ship without real Bengali typing.
Voice work moves out to make room. Two possible sub-tracks; the winning
approach may be either or both depending on user testing.

### ✅ Track A — Banglish phonetic transliteration (slices A & B complete)

Slice A — Engine (complete, 45 unit tests):
- [x] `:transliteration` pure-Kotlin Android library module.
- [x] `Rules.kt` — ~55 vowel / consonant / modifier rules, longest-match,
  case-sensitive (retroflex `T` vs. dental `t`, etc.).
- [x] `Tokenizer.kt` — longest-match Latin → token splitter.
- [x] `Assembler.kt` — position-aware Bengali assembly (matra vs. independent
  vowel, hasant-based conjuncts, modifier passthrough).
- [x] `Dictionary.kt` — ~200 hand-curated seed words (greetings, family, days,
  common verbs, numbers, everyday nouns).
- [x] `SelectionMemory.kt` — in-memory learning with recency-weighted boost.
- [x] `AvroLikeEngine.kt` — dictionary + rule primary + rule alternates +
  memory + literal escape hatch. Deduplicated, sorted, capped at 5.

Slice B — IME integration (complete):
- [x] `KeyboardMode.BENGALI_BANGLISH` replaces the M1 placeholder.
- [x] `BengaliBanglish` layout — same QWERTY letters, language toggle flipped
  to "En".
- [x] `CandidateStripView` — 48-dp-tall horizontal strip, 20 sp Bengali text,
  top candidate tinted, tap-to-commit, scrollable overflow.
- [x] Composition wiring in `ShohojakkhorInputMethodService`:
  - Latin buffer per keystroke → `setComposingText(topBengali)`.
  - Tap candidate → `commitText(bengali)` + `engine.onUserSelection(...)`.
  - Space / Enter commit top candidate.
  - Backspace mid-composition trims one Latin char.
  - Language toggle mid-composition commits raw Latin (never drop input).
  - Sensitive fields refuse Banglish mode.

### 🚧 Track A — Slice C (M2C, next up)

Polish and persistence after the user has been beta-testing live on their
Samsung device.

- [ ] Persist `SelectionMemory` to DataStore so learned choices survive
  process kill.
- [ ] Grow the seed dictionary using words the user has flagged as
  frequently-mis-transliterated (organic feedback).
- [ ] Fix the `ng + vowel` edge case (`angur` → আঙ্গুর, currently produces
  আংুর via anusvara).
- [ ] Enable shift in `BENGALI_BANGLISH` mode so retroflex capitals
  (`T`, `D`, `N`, `Sh`) are reachable via the keyboard itself.
- [ ] Unit tests for the composition state machine in `ShohojakkhorInputMethodService`
  (currently zero — only the pure engine has tests).
- [ ] Instrumentation smoke test for `SetupActivity`.

### Track B — Simplified native Bengali layout *(deferred)*

- [ ] Two-page Bengali key layout: consonants (page 1), vowels & modifiers
  (page 2). Large keycaps, elderly-first.
- [ ] Long-press for less-common conjuncts.
- [ ] Zero-width-joiner and hasanta handling.

**Success criterion:** an elderly user can compose an ordinary Bengali WhatsApp
message using at least one of the two tracks, without help.

## Milestone 3 — Standalone voice prototype *(was M2)*

- [ ] Standalone test screen (not yet inside the IME)
- [ ] Microphone permission flow
- [ ] `AudioRecord`-based capture, mono, 16 kHz
- [ ] Local voice-activity / silence tracking (client owns pause behaviour)
- [ ] `VoiceCompositionState` sealed state machine
- [ ] Backend `/v1/transcriptions` endpoint (Groq Whisper)
- [ ] Raw transcript displayed; no persistence
- [ ] Long-pause acceptance test (>5 s pause must not truncate)

## Milestone 4 — AI organization *(was M3)*

- [ ] `/v1/messages/rewrite` and `/v1/messages/revise` endpoints
- [ ] Structured JSON output (Pydantic schema)
- [ ] Three rewrite modes: VERBATIM / ORGANIZED / POLISHED (default ORGANIZED)
- [ ] Critical-entity extraction + clarification loop
- [ ] Voice-based correction prototype
- [ ] Read-back via Android `TextToSpeech`
- [ ] Golden prompt tests (invariant-based, not string-match)

## Milestone 5 — Integrate voice into the IME *(was M4)*

- [ ] Voice button in Bengali keyboard surface
- [ ] Recording state visible in the IME view
- [ ] Full flow: listen → organize → review → approve → `commitText`
- [ ] Sensitive-field blocking honoured
- [ ] Lifecycle: audio interruptions, phone calls, IME hide

## Milestone 6 — Elderly usability improvements

- [ ] Large mode
- [ ] Haptic / sound settings
- [ ] Caregiver setup flow
- [ ] Common templates ("আসসালামু আলাইকুম" etc.)
- [ ] Personal dictionary UI
- [ ] Undo history for current draft
- [ ] Usability study checklist

## Milestone 6.5 — Keyboard reachability

- [ ] Draggable keyboard position (top vs. bottom half, save preference).
- [ ] One-handed mode (shift left / right).
- [ ] Compact / normal / tall height variants.
- [ ] Setting UI accessible from `SetupActivity`.

## Milestone 7 — Handwriting

Deferred. Design present in `docs/architecture.md`; implementation only after M4
and M5 are stable.

## Milestone 8 — Offline speech evaluation

Benchmark whisper.cpp / sherpa-onnx / TFLite on representative low-cost
devices. Ship only if quality, latency, and battery are acceptable.
