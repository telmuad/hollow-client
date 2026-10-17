"""Where things live: the game folder, the launcher's own files, and the Hollow Client mod jar."""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

GAME_DIR_NAME = ".hollowclient"
JAR_PATTERN = re.compile(r"^hollowclient-(\d+(?:\.\d+)*)(?:[-+][0-9A-Za-z.\-]+)?\.jar$", re.IGNORECASE)
_SKIP_SUFFIXES = ("-sources.jar", "-dev.jar", "-javadoc.jar", "-all.jar")


def game_dir() -> Path:
    """%APPDATA%\\.hollowclient: Hollow's own game folder, separate from the official launcher's .minecraft."""
    appdata = os.environ.get("APPDATA")
    base = Path(appdata) if appdata else Path.home()
    return base / GAME_DIR_NAME


def mods_dir(game: Path | None = None) -> Path:
    return (game or game_dir()) / "mods"


def settings_file(game: Path | None = None) -> Path:
    return (game or game_dir()) / "launcher_settings.json"


def is_frozen() -> bool:
    """True when running as the packaged HollowClient.exe."""
    return bool(getattr(sys, "frozen", False))


def launcher_dir() -> Path:
    """The launcher/ source folder (when running from source)."""
    return Path(__file__).resolve().parent.parent


def bundle_dir() -> Path:
    """Read-only files shipped with the app (PyInstaller unpacks them to sys._MEIPASS)."""
    if is_frozen():
        return Path(getattr(sys, "_MEIPASS", Path(sys.executable).parent))
    return launcher_dir()


def app_dir() -> Path:
    """The folder with HollowClient.exe (packaged) or launcher/ (source). config.json is looked for here."""
    if is_frozen():
        return Path(sys.executable).resolve().parent
    return launcher_dir()


def assets_dir() -> Path:
    return bundle_dir() / "assets"


def jar_search_dirs() -> list[Path]:
    """Where to look for the Hollow Client mod jars: the bundled copies first, then the mod's Gradle output (main
    builds the 26.2 jar, the legacy/1.21.11 worktree under build/legacy the 1.21.11 one)."""
    dirs = [bundle_dir() / "bundled"]
    if is_frozen():
        # launcher/dist/HollowClient/HollowClient.exe -> repo/mod/build/libs (handy when testing a local build)
        exe = Path(sys.executable).resolve()
        repo = exe.parents[3] if len(exe.parents) > 3 else None
    else:
        repo = launcher_dir().parent
    if repo is not None:
        dirs.append(repo / "mod" / "build" / "libs")
        dirs.append(repo / "build" / "legacy" / "mod" / "build" / "libs")
    return dirs


def _version_key(version: str) -> tuple[int, ...]:
    return tuple(int(p) for p in version.split("."))


def jar_minecraft(jar: Path) -> str | None:
    """The Minecraft version a Hollow jar is built for, from depends.minecraft in its fabric.mod.json ("~26.2" -> "26.2")."""
    import json
    import zipfile

    try:
        with zipfile.ZipFile(jar) as z:
            data = json.loads(z.read("fabric.mod.json").decode("utf-8"))
    except (OSError, KeyError, ValueError, zipfile.BadZipFile):
        return None
    dep = data.get("depends", {}).get("minecraft") if isinstance(data, dict) else None
    if isinstance(dep, list):
        dep = dep[0] if dep else None
    if not isinstance(dep, str):
        return None
    return dep.strip().lstrip("~^>=<").strip() or None


def find_mod_jar(search_dirs: list[Path] | None = None, minecraft: str | None = None) -> Path | None:
    """The newest hollowclient-<version>.jar in the first folder that has one (sources/dev jars are ignored).
    With `minecraft`, only jars built for that Minecraft version count (read from the jar's fabric.mod.json)."""
    for d in search_dirs if search_dirs is not None else jar_search_dirs():
        if not d.is_dir():
            continue
        best: tuple[tuple[int, ...], Path] | None = None
        for f in d.iterdir():
            name = f.name
            if not f.is_file() or name.lower().endswith(_SKIP_SUFFIXES):
                continue
            m = JAR_PATTERN.match(name)
            if not m:
                continue
            if minecraft is not None and jar_minecraft(f) != minecraft:
                continue
            key = _version_key(m.group(1))
            if best is None or key > best[0]:
                best = (key, f)
        if best:
            return best[1]
    return None
