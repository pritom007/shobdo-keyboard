# Product Feature Backlog

This is the working task list for future Shohojakkhor development. Pick work by
task ID (for example, `VOICE-01`) so design, implementation, tests, and release
notes stay tied to the same outcome.

The order reflects product value and dependency risk, not a promised release
date. Elderly-first accessibility, explicit approval, and sensitive-field
privacy remain requirements for every item.

## Priority guide

- **Now:** strengthen the core typing and voice experience.
- **Next:** make the keyboard dependable and personal for daily use.
- **Later:** broaden it into a full power-user keyboard after the core is solid.

## Now — core experience

### Reliable editing

- [ ] **EDIT-01 — Complete selection-aware editing.** Replace selected text
  correctly for typing, candidates, paste, voice insertion, and AI revisions.
  Backspace deletes the selection immediately; add regression tests for
  collapsed, forward, reversed, and stale selections.
- [ ] **EDIT-02 — Undo and redo.** Maintain an in-memory edit history for the
  current field/draft, expose large undo/redo controls, and clear history on
  sensitive-field transitions or IME teardown.
- [ ] **EDIT-03 — Cursor-control spacebar.** Horizontal dragging on the
  spacebar moves the cursor predictably by character, with adjustable
  sensitivity and TalkBack-compatible alternatives.
- [ ] **EDIT-04 — Word and line deletion.** Add swipe-left backspace for word
  deletion and configurable hold-to-delete acceleration without skipping an
  active selection.
- [ ] **EDIT-05 — Editing toolbar.** Provide optional arrow, select-all, cut,
  copy, paste, undo, and redo buttons with 48 dp minimum targets.

### Patient voice composition

- [ ] **VOICE-01 — Patient recording session.** Tap to start, pause/resume, and
  finish manually; long pauses must not terminate recording. Show large Bengali
  listening, paused, processing, retry, and cancel states with sound/haptic cues.
- [ ] **VOICE-02 — Partial transcript preview.** Display provisional text while
  listening without committing it to the host field; clearly distinguish
  provisional from confirmed text.
- [ ] **VOICE-03 — Transcription alternatives.** Mark low-confidence words and
  offer large, tappable alternatives plus “আবার বলুন” and manual correction.
- [ ] **VOICE-04 — Voice editing commands.** Support Bengali commands for
  delete, replace, insert before/after, move cursor, select, undo, and redo.
  Commands must operate on a draft preview before host-field commit.
- [ ] **VOICE-05 — Conversational revision.** Support requests such as
  “শেষের কথাটা মুছে দাও”, “রহিমের জায়গায় করিম লিখো”, and “আরও ছোট করে লেখো”,
  preserving critical entities unless the correction is explicit.
- [ ] **VOICE-06 — Read-before-insert.** Read the final draft aloud, highlight
  the active phrase, and offer approve, revise, repeat, and cancel. Never press
  the host app's Send action.
- [ ] **VOICE-07 — Recording lifecycle hardening.** Handle calls, audio focus,
  permission revocation, IME hiding, rotation/configuration changes, timeouts,
  slow networks, and backend errors without losing or leaking a draft.

### Bangla suggestions and learning

- [ ] **LANG-01 — Persist accepted Banglish choices.** Store selection memory
  locally in DataStore, exclude sensitive/incognito fields, and provide one-tap
  deletion of learned data.
- [ ] **LANG-02 — Expand and evaluate the Bangla lexicon.** Add common names,
  places, family terms, medicine terms, greetings, and everyday Bangladeshi
  phrases using a versioned, testable word list.
- [ ] **LANG-03 — Context-aware candidate ranking.** Rank candidates using the
  current composition, preceding local words, user selections, and recency
  without sending surrounding text to the server.
- [ ] **LANG-04 — Correct previous words.** Tapping a composed word shows
  alternatives and replaces only that word while preserving cursor/selection.
- [ ] **LANG-05 — Mixed Bangla/English detection.** Switch candidates naturally
  between Bangla and English while preserving familiar English terms and user
  language preference.
- [ ] **LANG-06 — Dialect evaluation.** Build a consented, non-production test
  corpus covering representative Bangladeshi regional speech and transliteration
  variants; measure errors without retaining personal conversations.

## Next — daily usability and trust

### Accessibility presets

- [ ] **A11Y-01 — Size presets.** Add Standard, Large, and Extra Large layouts
  with adjustable height, key spacing, bottom gap, and label size.
