"""Finding and downloading mods (Fabric API, Sodium, the Hypixel Mod API) from the official Modrinth API (api.modrinth.com)."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Iterable

import requests

from . import VERSION

API = "https://api.modrinth.com/v2"
USER_AGENT = f"hollow-client-launcher/{VERSION} (github.com/telmuad/hollow-client)"

# Modrinth project ids (they never change, unlike slugs)
FABRIC_API = "P7dR8mSH"
SODIUM = "AANobbMI"
HYPIXEL_MOD_API = "1A2mKfBx"
WANTED = {FABRIC_API: "Fabric API", SODIUM: "Sodium", HYPIXEL_MOD_API: "Hypixel Mod API"}

_TYPE_RANK = {"release": 0, "beta": 1, "alpha": 2}


@dataclass(frozen=True)
class ModVersion:
    project_id: str
    version_id: str
    version_number: str
    version_type: str
    date_published: str
    game_versions: tuple[str, ...]
    loaders: tuple[str, ...]
    filename: str
    url: str
    sha512: str
    sha1: str
    size: int
    required_projects: tuple[str, ...] = field(default=())


def parse_version(data: dict[str, Any]) -> ModVersion | None:
    """One entry of /project/{id}/version. Returns None if it has no usable .jar file."""
    files = data.get("files") or []
    jars = [f for f in files if isinstance(f, dict) and str(f.get("filename", "")).endswith(".jar")]
    if not jars:
        return None
    primary = next((f for f in jars if f.get("primary")), jars[0])
    hashes = primary.get("hashes") or {}
    deps = tuple(
        d["project_id"]
        for d in data.get("dependencies") or []
        if isinstance(d, dict) and d.get("dependency_type") == "required" and d.get("project_id")
    )
    try:
        return ModVersion(
            project_id=str(data["project_id"]),
            version_id=str(data["id"]),
            version_number=str(data.get("version_number", "")),
            version_type=str(data.get("version_type", "release")),
            date_published=str(data.get("date_published", "")),
            game_versions=tuple(data.get("game_versions") or ()),
            loaders=tuple(data.get("loaders") or ()),
            filename=str(primary["filename"]),
            url=str(primary["url"]),
            sha512=str(hashes.get("sha512", "")),
            sha1=str(hashes.get("sha1", "")),
            size=int(primary.get("size") or 0),
            required_projects=deps,
        )
    except (KeyError, TypeError, ValueError):
        return None


def select_version(versions: Iterable[ModVersion], game_version: str, loader: str = "fabric") -> ModVersion | None:
    """The version to install: made for this game version and loader; releases beat betas beat alphas; within the
    best kind, the newest by publish date."""
    candidates = [v for v in versions if game_version in v.game_versions and loader in v.loaders]
    if not candidates:
        return None
    best_rank = min(_TYPE_RANK.get(v.version_type, 3) for v in candidates)
    best = [v for v in candidates if _TYPE_RANK.get(v.version_type, 3) == best_rank]
    return max(best, key=lambda v: v.date_published)


class ModrinthError(Exception):
    pass


class ModrinthClient:
    def __init__(self, session: requests.Session | None = None, timeout: float = 20):
        self.session = session or requests.Session()
        self.session.headers["User-Agent"] = USER_AGENT
        self.timeout = timeout

    def versions(self, project_id: str, game_version: str, loader: str = "fabric") -> list[ModVersion]:
        params = {"loaders": json.dumps([loader]), "game_versions": json.dumps([game_version])}
        try:
            r = self.session.get(f"{API}/project/{project_id}/version", params=params, timeout=self.timeout)
            r.raise_for_status()
            data = r.json()
        except (requests.RequestException, ValueError) as e:
            raise ModrinthError(f"Couldn't reach Modrinth ({e}). Check your internet connection.") from e
        if not isinstance(data, list):
            raise ModrinthError("Modrinth sent something unexpected.")
        return [v for v in (parse_version(d) for d in data if isinstance(d, dict)) if v is not None]

    def resolve(self, project_ids: Iterable[str], game_version: str, loader: str = "fabric") -> dict[str, ModVersion]:
        """Pick a version for each project, plus anything they require (followed a few levels deep)."""
        out: dict[str, ModVersion] = {}
        queue = list(project_ids)
        depth_guard = 0
        while queue and depth_guard < 20:
            depth_guard += 1
            pid = queue.pop(0)
            if pid in out:
                continue
            chosen = select_version(self.versions(pid, game_version, loader), game_version, loader)
            if chosen is None:
                raise ModrinthError(f"Modrinth has no {loader} build of {WANTED.get(pid, pid)} for Minecraft {game_version} yet.")
            out[pid] = chosen
            queue.extend(d for d in chosen.required_projects if d not in out)
        return out

    def download(self, version: ModVersion, target: Path, progress: Callable[[int, int], None] | None = None) -> None:
        """Download to target (via a .part file) and check the sha512 from Modrinth."""
        target.parent.mkdir(parents=True, exist_ok=True)
        part = target.with_name(target.name + ".part")
        h = hashlib.sha512()
        try:
            with self.session.get(version.url, stream=True, timeout=self.timeout) as r:
                r.raise_for_status()
                total = int(r.headers.get("Content-Length") or version.size or 0)
                done = 0
                with open(part, "wb") as f:
                    for chunk in r.iter_content(chunk_size=64 * 1024):
                        if not chunk:
                            continue
                        f.write(chunk)
                        h.update(chunk)
                        done += len(chunk)
                        if progress:
                            progress(done, total)
        except requests.RequestException as e:
            part.unlink(missing_ok=True)
            raise ModrinthError(f"Downloading {version.filename} failed ({e}).") from e
        if version.sha512 and h.hexdigest().lower() != version.sha512.lower():
            part.unlink(missing_ok=True)
            raise ModrinthError(f"{version.filename} didn't match Modrinth's checksum, so it wasn't installed. Try again.")
        part.replace(target)


def sha512_of(path: Path) -> str:
    h = hashlib.sha512()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def sha1_of(path: Path) -> str:
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()
