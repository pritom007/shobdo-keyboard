from __future__ import annotations

from pathlib import Path

import yaml


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]


def test_render_blueprint_deploys_only_backend_with_safe_defaults():
    blueprint = yaml.safe_load((REPOSITORY_ROOT / "render.yaml").read_text())
    services = blueprint["services"]
    assert len(services) == 1

    service = services[0]
    assert service["type"] == "web"
    assert service["runtime"] == "python"
    assert service["rootDir"] == "backend"
    assert service["healthCheckPath"] == "/health"
    assert service["autoDeployTrigger"] == "checksPass"
    assert service["buildFilter"]["paths"] == ["backend/**", "render.yaml"]

    env = {entry["key"]: entry for entry in service["envVars"]}
    assert env["ENVIRONMENT"]["value"] == "production"
    assert env["OPENAI_API_KEY"]["sync"] is False
    assert "value" not in env["OPENAI_API_KEY"]
    assert "SHOHOJAKKHOR_SHARED_SECRET" not in env


def test_render_start_command_uses_platform_port():
    blueprint = yaml.safe_load((REPOSITORY_ROOT / "render.yaml").read_text())
    command = blueprint["services"][0]["startCommand"]
    assert "$PORT" in command
    assert "--host 0.0.0.0" in command
