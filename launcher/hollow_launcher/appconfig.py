"""config.json: the Azure app (client) ID used for Microsoft login. It is kept out of git (see config.example.json)."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path

from . import paths

PLACEHOLDER_ID = "00000000-0000-0000-0000-000000000000"
DEFAULT_REDIRECT = "http://localhost"
_GUID = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")


@dataclass
class AppConfig:
    azure_client_id: str = ""
    redirect_uri: str = DEFAULT_REDIRECT
    source: Path | None = None
    problem: str | None = None

    @property
    def has_client_id(self) -> bool:
        return bool(_GUID.match(self.azure_client_id)) and self.azure_client_id != PLACEHOLDER_ID

    def status_text(self) -> str:
        if self.problem:
            return self.problem
        if self.has_client_id:
            return f"Azure app ID set ({self.azure_client_id[:8]}...)"
        if self.source is None:
            return "No config.json yet. Copy config.example.json to config.json and put your Azure app ID in it."
        return "config.json still has the placeholder app ID. Put your Azure app (client) ID in it."


def config_candidates() -> list[Path]:
    """config.json next to HollowClient.exe (packaged) or in launcher/ (source)."""
    out = [paths.app_dir() / "config.json"]
    if paths.is_frozen():
        out.append(paths.bundle_dir() / "config.json")
    return out


def load(candidates: list[Path] | None = None) -> AppConfig:
    """Read the first config.json found. Problems are reported in .problem instead of raised."""
    for path in candidates if candidates is not None else config_candidates():
        if not path.is_file():
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8-sig"))
        except (OSError, ValueError) as e:
            return AppConfig(source=path, problem=f"config.json couldn't be read ({e}). Check that it is valid JSON.")
        if not isinstance(data, dict):
            return AppConfig(source=path, problem="config.json should contain a JSON object like config.example.json.")
        client_id = data.get("azure_client_id", "")
        redirect = data.get("redirect_uri", DEFAULT_REDIRECT)
        cfg = AppConfig(
            azure_client_id=client_id.strip() if isinstance(client_id, str) else "",
            redirect_uri=redirect.strip() if isinstance(redirect, str) and redirect.strip() else DEFAULT_REDIRECT,
            source=path,
        )
        if cfg.azure_client_id and not _GUID.match(cfg.azure_client_id):
            cfg.problem = "The azure_client_id in config.json doesn't look like an app ID (it should look like 1a2b3c4d-....)."
        elif not cfg.redirect_uri.startswith("http://localhost") and not cfg.redirect_uri.startswith("http://127.0.0.1"):
            cfg.problem = "redirect_uri in config.json must be http://localhost (with or without a port)."
        return cfg
    return AppConfig()
