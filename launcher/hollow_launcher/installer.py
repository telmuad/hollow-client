"""Installing and repairing everything in the Hollow game folder.

Order: vanilla Minecraft 1.21.11 (official Mojang servers, including Mojang's Java 21 runtime) -> the newest stable
Fabric Loader (the official Fabric installer) -> Fabric API and Sodium (Modrinth) -> the Hollow Client mod jar.
Nothing from Minecraft is bundled; everything is downloaded from the official sources here.
"""

from __future__ import annotations

import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Protocol

import minecraft_launcher_lib as mll

from . import MC_VERSION, MIN_FABRIC_LOADER
from .jsonfile import read_json, write_json_atomic
from .modrinth import FABRIC_API, SODIUM, WANTED, ModrinthClient, ModrinthError, ModVersion, sha1_of, sha512_of
from .paths import find_mod_jar

MANIFEST_NAME = ".hollow-mods.json"
STATE_NAME = "hollow_install.json"


class Progress(Protocol):
    def status(self, text: str) -> None: ...

    def progress(self, current: int, maximum: int) -> None: ...

    def log(self, text: str) -> None: ...


class PrintProgress:
    """Progress for --install-only: prints to the console."""

    def __init__(self) -> None:
        self._last = -1

    def status(self, text: str) -> None:
        print(f"== {text}", flush=True)

    def progress(self, current: int, maximum: int) -> None:
        if maximum <= 0:
            return
        pct = int(current * 100 / maximum)
        if pct // 10 != self._last // 10:
            print(f"   {pct}%", flush=True)
        self._last = pct

    def log(self, text: str) -> None:
        print(f"   {text}", flush=True)


class InstallError(Exception):
    pass


@dataclass
class UpdateInfo:
    project_id: str
    name: str
    installed: str | None
    latest: str
    needed: bool


def _version_tuple(v: str) -> tuple[int, ...]:
    parts = []
    for p in v.split("."):
        digits = "".join(ch for ch in p if ch.isdigit())
        parts.append(int(digits) if digits else 0)
    return tuple(parts)


def pick_loader(stable_versions: list[str], minimum: str = MIN_FABRIC_LOADER) -> str:
    """Newest stable Fabric Loader that is at least the version the mod was built with."""
    ok = [v for v in stable_versions if _version_tuple(v) >= _version_tuple(minimum)]
    if not ok:
        raise InstallError(f"Fabric has no stable loader {minimum} or newer for Minecraft {MC_VERSION}.")
    return max(ok, key=_version_tuple)


# ------------------------------------------------------------------------------------------------ manifest

def read_manifest(mods: Path) -> dict[str, Any]:
    data, _ = read_json(mods / MANIFEST_NAME)
    if not isinstance(data, dict):
        data = {}
    data.setdefault("projects", {})
    if not isinstance(data["projects"], dict):
        data["projects"] = {}
    return data


def write_manifest(mods: Path, manifest: dict[str, Any]) -> None:
    write_json_atomic(mods / MANIFEST_NAME, manifest)


def installed_version(manifest: dict[str, Any], mods: Path, project_id: str) -> str | None:
    """The version_id installed for a project, if its file is still there."""
    entry = manifest.get("projects", {}).get(project_id)
    if not isinstance(entry, dict):
        return None
    filename = entry.get("filename")
    if not isinstance(filename, str) or not (mods / filename).is_file():
        return None
    v = entry.get("version_id")
    return v if isinstance(v, str) else None


def updates_needed(manifest: dict[str, Any], mods: Path, latest: dict[str, ModVersion]) -> list[UpdateInfo]:
    """Compare what is installed (by Modrinth version id) with the newest versions."""
    out = []
    for pid, v in latest.items():
        installed_id = installed_version(manifest, mods, pid)
        entry = manifest.get("projects", {}).get(pid, {})
        number = entry.get("version_number") if installed_id and isinstance(entry, dict) else None
        out.append(UpdateInfo(pid, WANTED.get(pid, pid), number, v.version_number, installed_id != v.version_id))
    return out


# ------------------------------------------------------------------------------------------------ installer