- [ ] **A11Y-02 — High-contrast themes.** Ship light, dark, and high-contrast
  themes that do not communicate state through colour alone.
- [ ] **A11Y-03 — Tremor-friendly mode.** Increase spacing, tune touch slop,
  reduce accidental repeated presses, and make long-press timing configurable.
- [ ] **A11Y-04 — Haptic and sound controls.** Offer clear intensity/volume
  choices and separate feedback for typing, errors, recording, and approval.
- [ ] **A11Y-05 — TalkBack completion.** Label every key and state, define sane
  traversal order, announce composition/candidate changes, and test keyboard
  operation without sight.
- [ ] **A11Y-06 — Read keys and composed text aloud.** Make touch exploration
  and text read-back optional, private, interruptible, and compatible with
  system TTS settings.
- [ ] **A11Y-07 — Reduced motion and 200% font scale.** Remove nonessential
  animation and ensure primary controls remain visible and usable at maximum
  supported font/display scaling.
- [ ] **A11Y-08 — Accessibility test matrix.** Test older participants, reading
  glasses on/off, tremor simulation, bright/low light, one hand, TalkBack,
  Switch Access, slow network, and interrupted recording.

### Personalization and shortcuts

- [ ] **PERS-01 — Personal dictionary UI.** Add/edit/delete local words such as
  family names, places, and medicine names; never consult it in sensitive mode.
- [ ] **PERS-02 — Phrase shortcuts.** Expand user-defined shortcuts such as
  `ass` into a phrase, with preview and collision handling.
- [ ] **PERS-03 — Elder-friendly templates.** Provide optional greetings,
  appointment messages, address sharing, and common replies without inventing
  personal facts.
- [ ] **PERS-04 — Per-app preferences.** Remember language/layout/size choices
  locally per host app while never exporting package usage as analytics.
- [ ] **PERS-05 — Vocabulary backup/export.** Design an explicit encrypted
  export/import flow; do not enable Android automatic backup by default.

### Privacy and safety

- [ ] **PRIV-01 — Privacy dashboard.** Explain in plain Bengali what stays on
  device, what is sent, retention, and how to delete learned data.
- [ ] **PRIV-02 — User-controlled incognito mode.** Allow manual local-only mode
  in addition to automatic sensitive-field detection, with an unmistakable
  keyboard indicator.
- [ ] **PRIV-03 — Cloud-processing disclosure.** Before first cloud voice/AI use,
  explain exactly what audio/text is sent and obtain revocable consent.
- [ ] **PRIV-04 — Audio-retention enforcement.** Prove audio is RAM-only on the
  client/backend, apply request limits, and add tests that prevent accidental
  file/body logging.
- [ ] **PRIV-05 — Clipboard safety.** Suppress history in sensitive fields,
  support automatic expiration, and visibly distinguish pinned items.
- [ ] **PRIV-06 — Security review gate.** Threat-model every new network,
  personalization, clipboard, and backup feature before release.

### AI writing tools

- [ ] **AI-01 — Rewrite-mode chooser.** Ship clear Bengali actions for verbatim,
  organized, polished, shorter, polite, formal, simple-language, and spelling
  correction without a vague general-purpose prompt box.
- [ ] **AI-02 — Preview and diff.** Show original versus proposed text, highlight
  changes, and require explicit acceptance with undo available.
- [ ] **AI-03 — Critical-entity protection.** Detect names, dates, times, money,
  phone numbers, addresses, medicine names, pronouns, and religious language;
  never silently change uncertain entities.
- [ ] **AI-04 — Focused clarification.** When an important entity is uncertain,
  ask one short Bengali question instead of guessing.
- [ ] **AI-05 — Bangla/English translation.** Translate on request with preview,
  mixed-language preference, entity preservation, and local-only fallback when
  cloud processing is disabled.
- [ ] **AI-06 — Reply suggestions.** Generate optional short replies only from
  text the user explicitly selects/shares; the keyboard must not silently read
  unrelated host-app conversations.
- [ ] **AI-07 — AI safety evaluation.** Maintain golden tests for hallucination,
  tone, তুমি/আপনি, numbers, medical terms, mixed language, and reversibility.

### Clipboard and productivity

- [ ] **CLIP-01 — Clipboard history.** Provide a local history panel with copy,
  paste, delete, clear-all, and bounded retention.
