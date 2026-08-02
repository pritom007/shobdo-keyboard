"""Security & abuse-prevention primitives.

Kept deliberately small. Everything here is stateless across restarts so the
service scales horizontally with zero coordination — when we hit 100k users,
swap `InMemoryRateLimiter` for a Redis-backed implementation behind the same
`RateLimiter` protocol (one file changes; the API layer is untouched).
"""

from __future__ import annotations

import time
from hashlib import sha256
from threading import Lock
from typing import Protocol


class RateLimiter(Protocol):
    """Per-device rate limiter.

    Implementations MUST be safe to call from multiple request coroutines /
    threads. `device_id` is opaque to this layer — the caller decides what it
    is (an install-local random ID for v1).
    """

    def check(self, device_id: str) -> bool:
        """Return True if the request is allowed, False if the limit is hit."""
        ...


class InMemoryRateLimiter:
    """Sliding-window per-device rate limiter, in-process.

    State is lost on restart — acceptable for v1 (abuse protection, not
    billing). For multi-instance deployments, replace with a Redis-backed
    limiter implementing the same `RateLimiter` protocol.
    """

    def __init__(self, per_minute: int, max_clients: int = 10_000) -> None:
        self._per_minute = max(1, per_minute)
        self._max_clients = max(100, max_clients)
        self._window = 60.0
        self._hits: dict[str, list[float]] = {}
        self._lock = Lock()

    def check(self, device_id: str) -> bool:
        now = time.monotonic()
        cutoff = now - self._window
        with self._lock:
            # Bound attacker-controlled cardinality and discard inactive keys.
            if len(self._hits) >= self._max_clients and device_id not in self._hits:
                self._hits = {
                    key: hits for key, hits in self._hits.items()
                    if hits and hits[-1] >= cutoff
                }
                if len(self._hits) >= self._max_clients:
                    return False
            bucket = [t for t in self._hits.get(device_id, ()) if t >= cutoff]
            if len(bucket) >= self._per_minute:
                self._hits[device_id] = bucket
                return False
            bucket.append(now)
            self._hits[device_id] = bucket
            return True


def rate_limit_key(client_host: str | None, device_id: str | None) -> str:
    """Return a fixed-size, non-identifying key for abuse prevention.

    The install ID is client supplied, so it is never trusted as authentication.
    Including the source address makes trivial ID rotation less effective, while
    hashing avoids retaining either value in process memory.
    """
    host = (client_host or "unknown").strip()[:255]
    install_id = (device_id or "anonymous").strip()
    if not install_id or len(install_id) > 128 or not install_id.isascii():
        install_id = "anonymous"
    return sha256(f"{host}\0{install_id}".encode("utf-8")).hexdigest()


class ProviderError(Exception):
    """Raised by speech providers. Carries a stable error `code` (mapped to a
    Bengali message on the client) and an HTTP status for the API layer."""

    def __init__(self, code: str, message: str, status: int = 503) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status = status
