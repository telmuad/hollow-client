"""Everything the QML window shows and does, in one object: profiles, install, sign-in, launch, updates, settings
and logs.

Launcher files (settings, sign-in note, skin cache, play stats, the launcher log) live in the root folder,
%APPDATA%\\.hollowclient. The game folder is the selected version profile's instance folder, instances\\<id>.
"""

from __future__ import annotations

import json
import logging
import os
import re
import threading
import time
import zipfile
from logging.handlers import RotatingFileHandler
from pathlib import Path
from typing import Any, Callable

import requests
from PySide6.QtCore import (Property, QAbstractListModel, QByteArray, QModelIndex, QObject, Qt, QTimer, QUrl, Signal,
                            Slot)
from PySide6.QtGui import QGuiApplication

from .. import APP_NAME, VERSION, appconfig, auth, conflicts, launch, migration, paths, profiles
from ..auth import Account, LoginError
from ..installer import InstallError, Installer, read_manifest, sha1_of
from ..jsonfile import read_json, write_json_atomic
from ..modrinth import ModrinthError
from ..settings import LauncherSettings, max_ram_for_slider, total_ram_mb
from ..token_store import TokenStore
from ..workers import GameWatcher, SignalProgress, Task
from . import stats

log = logging.getLogger("hollow")
FRESH_SECONDS = 20 * 60  # re-use a Minecraft token for this long before refreshing again
INSTALL_STEPS = ["Minecraft", "Fabric", "Mods", "Hollow", "Ready"]
MAX_LOG_LINES = 5000


def pretty_version(number: str) -> str:
    """Modrinth version numbers without the Minecraft part: 'mc26.2-0.9.2-fabric' -> '0.9.2',
    '0.161.0+26.2' -> '0.161.0', '1.0.2+build.1+mc26.1' -> '1.0.2+build.1'."""
    n = re.sub(r"^mc[0-9.]+-", "", number)
    n = re.sub(r"-fabric$", "", n)
    return re.sub(r"\+(mc)?[0-9]+(\.[0-9]+)+$", "", n)


def install_step(status: str) -> int:
    """Which install step a status line belongs to (for the animated progress)."""
    s = status.lower()
    if "everything is installed" in s:
        return 4
    if "hollow client mod" in s:
        return 3
    if "on modrinth" in s or "fabric api" in s or "sodium" in s or s.startswith("downloading"):
        return 2
    if "fabric loader" in s:
        return 1
    return 0


class LogModel(QAbstractListModel):
    """The log lines for the Logs page (game output and the launcher's own messages)."""

    TextRole = Qt.ItemDataRole.UserRole + 1
    KindRole = Qt.ItemDataRole.UserRole + 2

    def __init__(self, parent: QObject | None = None):
        super().__init__(parent)
        self.lines: list[tuple[str, str]] = []

    def rowCount(self, parent=QModelIndex()) -> int:  # noqa: N802
        return 0 if parent.isValid() else len(self.lines)

    def data(self, index, role=Qt.ItemDataRole.DisplayRole):
        if not index.isValid() or not (0 <= index.row() < len(self.lines)):
            return None
        text, kind = self.lines[index.row()]
        if role in (Qt.ItemDataRole.DisplayRole, self.TextRole):
            return text
        if role == self.KindRole:
            return kind
        return None

    def roleNames(self):  # noqa: N802
        return {self.TextRole: QByteArray(b"text"), self.KindRole: QByteArray(b"kind")}

    @staticmethod
    def kind_of(text: str) -> str:
        t = text.lower()
        if "/error]" in t or " error" in t or "exception" in t:
            return "error"
        if "/warn]" in t or "warning" in t:
            return "warn"
        if "[hollow]" in t:
            return "hollow"
        return ""

    def append(self, text: str) -> None:
        if len(self.lines) >= MAX_LOG_LINES:
            drop = len(self.lines) - MAX_LOG_LINES + 500
            self.beginRemoveRows(QModelIndex(), 0, drop - 1)
            del self.lines[:drop]
            self.endRemoveRows()
        self.beginInsertRows(QModelIndex(), len(self.lines), len(self.lines))
        self.lines.append((text, self.kind_of(text)))
        self.endInsertRows()

    def clear(self) -> None:
        self.beginResetModel()
        self.lines = []
        self.endResetModel()

    def text(self) -> str:
        return "\n".join(t for t, _ in self.lines)


class _LogBridge(QObject):
    line = Signal(str)


class _QtLogHandler(logging.Handler):
    def __init__(self, bridge: _LogBridge):
        super().__init__()
        self.bridge = bridge
        self.setFormatter(logging.Formatter("[%(asctime)s] [Hollow] %(message)s", "%H:%M:%S"))

    def emit(self, record: logging.LogRecord) -> None:
        self.bridge.line.emit(self.format(record))


