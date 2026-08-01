# Security Threat Model

Coverage matrix per master brief §26. This is a starting skeleton — every
milestone must revisit the relevant rows and add tests for any new attack
surface introduced by that milestone.

| # | Threat | Where it applies | Mitigation | Status |
|---|---|---|---|---|
| 1 | Provider API key extraction from APK | Android | Keys live **only** on backend. Client authenticates with a short-lived install token. | M2 |
| 2 | Keyboard content logging | Android IME | No `Log.*` call ever receives typed content. Lint rule to be added at M2 (custom detector). | M1 partial |
| 3 | Audio retention | Backend | In-memory only; buffer freed on response send. No disk. | M2 |
| 4 | Transcript retention | Backend | Never stored. Never in logs. | M2 |
| 5 | Malicious host application feeding poisoned surrounding-text | Android IME | Surrounding text is **not** sent to backend by default. When it becomes needed, it is treated as untrusted data and cannot override system prompt. | M2 |
| 6 | Sensitive input fields (password / OTP / PIN / card) | Android IME | `InputPrivacyPolicy` blocks all cloud features and personalization. UI badge visible. | **M1 ✅** |
| 7 | Clipboard leakage | Android IME | We never read clipboard automatically. Clipboard-history UI is a non-goal (§32). | M1 |
| 8 | Debug logging in release | Android + Backend | ProGuard rule strips `Log.d` / `Log.v`. Backend log formatter drops request/response bodies unconditionally. | M2 |
| 9 | Auto-backup leaking personal dictionary | Android | `android:allowBackup="false"` until encrypted backup is designed. | **M1 ✅** |
| 10 | Screenshot / recent-tasks leaking IME content | Android IME | IME windows are not screenshot by default; explicit `FLAG_SECURE` review at M4. | M4 |
| 11 | Man-in-the-middle on backend requests | Android + Backend | HTTPS only. Cert pinning evaluated at M2; deferred if it complicates ops. | M2 |
| 12 | Replay attacks on backend endpoints | Backend | Nonce + timestamp header, ±60 s window. | M2 |
| 13 | Abuse of public backend endpoints | Backend | Per-install token, per-IP rate limit, request-size and duration caps. | M2 |
| 14 | Prompt injection through user speech ("ignore previous instructions") | Backend | Structured-output validation, response schema rejection on prompt-leak markers, adversarial golden tests. | M3 |
| 15 | Model-generated misinformation (e.g. inventing a date) | Backend + UI | AI must return `critical_entities`; unchanged entities are surfaced in review; user approval is mandatory. | M3 |
| 16 | Personal dictionary exposure via UI | Android | Encrypted-at-rest for user-flagged sensitive entries. No copy-to-clipboard shortcut. | M6 |
| 17 | Accidental analytics collection | Android | **No analytics SDK present.** Adding one requires PR review + `docs/privacy-model.md` update. | Ongoing |
| 18 | Third-party SDK data exfiltration | Android | Every dependency tracked in `docs/third-party-licenses.md` with a note on runtime network behaviour. | Ongoing |
| 19 | Voice command spoofing ("send", "যাও") | Android | Voice commands never bypass the explicit approval step. Ambiguous commands trigger a confirmation prompt. | M3 |
| 20 | Race conditions inserting text into a stale field | Android IME | `InputConnection` calls guarded by `getCurrentInputConnection()` null check and a per-flow generation token. | M4 |

## Recurring review points

- Every PR must answer: *does this add a new place where user content lives?*
- Every dependency addition must add a row to `third-party-licenses.md`.
- Every milestone completion updates the "Status" column above.
