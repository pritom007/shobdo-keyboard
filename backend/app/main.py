"""FastAPI application entrypoint.

Run locally:
    uvicorn app.main:app --reload --port 8000

The app is intentionally tiny: a health probe plus one transcription endpoint.
All config comes from environment variables (see `app/settings.py`).
"""

from __future__ import annotations

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from . import __version__
from .api import health, transcribe
from .settings import settings

app = FastAPI(
    title="Shobdo Backend",
    version=__version__,
    description="Speech-to-text proxy for the Shobdo Keyboard Android IME.",
)

_origins = [o.strip() for o in settings.allowed_origins.split(",") if o.strip()]
if _origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=_origins,
        allow_credentials=False,
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["Content-Type", "X-Device-Id", "X-Shobdo-Secret"],
    )


@app.exception_handler(HTTPException)
async def stable_http_error(_request: Request, exc: HTTPException) -> JSONResponse:
    """Keep API errors on the documented top-level `{error: {code}}` shape."""
    if isinstance(exc.detail, dict) and "error" in exc.detail:
        return JSONResponse(status_code=exc.status_code, content=exc.detail)
    return JSONResponse(
        status_code=exc.status_code,
        content={"error": {"code": "HTTP_ERROR"}},
    )


@app.middleware("http")
async def security_headers(request: Request, call_next):
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["Referrer-Policy"] = "no-referrer"
    response.headers["Cache-Control"] = "no-store"
    return response

app.include_router(health.router)
app.include_router(transcribe.router)


@app.get("/")
async def root() -> dict[str, str]:
    return {"service": "shobdo-backend", "version": __version__, "health": "/health"}
