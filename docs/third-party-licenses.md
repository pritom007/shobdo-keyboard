# Third-Party Licenses

Every third-party runtime dependency lives here with its license, version,
and a short note. This file is source-of-truth for the license audit before
each release. New dependencies must add a row **in the same PR** that adds
the dependency.

## Android runtime dependencies (M1)

| Library | Version | License | Notes |
|---|---|---|---|
| Kotlin stdlib | 1.9.24 | Apache-2.0 | Language runtime. |
| AndroidX Core KTX | 1.13.1 | Apache-2.0 | Core extensions. |
| AndroidX AppCompat | 1.7.0 | Apache-2.0 | Theming for `SetupActivity`. |
| AndroidX Activity Compose | 1.9.0 | Apache-2.0 | Compose host in `app`. |
| Jetpack Compose BOM | 2024.06.00 | Apache-2.0 | UI toolkit for `app` module. |
| Compose Material3 | via BOM | Apache-2.0 | Material 3 widgets. |
| AndroidX Lifecycle Runtime KTX | 2.8.2 | Apache-2.0 | Lifecycle scopes. |
| AndroidX Annotation | 1.8.0 | Apache-2.0 | Nullability annotations. |

## Android test dependencies (M1)

| Library | Version | License | Notes |
|---|---|---|---|
| JUnit 4 | 4.13.2 | EPL-1.0 | JVM unit tests. |
| kotlin.test | 1.9.24 | Apache-2.0 | Kotlin-style assertions. |
| AndroidX Test Core | 1.6.1 | Apache-2.0 | Android context / rules. |
| AndroidX Test Runner | 1.6.1 | Apache-2.0 | Instrumentation runner. |
| Espresso Core | 3.6.1 | Apache-2.0 | Instrumentation UI (M2+). |

## Backend dependencies

Nothing yet — the backend directory is a skeleton. Rows will appear at M2:
FastAPI, Pydantic, uvicorn, python-multipart, groq (official SDK), httpx,
pytest, ruff, mypy.

## Deferred / evaluation-only

Libraries under evaluation but *not yet added*:

- **ML Kit Digital Ink Recognition** — for M7 handwriting. Requires review of
  its data-transmission behaviour before adoption.
- **whisper.cpp / sherpa-onnx / TFLite** — for M8 offline speech. Model
  licences differ per checkpoint and must be re-verified.

## Compliance notes

- No GPL/AGPL dependency is currently included. If a copyleft library is
  proposed later, escalate: it constrains the eventual license of Shobdo
  Keyboard itself.
- No dependency with an "no commercial use" clause.
- Every dependency version is pinned via `gradle/libs.versions.toml` (Android)
  or `pyproject.toml` (backend, from M2).
