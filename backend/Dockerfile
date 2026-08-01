# Shobdo Keyboard backend — speech-to-text proxy.
# Multi-stage, slim, non-root. Stateless and horizontally scalable.
# Build:  docker build -t shobdo-backend ./backend
# Run:    docker run -p 8000:8000 --env-file backend/.env shobdo-backend

# --- build -------------------------------------------------------------------
FROM python:3.11-slim AS build
ENV PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PYTHONDONTWRITEBYTECODE=1
WORKDIR /build

# Install build deps for any C-extension wheels (uvicorn[standard], etc.).
RUN apt-get update && apt-get install -y --no-install-recommends build-essential && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml ./
COPY app ./app
RUN python -m venv /opt/venv \
    && /opt/venv/bin/pip install --upgrade pip \
    && /opt/venv/bin/pip install .

# --- runtime -----------------------------------------------------------------
FROM python:3.11-slim AS runtime
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/opt/venv/bin:$PATH"

# Non-root user for defense-in-depth.
RUN groupadd --system shobdo && useradd --system --gid shobdo --home /app shobdo
WORKDIR /app
COPY --from=build /opt/venv /opt/venv
COPY app ./app

USER shobdo
EXPOSE 8000

# Stateless: any number of replicas behind a load balancer is safe.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