class Installer:
    def __init__(self, game_dir: Path, progress: Progress, modrinth: ModrinthClient | None = None):
        self.game_dir = game_dir
        self.mods = game_dir / "mods"
        self.p = progress
        self.modrinth = modrinth or ModrinthClient()

    # ---- state

    def state(self) -> dict[str, Any]:
        data, _ = read_json(self.game_dir / STATE_NAME)
        return data if isinstance(data, dict) else {}

    def version_id(self) -> str | None:
        v = self.state().get("version_id")
        return v if isinstance(v, str) else None

    def is_installed(self) -> bool:
        vid = self.version_id()
        if not vid:
            return False
        if not (self.game_dir / "versions" / vid / f"{vid}.json").is_file():
            return False
        manifest = read_manifest(self.mods)
        return all(installed_version(manifest, self.mods, pid) for pid in (FABRIC_API, SODIUM)) and bool(
            manifest.get("hollowclient", {}).get("filename")) and (self.mods / manifest["hollowclient"]["filename"]).is_file()

    def _callback(self) -> dict[str, Callable]:
        maximum = [0]

        def set_max(m: int) -> None:
            maximum[0] = int(m or 0)

        def set_progress(c: int) -> None:
            self.p.progress(int(c or 0), maximum[0])

        return {"setStatus": lambda s: self.p.log(str(s)), "setProgress": set_progress, "setMax": set_max}

    # ---- steps

    def install_all(self) -> str:
        """Install or repair everything. Returns the Fabric version id to launch. Safe to run again (repair)."""
        self.game_dir.mkdir(parents=True, exist_ok=True)
        self.mods.mkdir(parents=True, exist_ok=True)
        try:
            self.p.status(f"Installing Minecraft {MC_VERSION}")
            mll.install.install_minecraft_version(MC_VERSION, str(self.game_dir), callback=self._callback())
            java = self.java_path()
            self.p.log(f"Java runtime: {java}")

            self.p.status("Installing Fabric Loader")
            fabric = mll.mod_loader.get_mod_loader("fabric")
            loader = pick_loader(fabric.get_loader_versions(MC_VERSION, True))
            version_id = fabric.install(MC_VERSION, str(self.game_dir), loader_version=loader, callback=self._callback(), java=java)
            self.p.log(f"Fabric version: {version_id}")
            write_json_atomic(self.game_dir / STATE_NAME, {"mc_version": MC_VERSION, "loader_version": loader, "version_id": version_id})
        except InstallError:
            raise
        except Exception as e:  # minecraft-launcher-lib raises many kinds (network, checksum, subprocess)
            raise InstallError(f"Installing Minecraft/Fabric failed: {e}") from e

        self.install_mods(force=False)
        self.install_hollow_jar()
        self.p.status("Everything is installed")
        return version_id

    def java_path(self) -> str:
        """Mojang's Java runtime for this Minecraft version (installed together with the game)."""
        info = mll.runtime.get_version_runtime_information(MC_VERSION, str(self.game_dir))
        component = info["name"] if info else "java-runtime-delta"
        path = mll.runtime.get_executable_path(component, str(self.game_dir))
        if not path:
            mll.runtime.install_jvm_runtime(component, str(self.game_dir), callback=self._callback())
            path = mll.runtime.get_executable_path(component, str(self.game_dir))
        if not path:
            raise InstallError("Mojang's Java runtime couldn't be installed.")
        return path

    def latest_mods(self) -> dict[str, ModVersion]:
        return self.modrinth.resolve([FABRIC_API, SODIUM], MC_VERSION)

    def check_updates(self) -> list[UpdateInfo]:
        return updates_needed(read_manifest(self.mods), self.mods, self.latest_mods())

    def install_mods(self, force: bool) -> list[str]:
        """Download Fabric API, Sodium (and anything they require) if missing or out of date. Returns what changed."""
        self.p.status("Checking Fabric API and Sodium on Modrinth")
        try:
            latest = self.latest_mods()
        except ModrinthError as e:
            raise InstallError(str(e)) from e
        manifest = read_manifest(self.mods)
        changed = []
        for pid, v in latest.items():
            name = WANTED.get(pid, v.filename)
            current = installed_version(manifest, self.mods, pid)
            target = self.mods / v.filename
            if not force and current == v.version_id and target.is_file() and (not v.sha512 or sha512_of(target) == v.sha512):
                self.p.log(f"{name} {v.version_number} is up to date")
                continue
            self.p.status(f"Downloading {name} {v.version_number}")
            try:
                self.modrinth.download(v, target, self.p.progress)
            except ModrinthError as e:
                raise InstallError(str(e)) from e
            old = manifest["projects"].get(pid, {})
            old_file = old.get("filename") if isinstance(old, dict) else None
            if isinstance(old_file, str) and old_file != v.filename:
                self._remove_mod_file(old_file)
            manifest["projects"][pid] = {
                "name": name, "version_id": v.version_id, "version_number": v.version_number,
                "filename": v.filename, "sha512": v.sha512,
            }
            write_manifest(self.mods, manifest)
            changed.append(f"{name} {v.version_number}")
        return changed

    def _remove_mod_file(self, filename: str) -> None:
        path = (self.mods / filename).resolve()
        if path.parent == self.mods.resolve() and path.is_file():
            path.unlink()

    def install_hollow_jar(self, jar: Path | None = None) -> str:
        """Copy the Hollow Client mod into mods/ (bundled copy first, then mod/build/libs). Old copies are replaced."""
        self.p.status("Installing the Hollow Client mod")
        source = jar or find_mod_jar()
        if source is None:
            raise InstallError("The Hollow Client mod jar wasn't found. Build it first (scripts\\build_all.bat) "
                               "or put hollowclient-<version>.jar in the launcher's bundled folder.")
        manifest = read_manifest(self.mods)
        for old in self.mods.glob("hollowclient-*.jar"):
            if old.name != source.name:
                old.unlink()
        target = self.mods / source.name
        if not target.is_file() or sha1_of(target) != sha1_of(source):
            shutil.copy2(source, target)
        manifest["hollowclient"] = {"filename": source.name, "sha1": sha1_of(target)}
        write_manifest(self.mods, manifest)
        self.p.log(f"Hollow Client: {source.name}")
        return source.name

    def summary(self) -> dict[str, str]:
        """Installed versions for the Home screen."""
        manifest = read_manifest(self.mods)
        st = self.state()
        out = {"Minecraft": MC_VERSION}
        if isinstance(st.get("loader_version"), str):
            out["Fabric"] = st["loader_version"]
        for pid, label in ((FABRIC_API, "Fabric API"), (SODIUM, "Sodium")):
            entry = manifest["projects"].get(pid)
            if isinstance(entry, dict) and installed_version(manifest, self.mods, pid):
                out[label] = str(entry.get("version_number", "?"))
        hollow = manifest.get("hollowclient", {})
        if isinstance(hollow, dict) and isinstance(hollow.get("filename"), str):
            name = hollow["filename"]
            out["Hollow"] = name[len("hollowclient-"):-len(".jar")] if name.startswith("hollowclient-") else name
        return out
