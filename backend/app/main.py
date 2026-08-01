"""FastAPI application entrypoint.

Run locally:
    uvicorn app.main:app --reload --port 8000

The app is intentionally tiny: a health probe plus one transcription endpoint.
All config comes from environment variables (see `app/settings.py`).
"""

from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from . import __version__
from .api import health, transcribe
from .settings import settings

app = FastAPI(
    title="Shobdo Backend",
    version=__version__,
    description="Speech-to-text proxy for the Shobdo Keyboard Android IME.",
)

_origins = [o.strip() for o in settings.allowed_origins.split(",") if o.strip()] or ["*"]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_origins,
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(transcribe.router)


@app.get("/")
async def root() -> dict[str, str]:
    return {"service": "shobdo-backend", "version": __version__, "health": "/health"}
