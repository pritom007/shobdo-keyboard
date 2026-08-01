# Testing Strategy

Testing is a core feature (§23). This document is the contract for what
"tested" means at each milestone.

## Layers

| Layer | Tooling | Runs where |
|---|---|---|
| Kotlin unit tests | JUnit4, kotlin.test, MockK | JVM (fast, `testDebugUnitTest`) |
| Instrumentation tests | AndroidX Test, Espresso, UIAutomator | `connectedAndroidTest`, emulator/device |
| Backend unit tests | pytest | `pytest` (M2+) |
| Backend integration tests | pytest + FastAPI TestClient + provider fake | `pytest` (M2+) |
| Golden prompt tests | pytest with invariant assertions | `pytest` (M3+) |
| Contract tests | Shared JSON schema, generated Kotlin data classes | Both (M2+) |
| Accessibility tests | AccessibilityChecks, manual TalkBack pass | Manual + `connectedAndroidTest` (M6+) |

## What we test at M1

- `InputPrivacyPolicy.classify()` returns `SENSITIVE` for password / PIN / OTP
  fields, `INCOGNITO` for `IME_FLAG_NO_PERSONALIZED_LEARNING`, `NORMAL`
  otherwise.
- `KeyboardMode` transitions are deterministic: `ENGLISH → BENGALI → ENGLISH`,
  shift latch on/off, numeric mode.
- `EnglishQwertyLayout` has correct row structure and keycap contents.
- Setup activity can be instantiated (Robolectric or instrumentation — kept
  minimal at M1).

Not covered at M1 (explicit gaps):

- Full IME instrumentation. Testing an IME through Espresso against a foreign
  app is fragile and slow; we defer to M2 where it can be scripted with
  UIAutomator against a small test target activity.
- Rotation / configuration change stress tests — deferred to M2.

## What we test at M2

- Long-pause tolerance: recorder does not truncate on 6-second silence.
- Microphone permission flow: denied → clear Bengali error, no crash.
- Backend `/v1/transcriptions`: happy path, oversized audio, malformed WAV,
  provider timeout, provider 5xx, provider malformed JSON.
- Redaction: log capture in tests asserts no transcript substring ever
  appears in any log line.

## What we test at M3

- Rewrite modes preserve invariants:
  - VERBATIM preserves word choice; only fixes obvious recognition artefacts.
  - ORGANIZED removes fillers but does not add greetings, promises, or facts.
  - POLISHED changes wording but preserves names, dates, amounts.
- Clarification loop: ambiguous date triggers `needsClarification=true` with
  a single Bengali question.
- Revision preserves untouched sentences byte-for-byte.
- Prompt injection resistance: transcripts containing "ignore previous
  instructions", "system:", or JSON-escape attempts do not exfiltrate.

## Golden prompt tests (M3+)

For each synthetic Bengali example:

```
{
  "raw_transcript": "...",
  "rewrite_mode": "ORGANIZED",
  "expected_invariants": {
    "preserves_names": ["রহিম", "করিম"],
    "preserves_dates": ["শুক্রবার"],
    "preserves_amounts": ["পাঁচ হাজার"],
    "no_added_greeting": true,
    "no_added_promise": true,
    "self_correction_applied": true
  },
  "expected_clarification": false
}
```

Assertions are **invariant-based**, not full-string matches, because model
outputs are non-deterministic. Full-string comparisons would create flaky
tests that pressure the team to weaken invariants — the opposite of what we
want.

## Contract tests

The `MessageEditResponse` schema is the source of truth. It lives in the
backend (`schemas/message_edit.py`). A CI step generates the equivalent
Kotlin data class into `core-model/build/generated/` and diff-checks it
against the committed version. Any drift fails the build.

Concrete tooling for schema-sync is chosen at M2 (candidates: `datamodel-code-generator`,
`quicktype`, or a hand-rolled generator; we start simple).

## Accessibility (M6+)

- Font-scale sweep: 100 %, 130 %, 150 %, 200 %. All primary controls remain
  hit-testable and readable.
- Touch-target audit: every actionable element ≥ 48 dp. Primary voice
  controls 56–72 dp.
- Contrast ratios ≥ 4.5:1 for text on background.
- TalkBack pass: every button announces a meaningful Bengali label.
- One-handed usability: primary voice button reachable in the bottom-third of
  the keyboard surface.

## What we deliberately *do not* test

- Exact string equality of AI outputs.
- Third-party provider internal behaviour.
- Emulator screenshots of the IME surface (brittle).

## Running the tests

```bash
# Android
cd android
./gradlew testDebugUnitTest         # unit tests
./gradlew lint                      # Android lint
./gradlew :app:connectedAndroidTest # emulator tests (M2+)

# Backend (M2+)
cd backend
pytest -q
```