def _file_url(path: Path | None) -> str:
    return QUrl.fromLocalFile(str(path)).toString() if path else ""


class Backend(QObject):
    changed = Signal()
    settingsChanged = Signal()
    statsChanged = Signal()
    dialog = Signal(str, str)  # title, text
    launched = Signal()

    def __init__(self, network: bool = True, store: TokenStore | None = None, parent: QObject | None = None):
        super().__init__(parent)
        self.network = network
        self.root = paths.game_dir()
        self.settings = LauncherSettings.load(paths.settings_file(self.root))
        self.config = appconfig.load()
        self.store = store or TokenStore()
        self.account: Account | None = None
        public, _ = read_json(self.root / "account.json")
        self.account_public: dict[str, Any] = public if isinstance(public, dict) else {}
        self.sig_progress = SignalProgress()
        self.profiles, self.default_profile = profiles.load()
        self.profile = profiles.by_id(self.profiles, self.settings.profile, self.default_profile)
        self.logs = LogModel(self)
        self._setup_logging()
        if network:  # self-test and screenshot runs must not change the real folder
            self._migrate()
        self.installer = Installer(self.root, self.profile, self.sig_progress)
        self._server: str | None = None
        self._conflicts: list[str] = []
        self.tasks: list[Task] = []
        self.busy = False
        self.game = None
        self.game_started_at = 0.0
        self.watcher: GameWatcher | None = None
        self.login_cancel: threading.Event | None = None
        self.updates: list = []

        self._status = ""
        self._status_kind = ""
        self._progress = -1.0
        self._step = 0
        self._banner = ("", "", "")
        self._account_note = ""
        self._update_status = "Not checked yet."
        self._mod_rows: list[dict[str, str]] = []
        self._logging_in = False
        self._skin_rev = 0
        self._stats: dict[str, Any] = {}
        self._counts: dict[str, int] = {}

        self.sig_progress.status_changed.connect(self._on_status)
        self.sig_progress.progress_changed.connect(self._on_progress)
        self.sig_progress.logged.connect(lambda t: log.info(t))
        self.refresh()
        self.refreshStats()
        if network:
            QTimer.singleShot(400, self._startup)

    @property
    def game_dir(self) -> Path:
        """The selected profile's game folder (mods, config, saves, options, screenshots, ...)."""
        return self.profile.instance_dir(self.root)

    def skyblock_instance(self) -> Path | None:
        """The Hypixel-ready profile's game folder (where the mod writes its SkyBlock session totals)."""
        p = next((q for q in self.profiles if q.hypixel), None)
        return p.instance_dir(self.root) if p else None

    def theme_files(self) -> list[Path]:
        """The mod's theme file in every profile, the selected profile first (the launcher keeps them in sync)."""
        others = [p for p in self.profiles if p.id != self.profile.id]
        return [p.instance_dir(self.root) / "config" / "hollowclient" / "theme.json" for p in [self.profile, *others]]

    # ------------------------------------------------------------------------------------------------ plumbing

    def _setup_logging(self) -> None:
        log.setLevel(logging.INFO)
        for h in list(log.handlers):
            log.removeHandler(h)
        try:
            self.root.mkdir(parents=True, exist_ok=True)
            fh = RotatingFileHandler(self.root / "launcher.log", maxBytes=1_000_000, backupCount=2, encoding="utf-8")
            fh.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
            log.addHandler(fh)
        except OSError:
            pass
        self._bridge = _LogBridge()
        self._bridge.line.connect(self.logs.append)
        log.addHandler(_QtLogHandler(self._bridge))
        log.info(f"{APP_NAME} launcher {VERSION} - folder: {self.root}, profile: {self.profile.id}")

    def _migrate(self) -> None:
        """Launcher 1.x kept the 1.21.11 game in the root folder; move it into its instance folder once."""
        try:
            moved = migration.migrate(self.root)
        except OSError as e:
            log.warning(f"Moving the old install into instances/ failed: {e}")
            return
        if moved:
            log.info(f"Your 1.21.11 game folder moved to instances/{migration.LEGACY_ID} ({len(moved)} items)")

    def _spawn(self, fn: Callable[[SignalProgress], Any], ok: Callable[[Any], None], fail: Callable[[Exception], None],
               progress: SignalProgress | None = None) -> Task:
        task = Task(fn, progress or SignalProgress())
        task.succeeded.connect(ok)
        task.failed.connect(fail)
        task.finished.connect(lambda: self.tasks.remove(task) if task in self.tasks else None)
        self.tasks.append(task)
        task.start()
        return task

    def set_status(self, text: str, kind: str = "") -> None:
        self._status = text
        self._status_kind = kind
        self.changed.emit()

    def _on_status(self, text: str) -> None:
        self._step = install_step(text)
        self.set_status(text)

    def _on_progress(self, current: int, maximum: int) -> None:
        self._progress = -1.0 if maximum <= 0 else max(0.0, min(1.0, current / maximum))
        self.changed.emit()

    def _set_busy(self, busy: bool, text: str = "") -> None:
        self.busy = busy
        if busy:
            self._progress = -1.0
            self._step = 0
            if text:
                self._status = text
                self._status_kind = ""
        self.changed.emit()

    def _can_login(self) -> bool:
        return self.config.has_client_id and not self.config.problem

    def _open(self, path: Path) -> None:
        try:
            path.mkdir(parents=True, exist_ok=True)
            os.startfile(str(path))  # type: ignore[attr-defined]
        except (OSError, AttributeError) as e:
            log.warning(f"Couldn't open {path}: {e}")

    # ------------------------------------------------------------------------------------------------ properties

    status = Property(str, lambda self: self._status, notify=changed)
    statusKind = Property(str, lambda self: self._status_kind, notify=changed)
    isBusy = Property(bool, lambda self: self.busy, notify=changed)
    progress = Property(float, lambda self: self._progress, notify=changed)
    installStep = Property(int, lambda self: self._step, notify=changed)
    installSteps = Property(list, lambda self: INSTALL_STEPS, constant=True)
    installed = Property(bool, lambda self: self.installer.is_installed(), notify=changed)
    gameRunning = Property(bool, lambda self: self.game is not None, notify=changed)
    canPlay = Property(bool, lambda self: not self.busy and self.game is None, notify=changed)
    loggingIn = Property(bool, lambda self: self._logging_in, notify=changed)
    bannerText = Property(str, lambda self: self._banner[0], notify=changed)
    bannerKind = Property(str, lambda self: self._banner[1], notify=changed)
    bannerAction = Property(str, lambda self: self._banner[2], notify=changed)
    updateStatus = Property(str, lambda self: self._update_status, notify=changed)
    updatesAvailable = Property(bool, lambda self: any(u.needed for u in self.updates) or any(
        r["state"] == "update" for r in self._mod_rows), notify=changed)
    modRows = Property(list, lambda self: self._mod_rows, notify=changed)
    launcherVersion = Property(str, lambda self: VERSION, constant=True)
    mcVersion = Property(str, lambda self: self.profile.minecraft, notify=changed)
    profileId = Property(str, lambda self: self.profile.id, notify=changed)
    profileName = Property(str, lambda self: self.profile.name, notify=changed)
    profileTagline = Property(str, lambda self: self.profile.tagline, notify=changed)
    profileHypixel = Property(bool, lambda self: self.profile.hypixel, notify=changed)
    hasHypixelProfile = Property(bool, lambda self: any(p.hypixel for p in self.profiles), constant=True)
    conflictText = Property(str, lambda self: conflicts.message(self._conflicts), notify=changed)

    def _get_profile_list(self) -> list[dict[str, Any]]:
        out = []
        for p in self.profiles:
            inst = self.installer if p.id == self.profile.id else Installer(self.root, p, self.sig_progress)
            out.append({"id": p.id, "name": p.name, "tagline": p.tagline, "minecraft": p.minecraft,
                        "hypixel": p.hypixel, "installed": inst.is_installed(), "label": p.label})
        return out

    profileList = Property(list, _get_profile_list, notify=changed)
    canLogin = Property(bool, lambda self: self._can_login(), notify=changed)
    configStatus = Property(str, lambda self: self.config.status_text() + (
        f" (looked in {paths.app_dir()})" if self.config.source is None else ""), notify=changed)
    logModel = Property(QObject, lambda self: self.logs, constant=True)

    def _get_play_sub(self) -> str:
        if self.game is not None:
            return "Minecraft is running"
        return "" if self.installer.is_installed() else f"Installs Minecraft {self.profile.minecraft} first (about 700 MB)"

    playSub = Property(str, _get_play_sub, notify=changed)

    def _get_versions(self) -> list[dict[str, str]]:
        summary = self.installer.summary() if self.installer.is_installed() else {}
        rows = summary or {"Minecraft": "not installed yet"}
        return [{"name": k, "value": pretty_version(v)} for k, v in rows.items()]

    versions = Property(list, _get_versions, notify=changed)

    def _get_account_name(self) -> str:
        name = self.account.name if self.account else self.account_public.get("name")
        return name if isinstance(name, str) else ""

    accountName = Property(str, _get_account_name, notify=changed)
    signedIn = Property(bool, lambda self: self.account is not None, notify=changed)
    accountNote = Property(str, lambda self: self._account_note, notify=changed)

    def _get_account_text(self) -> str:
        name = self._get_account_name()
        if self.account:
            return f"Signed in as {self.account.name}. Your Microsoft sign-in is stored safely in Windows Credential Manager."
        if not self._can_login():
            return "Login isn't set up yet: " + self.config.status_text()
        if name:
            return f"{name}: you'll be signed in again when you press PLAY."
        return "Not signed in. Press PLAY or the account button to sign in with Microsoft."

    accountText = Property(str, _get_account_text, notify=changed)

    def _skin_file(self) -> Path:
        return self.root / "cache" / "skin.png"

    def _get_skin_url(self) -> str:
        f = self._skin_file()
        return _file_url(f) + f"?v={self._skin_rev}" if f.is_file() else ""

    skinUrl = Property(str, _get_skin_url, notify=changed)
    skinPath = Property(str, lambda self: str(self._skin_file()) if self._skin_file().is_file() else "", notify=changed)

    # ---- cosmetics from the mod's config, for the skin viewer

    def _mod_config(self) -> dict[str, Any]:
        try:
            data = json.loads((self.game_dir / "config" / "hollowclient.json").read_text(encoding="utf-8"))
            return data if isinstance(data, dict) else {}
        except (OSError, ValueError):
            return {}

    def _module(self, cfg: dict[str, Any], mid: str) -> dict[str, Any]:
        mods = cfg.get("modules", {})
        m = mods.get(mid) if isinstance(mods, dict) else None
        return m if isinstance(m, dict) else {}

    def _jar_texture(self, inner: str) -> str:
        """Extract a texture from the mod jar into the launcher cache (built-in capes and wings live there)."""
        jar = paths.find_mod_jar(minecraft=self.profile.minecraft) or paths.find_mod_jar()
        if jar is None:
            return ""
        target = self.root / "cache" / "textures" / inner.replace("/", "_")
        try:
            if not target.is_file() or target.stat().st_mtime < jar.stat().st_mtime:
                with zipfile.ZipFile(jar) as z:
                    data = z.read("assets/hollowclient/textures/" + inner)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
            return str(target)
        except (OSError, KeyError, zipfile.BadZipFile):
            return ""

    def _get_cosmetics(self) -> dict[str, str]:
        cfg = self._mod_config()
        out = {"cape": "", "wings": "", "halo": ""}
        cape = self._module(cfg, "cape")
        if cape.get("enabled"):
            cid = cape.get("settings", {}).get("cape", "builtin:hollow") if isinstance(cape.get("settings"), dict) else "builtin:hollow"
            if isinstance(cid, str) and cid.startswith("builtin:"):
                name = cid[len("builtin:"):]
                out["cape"] = self._jar_texture(f"capes/{name}.png") if name != "void_flow" else self._jar_texture("capes/nightfall.png")
            elif isinstance(cid, str) and cid.startswith("custom:"):
                capes = (self.game_dir / "config" / "hollowclient" / "capes").resolve()
                f = (capes / cid[len("custom:"):]).resolve()
                if f.is_relative_to(capes) and f.is_file() and f.suffix.lower() == ".png":
                    out["cape"] = str(f)
        wings = self._module(cfg, "wings")
        if wings.get("enabled"):
            style = wings.get("settings", {}).get("style", "SHADOW") if isinstance(wings.get("settings"), dict) else "SHADOW"
            out["wings"] = self._jar_texture(f"cosmetics/wings_{str(style).lower()}.png")
        halo = self._module(cfg, "halo")
        if halo.get("enabled"):
            s = halo.get("settings", {}) if isinstance(halo.get("settings"), dict) else {}
            out["halo"] = "theme" if s.get("themeColor", True) else str(s.get("tint", "#FFFFE27A"))
        return out

    cosmetics = Property("QVariantMap", _get_cosmetics, notify=changed)

    # ---- settings

    ramMb = Property(int, lambda self: self.settings.ram_mb, notify=settingsChanged)
    ramMax = Property(int, lambda self: max_ram_for_slider(total_ram_mb()), constant=True)
    totalRam = Property(int, lambda self: total_ram_mb(), constant=True)
    jvmArgs = Property(str, lambda self: self.settings.jvm_args, notify=settingsChanged)
    resWidth = Property(int, lambda self: self.settings.resolution_width, notify=settingsChanged)
    resHeight = Property(int, lambda self: self.settings.resolution_height, notify=settingsChanged)
    closeOnLaunch = Property(bool, lambda self: self.settings.close_on_launch, notify=settingsChanged)
    animatedBackground = Property(bool, lambda self: self.settings.animated_background, notify=settingsChanged)
    uiSounds = Property(bool, lambda self: self.settings.ui_sounds, notify=settingsChanged)
    showIntro = Property(bool, lambda self: self.settings.show_intro, notify=settingsChanged)

    def _save_settings(self) -> None:
        self.settings.sanitize()
        try:
            self.settings.save(paths.settings_file(self.root))
        except OSError as e:
            log.warning(f"Settings couldn't be saved: {e}")
        self.settingsChanged.emit()

    @Slot(int)
    def setRam(self, mb: int) -> None:  # noqa: N802
        self.settings.ram_mb = int(mb)
        self._save_settings()

    @Slot(str)
    def setJvmArgs(self, text: str) -> None:  # noqa: N802
        self.settings.jvm_args = text
        self._save_settings()

    @Slot(int, int)
    def setResolution(self, w: int, h: int) -> None:  # noqa: N802
        self.settings.resolution_width, self.settings.resolution_height = int(w), int(h)
        self._save_settings()

    @Slot(bool)
    def setCloseOnLaunch(self, value: bool) -> None:  # noqa: N802
        self.settings.close_on_launch = bool(value)
        self._save_settings()

    @Slot(str, bool)
    def setLauncherOption(self, name: str, value: bool) -> None:  # noqa: N802
        """Window options: animated_background, ui_sounds, show_intro."""
        if name in ("animated_background", "ui_sounds", "show_intro"):
            setattr(self.settings, name, bool(value))
            self._save_settings()

    # ---- stats

    statsData = Property("QVariantMap", lambda self: self._stats, notify=statsChanged)
    folderCounts = Property("QVariantMap", lambda self: self._counts, notify=statsChanged)

    @Slot()
    def refreshStats(self) -> None:  # noqa: N802
        self._stats = stats.summarize(stats.load_sessions(self.root))
        self._counts = stats.folder_counts(self.game_dir)
        self.statsChanged.emit()

    # ------------------------------------------------------------------------------------------------ profiles

    @Slot(str)
    def selectProfile(self, pid: str) -> None:  # noqa: N802
        """Switch the version profile (not while installing or playing)."""
        if self.busy or self.game is not None or pid == self.profile.id:
            return
        p = next((q for q in self.profiles if q.id == pid), None)
        if p is None:
            return
        self.profile = p
        self.installer = Installer(self.root, p, self.sig_progress)
        self.updates = []
        self._update_status = "Not checked yet."
        self._banner = ("", "", "")
        self.settings.profile = p.id
        self._save_settings()
        log.info(f"Profile: {p.label} (Minecraft {p.minecraft})")
        self.refresh()
        self.refreshStats()
        if self.network and self.installer.is_installed():
            self.checkUpdates()

    # ------------------------------------------------------------------------------------------------ refresh

    @Slot()
    def refresh(self) -> None:
        if not self.busy and self.game is None:
            if self.installer.is_installed():
                self._status = "Ready to play." if self._can_login() else "Installed. Microsoft login isn't set up yet, see Settings."
            else:
                self._status = (f"Not installed yet. Press PLAY to download Minecraft {self.profile.minecraft}, Fabric, "
                                f"{', '.join(m.name for m in self.profile.mods)} and Hollow Client.")
            self._status_kind = ""
        self._refresh_mod_rows()
        self._conflicts = conflicts.find(self.game_dir / "mods")
        self.changed.emit()

    def _refresh_mod_rows(self) -> None:
        manifest = read_manifest(self.game_dir / "mods")
        latest = {u.project_id: u for u in self.updates}
        rows = []
        for pid, name in ((m.project, m.name) for m in self.profile.mods):
            entry = manifest["projects"].get(pid) if isinstance(manifest.get("projects"), dict) else None
            present = isinstance(entry, dict) and (self.game_dir / "mods" / str(entry.get("filename", ""))).is_file()
            installed = str(entry.get("version_number", "")) if present else ""
            u = latest.get(pid)
            state = "missing" if not present else ("update" if u and u.needed else ("ok" if u else "unknown"))
            rows.append({"name": name, "installed": pretty_version(installed), "latest": pretty_version(u.latest) if u else "", "state": state})
        hollow = manifest.get("hollowclient", {})
        hollow_name = hollow.get("filename", "") if isinstance(hollow, dict) else ""
        bundled = paths.find_mod_jar(minecraft=self.profile.minecraft)
        hv = hollow_name[len("hollowclient-"):-4] if hollow_name.startswith("hollowclient-") else ""
        bv = bundled.name[len("hollowclient-"):-4] if bundled else ""
        state = "missing" if not hv else ("update" if self._hollow_outdated() else "ok")
        rows.append({"name": "Hollow Client", "installed": hv, "latest": bv or hv, "state": state})
        self._mod_rows = rows

    def _hollow_outdated(self) -> bool:
        """True when the installed Hollow Client jar isn't the one this launcher carries (compared by content,
        so a rebuilt jar with the same version number still counts as new)."""
        bundled = paths.find_mod_jar(minecraft=self.profile.minecraft)
        if bundled is None:
            return False
        manifest = read_manifest(self.game_dir / "mods")
        entry = manifest.get("hollowclient") if isinstance(manifest.get("hollowclient"), dict) else {}
        installed = self.game_dir / "mods" / str(entry.get("filename", ""))
        if not entry or not installed.is_file():
            return True
        try:
            return sha1_of(installed) != sha1_of(bundled)
        except OSError:
            return False

    def _startup(self) -> None:
        token = self.store.load_refresh_token() if self._can_login() else None
        if token:
            self._refresh_account(token, then=None, silent=True)
        if self.installer.is_installed():
            self.checkUpdates()

    @Slot()
    def checkUpdates(self) -> None:  # noqa: N802
        self._update_status = "Checking Modrinth..."
        self.changed.emit()
        pid = self.profile.id

        def done(updates) -> None:
            if pid != self.profile.id:
                return  # the profile changed while this was checking
            self.updates = updates
            needed = [u for u in updates if u.needed]
            self._update_status = f"Checked {time.strftime('%H:%M')}. " + (
                f"{len(needed)} update(s) available." if needed else "Everything is up to date.")
            self._refresh_mod_rows()
            if needed and self.installer.is_installed():
                names = ", ".join(f"{u.name} {pretty_version(u.latest)}" for u in needed)
                self._banner = (f"Updates available: {names}", "info", "Update now")
            self.changed.emit()

        def failed(e: Exception) -> None:
            if pid != self.profile.id:
                return
            self._update_status = str(e) if isinstance(e, ModrinthError) else f"Update check failed: {e}"
            self.changed.emit()

        self._spawn(lambda p: self.installer.check_updates(), done, failed)

    @Slot()
    def dismissBanner(self) -> None:  # noqa: N802
        self._banner = ("", "", "")
        self.changed.emit()

    @Slot()
    def bannerClicked(self) -> None:  # noqa: N802
        if self._banner[2] == "Update now":
            self.updateMods()

    # ------------------------------------------------------------------------------------------------ PLAY

    @Slot()
    def play(self) -> None:
        self._play(None)

    @Slot()
    def joinHypixel(self) -> None:  # noqa: N802
        """Start the Hypixel-ready profile and connect to Hypixel from the title screen. Only the connection is
        automatic; once there, the player does everything (including typing /skyblock)."""
        if self.busy or self.game is not None:
            return
        target = next((p for p in self.profiles if p.hypixel), None)
        if target is None:
            return
        if target.id != self.profile.id:
            self.selectProfile(target.id)
        self._play(launch.HYPIXEL)

    def _play(self, server: str | None) -> None:
        if self.busy or self.game is not None:
            return
        self._server = server
        self._banner = ("", "", "")
        if not self.installer.is_installed():
            self._install(then=self._ensure_account)
        elif self._hollow_outdated():
            self._update_hollow(then=self._ensure_account)
        else:
            self._ensure_account()

    def _update_hollow(self, then: Callable[[], None] | None) -> None:
        """Put this launcher's Hollow Client jar into mods/ (a quick copy) before playing."""
        self._set_busy(True, "Updating Hollow Client...")

        def ok(name) -> None:
            self._set_busy(False)
            self.refresh()
            log.info(f"Hollow Client updated ({name})")
            if then:
                then()

        def fail(e: Exception) -> None:
            self._set_busy(False)
            self.set_status(str(e) if isinstance(e, InstallError) else f"Couldn't update Hollow Client: {e}", "error")

        self._spawn(lambda p: self.installer.install_hollow_jar(), ok, fail, self.sig_progress)

    def _install(self, then: Callable[[], None] | None, repair: bool = False) -> None:
        self._set_busy(True, "Repairing..." if repair else "Installing...")
        log.info("Repair started" if repair else "Install started")

        def ok(version_id) -> None:
            self._set_busy(False)
            self._step = 4
            self.refresh()
            self.set_status("Everything is installed." if not repair else "Repair finished. Everything is installed.", "ok")
            self.checkUpdates()
            if then:
                then()

        def fail(e: Exception) -> None:
            self._set_busy(False)
            self.refresh()
            msg = str(e) if isinstance(e, InstallError) else f"Install failed: {e}"
            self.set_status(msg, "error")
            log.error(msg)

        self._spawn(lambda p: self.installer.install_all(), ok, fail, self.sig_progress)

    @Slot()
    def repair(self) -> None:
        if not self.busy:
            self._install(then=None, repair=True)

    @Slot()
    def updateMods(self) -> None:  # noqa: N802
        if self.busy:
            return
        self._banner = ("", "", "")
        self._set_busy(True, "Updating mods...")

        def ok(changed) -> None:
            self._set_busy(False)
            self.updates = []
            self.refresh()
            self.set_status("Updated " + ", ".join(changed) + "." if changed else "Mods were already up to date.", "ok")
            self.checkUpdates()

        def fail(e: Exception) -> None:
            self._set_busy(False)
            self.set_status(str(e), "error")

        def work(p):
            changed = self.installer.install_mods(force=False)
            if self._hollow_outdated():
                self.installer.install_hollow_jar()
                changed = list(changed) + ["Hollow Client"]
            return changed

        self._spawn(work, ok, fail, self.sig_progress)

    def _ensure_account(self) -> None:
        if not self._can_login():
            self.set_status("Microsoft login isn't set up yet. " + self.config.status_text(), "error")
            self.showConfigHelp()
            return
        if self.account and time.time() - self.account.obtained_at < FRESH_SECONDS:
            self._launch()
            return
        token = self.account.refresh_token if self.account else self.store.load_refresh_token()
        if token:
            self._refresh_account(token, then=self._launch, silent=False)
        else:
            self._login(then=self._launch)

    def _refresh_account(self, token: str, then: Callable[[], None] | None, silent: bool) -> None:
        if not silent:
            self._set_busy(True, "Signing in...")

        def ok(account: Account) -> None:
            if not silent:
                self._set_busy(False)
            self._signed_in(account)
            if then:
                then()

        def fail(e: Exception) -> None:
            if not silent:
                self._set_busy(False)
            if isinstance(e, LoginError) and e.kind == "expired":
                self.store.clear()
                if then:
                    self._login(then=then)
                return
            self.login_failed(e, silent)

        self._spawn(lambda p: auth.refresh(self.config, token), ok, fail)

    @Slot()
    def login(self) -> None:
        self._login(then=None)

    def _login(self, then: Callable[[], None] | None) -> None:
        if not self._can_login():
            self.showConfigHelp()
            return
        self.login_cancel = threading.Event()
        cancel = self.login_cancel
        self._logging_in = True
        self._set_busy(True, "Opening the Microsoft sign-in page in your browser... Finish signing in there.")
        log.info("Opened the Microsoft sign-in page in the browser")

        def ok(account: Account) -> None:
            self._logging_in = False
            self._set_busy(False)
            self._signed_in(account)
            self.set_status(f"Signed in as {account.name}.", "ok")
            if then:
                then()

        def fail(e: Exception) -> None:
            self._logging_in = False
            self._set_busy(False)
            self.login_failed(e, silent=False)

        self._spawn(lambda p: auth.login(self.config, cancelled=cancel), ok, fail)

    @Slot()
    def cancelLogin(self) -> None:  # noqa: N802
        if self.login_cancel:
            self.login_cancel.set()

    def _signed_in(self, account: Account) -> None:
        self.account = account
        self.account_public = account.public()
        self._account_note = ""
        try:
            self.store.save_refresh_token(account.refresh_token)
        except Exception as e:
            log.warning(f"Couldn't save the sign-in to Credential Manager: {e}")
        try:
            write_json_atomic(self.root / "account.json", account.public())
        except OSError:
            pass
        log.info(f"Signed in as {account.name}")
        self.changed.emit()
        if account.skin_url and self.network:
            self._spawn(lambda p: _download_skin(account.skin_url, self._skin_file()), self._skin_ready, lambda e: None)

    def _skin_ready(self, data: bytes | None) -> None:
        if data:
            self._skin_rev += 1
            self.changed.emit()

    def login_failed(self, e: Exception, silent: bool) -> None:
        if isinstance(e, LoginError):
            log.warning(f"Login: {e.kind}: {e.message}" + (f" ({e.detail[:300]})" if e.detail else ""))
            if e.kind == "cancelled":
                self.set_status("Sign-in cancelled.", "")
                return
            if e.kind == "not_approved":
                self._banner = (auth.NOT_APPROVED_MESSAGE, "warn", "")
                self._account_note = "not approved yet"
                self.set_status("Mojang hasn't approved the app ID yet (see the note above). Install and updates still work.", "")
                if not silent:
                    self.dialog.emit(APP_NAME, auth.NOT_APPROVED_MESSAGE)
                return
            if not silent:
                self.set_status(e.message, "error")
            self.changed.emit()
            return
        log.error(f"Login failed: {e}")
        if not silent:
            self.set_status(f"Sign-in failed: {e}", "error")

    @Slot()
    def showConfigHelp(self) -> None:  # noqa: N802
        self.dialog.emit(
            "Microsoft sign-in",
            "Sign-in needs your Azure app (client) ID.\n\n"
            "1. Copy config.example.json to config.json (next to the launcher).\n"
            "2. Replace the zeros with your app ID.\n"
            "3. Restart the launcher.\n\n" + self.config.status_text())

    @Slot()
    def logout(self) -> None:
        self.store.clear()
        self.account = None
        self.account_public = {}
        self._account_note = ""
        for f in (self.root / "account.json", self._skin_file()):
            try:
                f.unlink(missing_ok=True)
            except OSError:
                pass
        log.info("Logged out; the stored sign-in was removed")
        self.refresh()
        self.set_status("Logged out.", "")

    def _launch(self) -> None:
        if self.account is None:
            return
        version_id = self.installer.version_id()
        if not version_id:
            self.set_status("The installation is incomplete. Press Repair.", "error")
            return
        server, self._server = self._server, None
        try:
            self.game_dir.mkdir(parents=True, exist_ok=True)
            self.installer.seed_settings([p.instance_dir(self.root) for p in self.profiles if p.id != self.profile.id])
            options = launch.build_options(self.account, self.settings, self.game_dir, server)
            command = launch.build_command(version_id, self.root, options)
        except Exception as e:
            self.set_status(f"Couldn't build the game command: {e}. Try Repair.", "error")
            log.error(f"Building the command failed: {e}")
            return
        log.info("Starting: " + " ".join(launch.redact(command, [self.account.access_token])))
        capture = not self.settings.close_on_launch
        try:
            self.game = launch.start(command, self.game_dir, capture_output=capture)
        except OSError as e:
            self.set_status(f"Minecraft couldn't start: {e}", "error")
            log.error(f"Start failed: {e}")
            return
        self.game_started_at = stats.now()
        self.set_status(f"Minecraft is starting{' and joining ' + server if server else ''}... it opens in its own window.", "ok")
        self.launched.emit()
        if capture:
            self.watcher = GameWatcher(self.game)
            self.watcher.line.connect(self.logs.append)
            self.watcher.exited.connect(self._game_exited)
            self.watcher.start()
        else:
            stats.record_session(self.root, self.game_started_at, None, self.profile.id)
            QTimer.singleShot(4000, self._close_after_launch)

    def _close_after_launch(self) -> None:
        if self.game is not None and self.game.poll() is None:
            log.info("Game started; closing the launcher as set in Settings")
            QGuiApplication.quit()
        else:
            code = self.game.poll() if self.game else None
            self.game = None
            self.refresh()
            self.set_status(f"Minecraft closed right away (code {code}). Turn off \"Close the launcher\" to see the log.", "error")

    def _game_exited(self, code: int) -> None:
        log.info(f"Minecraft closed (exit code {code})")
        if self.game_started_at:
            stats.record_session(self.root, self.game_started_at, max(0.0, stats.now() - self.game_started_at), self.profile.id)
            self.game_started_at = 0.0
            self.refreshStats()
        self.game = None
        self.watcher = None
        self.refresh()
        if code == 0:
            self.set_status("Minecraft closed. Ready to play again.", "")
        else:
            self.set_status(f"Minecraft closed with an error (code {code}). The Logs page has the details.", "error")

    # ------------------------------------------------------------------------------------------------ folders and logs

    @Slot(str)
    def openFolder(self, which: str) -> None:  # noqa: N802
        hollow = self.game_dir / "config" / "hollowclient"
        targets = {"game": self.game_dir, "mods": self.game_dir / "mods", "packs": self.game_dir / "resourcepacks",
                   "capes": hollow / "capes", "music": hollow / "music", "screenshots": self.game_dir / "screenshots",
                   "logs": self.game_dir / "logs", "config": paths.app_dir(), "root": self.root}
        if which in targets:
            self._open(targets[which])

    @Slot()
    def copyLogs(self) -> None:  # noqa: N802
        QGuiApplication.clipboard().setText(self.logs.text())
        log.info("Logs copied to the clipboard")

    @Slot()
    def clearLogs(self) -> None:  # noqa: N802
        self.logs.clear()


def _download_skin(url: str, target: Path) -> bytes | None:
    """Your skin from Mojang's texture server, for the avatar and the 3D viewer."""
    if not url.startswith(("http://textures.minecraft.net/", "https://textures.minecraft.net/")):
        return None
    r = requests.get(url.replace("http://", "https://", 1), timeout=10)
    r.raise_for_status()
    data = r.content[:2_000_000]
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return data