- [ ] **CLIP-02 — Pin clipboard items.** Keep explicitly pinned items separately
  and require confirmation before clearing them.
- [ ] **CLIP-03 — Smart clipboard actions.** Recognize links, phone numbers, and
  addresses locally, offering safe formatting without network lookup.

## Later — complete keyboard and power-user features

### Layout and reachability

- [ ] **LAYOUT-01 — One-handed layout.** Left/right modes with persistent,
  easily reversible positioning and accessibility-safe key sizes.
- [ ] **LAYOUT-02 — Floating/draggable keyboard.** Constrain movement to safe
  screen areas, persist position per orientation, and provide a reset action.
- [ ] **LAYOUT-03 — Optional number and symbol rows.** Make rows configurable
  without crowding the elderly presets.
- [ ] **LAYOUT-04 — Custom toolbar.** Let users choose a small set of visible
  actions while essential accessibility controls remain discoverable.
- [ ] **LAYOUT-05 — Simplified native Bengali layout.** Build large-key consonant
  and vowel/modifier pages with tested hasanta, conjunct, and joiner behaviour.

### Advanced input

- [ ] **INPUT-01 — Swipe typing.** Add gesture typing only after candidate
  ranking is accurate, with a visible disable option and elderly-mode default
  off.
- [ ] **INPUT-02 — Handwriting.** Recognize Bengali and English handwriting with
  a large writing surface, undo strokes, alternatives, and local-first privacy.
- [ ] **INPUT-03 — Hardware-keyboard transliteration.** Apply the same Banglish
  engine to attached keyboards with a clear on/off shortcut and composition UI.
- [ ] **INPUT-04 — Emoji and symbol search.** Search Bengali/English descriptions,
  show recent items locally, and support large touch targets.
- [ ] **INPUT-05 — Optional stickers/GIFs.** Evaluate only after privacy,
  performance, licensing, and network-content risks have explicit designs.

### Offline and performance

- [ ] **OFFLINE-01 — Offline speech benchmark.** Compare suitable on-device
  engines on representative low-cost phones for Bangla accuracy, latency,
  memory, APK size, battery, and thermal impact.
- [ ] **OFFLINE-02 — Downloadable language model.** If benchmarks pass, move the
  large model out of the base APK with resumable download, integrity checking,
  storage controls, and deletion.
- [ ] **OFFLINE-03 — Offline/online routing.** Clearly show which engine will be
  used, prefer user policy, and never fall back to cloud silently.
- [ ] **PERF-01 — Startup and key-latency budget.** Define and continuously test
  cold IME startup, key response, candidate refresh, memory, and ANR targets on
  low-cost hardware.
- [ ] **PERF-02 — APK size reduction.** Audit native libraries/assets, use Play
  delivery where appropriate, and retain ABI-specific GitHub APKs.

### Release quality

- [ ] **QUAL-01 — IME instrumentation suite.** Cover enable/select onboarding,
  typing, selection, cursor, candidates, privacy modes, lifecycle, and TalkBack.
- [ ] **QUAL-02 — Host-app compatibility matrix.** Verify WhatsApp, Messages,
  Messenger, Chrome, Gmail, search, multiline editors, numeric fields, OTPs,
  and password managers.
- [ ] **QUAL-03 — Crash and performance telemetry design.** Add only minimized,
  opt-out, content-free events after the written privacy requirements are met.
- [ ] **QUAL-04 — Play Store release pipeline.** Automate signed AAB validation,
  staged rollout, release notes, mapping/native symbols, rollback, and signing
  certificate checks.
- [ ] **QUAL-05 — Elder beta program.** Establish consent, feedback prompts,
  issue severity, success measures, and a repeatable usability-study protocol.

## Cross-cutting definition of done

Every completed task must include, where applicable:

- [ ] Unit/instrumentation tests and regression coverage.
- [ ] Sensitive and incognito-field behaviour.
- [ ] Bengali strings and plain-language error states.
- [ ] TalkBack, font scaling, contrast, and 48 dp touch-target review.
- [ ] No typed text, transcript, audio, dictionary content, or secrets in logs.
- [ ] Lifecycle, offline, timeout, retry, cancel, and undo behaviour.
- [ ] Documentation and release notes.
- [ ] Physical-device verification, including at least one lower-spec device
  before production release.

