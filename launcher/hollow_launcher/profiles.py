"""Version profiles: which Minecraft version, which mods, and the instance folder each one plays in.

The definitions live in profiles.json next to this file (the one place to edit for a Minecraft version bump, see
VERSION_BUMP.md). Shared files (Minecraft versions, libraries, assets, Mojang's Java runtimes) stay in the root of
%APPDATA%\\.hollowclient; each profile's game folder (mods, config, saves, options, screenshots, resource packs,
logs) is %APPDATA%\\.hollowclient\\instances\\<id>.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

from . import paths

DEFINITIONS = Path(__file__).resolve().parent / "profiles.json"


@dataclass(frozen=True)
class ModRef:
    project: str
    name: str


@dataclass(frozen=True)
class Profile:
    id: str
    name: str
    tagline: str
    minecraft: str
    min_loader: str
    mods: tuple[ModRef, ...] = field(default=())
    hypixel: bool = False

    @property
    def label(self) -> str:
        return f"{self.name} — {self.tagline}" if self.tagline else self.name

    def mod_names(self) -> dict[str, str]:
        return {m.project: m.name for m in self.mods}

    def instance_dir(self, root: Path) -> Path:
        return instances_dir(root) / self.id


def instances_dir(root: Path) -> Path:
    return root / "instances"


def _definitions_file() -> Path:
    bundled = paths.bundle_dir() / "hollow_launcher" / "profiles.json"
    return bundled if paths.is_frozen() and bundled.is_file() else DEFINITIONS


def load(path: Path | None = None) -> tuple[list[Profile], str]:
    """All profiles and the default profile's id."""
    data = json.loads((path or _definitions_file()).read_text(encoding="utf-8"))
    out = []
    for p in data["profiles"]:
        mods = tuple(ModRef(str(m["project"]), str(m.get("name", m["project"]))) for m in p.get("mods", []))
        out.append(Profile(id=str(p["id"]), name=str(p["name"]), tagline=str(p.get("tagline", "")),
                           minecraft=str(p["minecraft"]), min_loader=str(p.get("min_loader", "0")), mods=mods,
                           hypixel=bool(p.get("hypixel", False))))
    default = str(data.get("default", out[0].id))
    return out, default


def by_id(profiles: list[Profile], pid: str | None, default: str) -> Profile:
    for p in profiles:
        if p.id == pid:
            return p
    for p in profiles:
        if p.id == default:
            return p
    return profiles[0]
