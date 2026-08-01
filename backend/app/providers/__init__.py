"""Speech providers.

A provider implements the `SpeechProvider` protocol (defined in `speech.py`).
The default is `OpenAICompatSpeechProvider`, which talks to any
OpenAI-compatible transcription endpoint (Groq by default). Swap by editing
env vars — no code change.
"""
