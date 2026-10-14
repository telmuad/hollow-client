"""The launcher window: sidebar, pages, and the PLAY flow (install if needed -> sign in -> start the game)."""

from __future__ import annotations

import logging
import os
import threading
import time
from logging.handlers import RotatingFileHandler
from pathlib import Path
from typing import Any, Callable

import requests
from PySide6.QtCore import QObject, Qt, QTimer, Signal
from PySide6.QtGui import QAction, QGuiApplication, QIcon
from PySide6.QtWidgets import (QButtonGroup, QHBoxLayout, QLabel, QMainWindow, QMenu, QMessageBox, QPushButton,
                               QStackedWidget, QVBoxLayout, QWidget)

from .. import APP_NAME, MC_VERSION, VERSION, appconfig, auth, launch, paths
from ..auth import Account, LoginError
from ..installer import InstallError, Installer, read_manifest
from ..jsonfile import read_json, write_json_atomic
from ..modrinth import FABRIC_API, SODIUM, ModrinthError
from ..settings import LauncherSettings, max_ram_for_slider, total_ram_mb
from ..token_store import TokenStore
from ..workers import GameWatcher, SignalProgress, Task
from . import theme
from .logo import render as render_logo
from .pages import HomePage, LogsPage, ModsPage, SettingsPage
from .widgets import AccountBadge

log = logging.getLogger("hollow")
FRESH_SECONDS = 20 * 60  # re-use a Minecraft token for this long before refreshing again


class _LogBridge(QObject):
    line = Signal(str)


class _QtLogHandler(logging.Handler):
    def __init__(self, bridge: _LogBridge):
        super().__init__()
        self.bridge = bridge
        self.setFormatter(logging.Formatter("[%(asctime)s] [Hollow] %(message)s", "%H:%M:%S"))

    def emit(self, record: logging.LogRecord) -> None:
        self.bridge.line.emit(self.format(record))


class MainWindow(QMainWindow):
    def __init__(self, network: bool = True):
        super().__init__()
        self.network = network
        self.game_dir = paths.game_dir()
        self.settings = LauncherSettings.load(paths.settings_file(self.game_dir))
        self.config = appconfig.load()
        self.store = TokenStore()
        self.account: Account | None = None
        public, _ = read_json(self.game_dir / "account.json")
        self.account_public: dict[str, Any] = public if isinstance(public, dict) else {}
        self.skin_png: bytes | None = self._cached_skin()
        self.progress = SignalProgress()
        self.installer = Installer(self.game_dir, self.progress)
        self.tasks: list[Task] = []
        self.busy = False
        self.game = None
        self.watcher: GameWatcher | None = None
        self.login_cancel: threading.Event | None = None
        self.updates: list = []

        self.setWindowTitle(APP_NAME)
        self.setWindowIcon(self._icon())
        self.resize(1180, 740)
        self.setMinimumSize(980, 640)
        self._build()
        self._setup_logging()
        theme.dark_title_bar(self)

        self.progress.status_changed.connect(lambda t: self.home.set_status(t))
        self.progress.progress_changed.connect(self._on_progress)
        self.progress.logged.connect(lambda t: log.info(t))
        self._refresh_home()
        if network:
            QTimer.singleShot(150, self._startup)

    # ------------------------------------------------------------------------------------------------ building

    def _icon(self) -> QIcon:
        ico = paths.assets_dir() / "icon.ico"
        return QIcon(str(ico)) if ico.is_file() else QIcon()

    def _build(self) -> None:
        root = QWidget()
        root.setObjectName("root")
        self.setCentralWidget(root)
        outer = QHBoxLayout(root)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.setSpacing(0)

        side = QWidget()
        side.setObjectName("sidebar")
        side.setFixedWidth(220)
        sl = QVBoxLayout(side)
        sl.setContentsMargins(18, 22, 18, 18)
        sl.setSpacing(6)
        brand_row = QHBoxLayout()
        logo = QLabel()
        logo.setPixmap(QIcon(self._logo_pixmap()).pixmap(36, 36))
        brand_row.addWidget(logo)
        names = QVBoxLayout()
        names.setSpacing(0)
        b1 = QLabel("HOLLOW")
        b1.setObjectName("brand")
        b2 = QLabel("CLIENT")
        b2.setObjectName("brandSub")
        names.addWidget(b1)
        names.addWidget(b2)
        brand_row.addLayout(names)
        brand_row.addStretch(1)
        sl.addLayout(brand_row)
        sl.addSpacing(22)

        self.nav = QButtonGroup(self)
        self.nav.setExclusive(True)
        self.pages = QStackedWidget()
        self.home = HomePage()
        self.mods = ModsPage()
        self.settings_page = SettingsPage(self.settings, max_ram_for_slider(total_ram_mb()), total_ram_mb())
        self.logs = LogsPage()
        self.page_titles = []
        for i, (title, sub, page) in enumerate([
            ("Home", f"Minecraft {MC_VERSION} with Fabric, Sodium and Hollow Client", self.home),
            ("Styles & Mods", "What's installed, updates, and where styles live", self.mods),
            ("Settings", "Memory, window size and launcher options", self.settings_page),
            ("Logs", "Live output from the launcher and the game", self.logs),
        ]):
            btn = QPushButton("   " + title.replace("&", "&&"))
            btn.setObjectName("nav")
            btn.setCheckable(True)
            btn.setCursor(Qt.CursorShape.PointingHandCursor)
            self.nav.addButton(btn, i)
            sl.addWidget(btn)
            self.pages.addWidget(page)
            self.page_titles.append((title, sub))
        self.nav.button(0).setChecked(True)
        self.nav.idClicked.connect(self.show_page)
        sl.addStretch(1)
        ver = QLabel(f"Launcher {VERSION}\nNot affiliated with Mojang or Microsoft")
        ver.setObjectName("sideVersion")
        ver.setWordWrap(True)
        sl.addWidget(ver)
        outer.addWidget(side)

        content = QWidget()
        cl = QVBoxLayout(content)
        cl.setContentsMargins(28, 22, 28, 24)
        cl.setSpacing(16)
        top = QHBoxLayout()
        titles = QVBoxLayout()
        titles.setSpacing(2)
        self.page_title = QLabel("Home")
        self.page_title.setObjectName("pageTitle")
        self.page_sub = QLabel(self.page_titles[0][1])
        self.page_sub.setObjectName("pageSub")
        titles.addWidget(self.page_title)
        titles.addWidget(self.page_sub)
        top.addLayout(titles)
        top.addStretch(1)
        self.badge = AccountBadge()
        self.badge_menu = QMenu(self)
        self.badge.setMenu(self.badge_menu)
        self.badge_menu.aboutToShow.connect(self._fill_account_menu)
        top.addWidget(self.badge, 0, Qt.AlignmentFlag.AlignTop)
        cl.addLayout(top)
        cl.addWidget(self.pages, 1)
        outer.addWidget(content, 1)

        # wiring
        self.home.play.clicked.connect(self.on_play)
        self.home.repair.clicked.connect(self.on_repair)
        self.home.open_game.clicked.connect(lambda: self._open_folder(self.game_dir))
        self.home.cancel.clicked.connect(self._cancel_login)
        self.home.banner.action_clicked.connect(self.on_update_mods)
        self.mods.check.clicked.connect(lambda: self.check_updates(silent=False))
        self.mods.update_all.clicked.connect(self.on_update_mods)
        self.mods.open_mods.clicked.connect(lambda: self._open_folder(self.game_dir / "mods"))
        self.mods.open_packs.clicked.connect(lambda: self._open_folder(self.game_dir / "resourcepacks"))
        self.mods.open_capes.clicked.connect(lambda: self._open_folder(self.game_dir / "config" / "hollowclient" / "capes"))
        self.settings_page.changed.connect(self._save_settings)
        self.settings_page.open_game.clicked.connect(lambda: self._open_folder(self.game_dir))
        self.settings_page.open_config.clicked.connect(lambda: self._open_folder(paths.app_dir()))
        self.settings_page.config_status.setText(self._config_text())
        self.logs.copy.clicked.connect(self._copy_logs)
        self.logs.open_logs.clicked.connect(lambda: self._open_folder(self.game_dir / "logs"))

    def _logo_pixmap(self):
        from PySide6.QtGui import QPixmap

        return QPixmap.fromImage(render_logo(72))

    def _setup_logging(self) -> None:
        log.setLevel(logging.INFO)
        for h in list(log.handlers):
            log.removeHandler(h)
        try:
            self.game_dir.mkdir(parents=True, exist_ok=True)
            fh = RotatingFileHandler(self.game_dir / "launcher.log", maxBytes=1_000_000, backupCount=2, encoding="utf-8")
            fh.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
            log.addHandler(fh)
        except OSError:
            pass
        self._bridge = _LogBridge()
        self._bridge.line.connect(self.logs.append)
        log.addHandler(_QtLogHandler(self._bridge))
        log.info(f"{APP_NAME} launcher {VERSION} - game folder: {self.game_dir}")

    def show_page(self, index: int) -> None:
        self.pages.setCurrentIndex(index)
        self.nav.button(index).setChecked(True)
        title, sub = self.page_titles[index]
        self.page_title.setText(title)
        self.page_sub.setText(sub)

    # ------------------------------------------------------------------------------------------------ helpers

    def _config_text(self) -> str:
        where = f" (looked in {paths.app_dir()})" if self.config.source is None else ""
        return self.config.status_text() + where

    def _open_folder(self, path: Path) -> None:
        try:
            path.mkdir(parents=True, exist_ok=True)
            os.startfile(str(path))  # type: ignore[attr-defined]
        except (OSError, AttributeError) as e:
            log.warning(f"Couldn't open {path}: {e}")

    def _copy_logs(self) -> None:
        QGuiApplication.clipboard().setText(self.logs.text.toPlainText())
        log.info("Logs copied to the clipboard")

    def _save_settings(self) -> None:
        try:
            self.settings.save(paths.settings_file(self.game_dir))
        except OSError as e:
            log.warning(f"Settings couldn't be saved: {e}")

    def _on_progress(self, current: int, maximum: int) -> None:
        bar = self.home.progress
        if maximum <= 0:
            bar.setRange(0, 0)
        else:
            bar.setRange(0, 1000)
            bar.setValue(int(current * 1000 / max(1, maximum)))

    def _spawn(self, fn: Callable[[SignalProgress], Any], ok: Callable[[Any], None], fail: Callable[[Exception], None],
               progress: SignalProgress | None = None) -> Task:
        task = Task(fn, progress or SignalProgress())
        task.succeeded.connect(ok)
        task.failed.connect(fail)
        task.finished.connect(lambda: self.tasks.remove(task) if task in self.tasks else None)
        self.tasks.append(task)
        task.start()
        return task

    def _set_busy(self, busy: bool, text: str = "") -> None:
        self.busy = busy
        self.home.play.setEnabled(not busy and self.game is None)
        self.home.repair.setEnabled(not busy)
        self.mods.update_all.setEnabled(not busy and any(u.needed for u in self.updates))
        self.home.progress.setVisible(busy)
        if busy:
            self.home.progress.setRange(0, 0)
            if text:
                self.home.set_status(text)

    def _refresh_home(self) -> None:
        installed = self.installer.is_installed()
        summary = self.installer.summary() if installed else {}
        self.home.set_versions(summary)
        if self.game is not None:
            self.home.play.setText("RUNNING")
            self.home.play.set_sub("")
        else:
            self.home.play.setText("PLAY")
            self.home.play.set_sub("" if installed else "Installs first (about 700 MB)")
        if not self.busy and self.game is None:
            if installed:
                self.home.set_status("Ready to play." if self._can_login() else
                                     "Installed. Microsoft login isn't set up yet, see Settings.", "")
            else:
                self.home.set_status("Not installed yet. Press PLAY to download Minecraft, Fabric, Sodium and Hollow Client.")
        self._refresh_account_ui()
        self._refresh_mod_rows()

    def _can_login(self) -> bool:
        return self.config.has_client_id and not self.config.problem

    def _refresh_account_ui(self, note: str | None = None) -> None:
        name = self.account.name if self.account else self.account_public.get("name")
        self.badge.set_account(name if isinstance(name, str) else None, self.skin_png, note)
        if self.account:
            text = f"Signed in as {self.account.name}. Your Microsoft sign-in is stored safely in Windows Credential Manager."
        elif not self._can_login():
            text = "Login isn't set up yet: " + self.config.status_text()
        elif isinstance(name, str):
            text = f"{name} - you'll be signed in again when you press PLAY."
        else:
            text = "Not signed in. Press PLAY or the account button to log in with Microsoft."
        self.home.account_text.setText(text)

    def _refresh_mod_rows(self) -> None:
        manifest = read_manifest(self.game_dir / "mods")
        latest = {u.project_id: u for u in self.updates}
        rows = []
        for pid, name in ((FABRIC_API, "Fabric API"), (SODIUM, "Sodium")):
            entry = manifest["projects"].get(pid) if isinstance(manifest.get("projects"), dict) else None
            present = isinstance(entry, dict) and (self.game_dir / "mods" / str(entry.get("filename", ""))).is_file()
            installed = str(entry.get("version_number", "")) if present else ""
            u = latest.get(pid)
            state = "missing" if not present else ("update" if u and u.needed else ("ok" if u else "unknown"))
            rows.append((name, installed, u.latest if u else "", state))
        hollow = manifest.get("hollowclient", {})
        hollow_name = hollow.get("filename", "") if isinstance(hollow, dict) else ""
        bundled = paths.find_mod_jar()
        hv = hollow_name[len("hollowclient-"):-4] if hollow_name.startswith("hollowclient-") else ""
        bv = bundled.name[len("hollowclient-"):-4] if bundled else ""
        state = "missing" if not hv else ("update" if bv and bv != hv else "ok")
        rows.append(("Hollow Client", hv, bv or hv, state))
        self.mods.set_rows(rows)

    def _cached_skin(self) -> bytes | None:
        f = self.game_dir / "cache" / "skin.png"
        try:
            return f.read_bytes() if f.is_file() else None
        except OSError:
            return None

    # ------------------------------------------------------------------------------------------------ startup

    def _startup(self) -> None:
        token = self.store.load_refresh_token() if self._can_login() else None
        if token:
            self._refresh_account(token, then=None, silent=True)
        if self.installer.is_installed():
            self.check_updates(silent=True)

    def check_updates(self, silent: bool) -> None:
        if not silent:
            self.mods.update_status.setText("Checking Modrinth...")

        def done(updates) -> None:
            self.updates = updates
            needed = [u for u in updates if u.needed]
            self.mods.update_status.setText(
                f"Checked {time.strftime('%H:%M')}. " + (f"{len(needed)} update(s) available." if needed else "Everything is up to date."))
            self.mods.update_all.setEnabled(bool(needed) and not self.busy)
            self._refresh_mod_rows()
            if needed and self.installer.is_installed():
                names = ", ".join(f"{u.name} {launch_pretty(u.latest)}" for u in needed)
                self.home.banner.show_message(f"Updates available: {names}", "info", "Update now")

        def failed(e: Exception) -> None:
            self.mods.update_status.setText(str(e) if isinstance(e, ModrinthError) else f"Update check failed: {e}")

        self._spawn(lambda p: self.installer.check_updates(), done, failed)

    # ------------------------------------------------------------------------------------------------ PLAY

    def on_play(self) -> None:
        if self.busy or self.game is not None:
            return
        self.home.banner.hide()
        if not self.installer.is_installed():
            self._install(then=self._ensure_account)
        else:
            self._ensure_account()

    def _install(self, then: Callable[[], None] | None, repair: bool = False) -> None:
        self._set_busy(True, "Repairing..." if repair else "Installing...")
        log.info("Repair started" if repair else "Install started")

        def ok(version_id) -> None:
            self._set_busy(False)
            self._refresh_home()
            self.home.set_status("Everything is installed." if not repair else "Repair finished. Everything is installed.", "ok")
            self.check_updates(silent=True)
            if then:
                then()

        def fail(e: Exception) -> None:
            self._set_busy(False)
            self._refresh_home()
            msg = str(e) if isinstance(e, InstallError) else f"Install failed: {e}"
            self.home.set_status(msg, "error")
            log.error(msg)

        self._spawn(lambda p: self.installer.install_all(), ok, fail, self.progress)

    def on_repair(self) -> None:
        if not self.busy:
            self._install(then=None, repair=True)

    def on_update_mods(self) -> None:
        if self.busy:
            return
        self.home.banner.hide()
        self._set_busy(True, "Updating mods...")

        def ok(changed) -> None:
            self._set_busy(False)
            self.updates = []
            self._refresh_home()
            self.home.set_status("Updated " + ", ".join(changed) + "." if changed else "Mods were already up to date.", "ok")
            self.check_updates(silent=True)

        def fail(e: Exception) -> None:
            self._set_busy(False)
            self.home.set_status(str(e), "error")

        self._spawn(lambda p: self.installer.install_mods(force=False), ok, fail, self.progress)

    def _ensure_account(self) -> None:
        if not self._can_login():
            self.home.set_status("Microsoft login isn't set up yet. " + self.config.status_text(), "error")
            self._show_config_help()
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
            self._login_failed(e, silent)

        self._spawn(lambda p: auth.refresh(self.config, token), ok, fail)

    def _login(self, then: Callable[[], None] | None) -> None:
        if not self._can_login():
            self._show_config_help()
            return
        self.login_cancel = threading.Event()
        cancel = self.login_cancel
        self._set_busy(True, "Opening the Microsoft sign-in page in your browser... Finish signing in there.")
        self.home.cancel.show()
        log.info("Opened the Microsoft sign-in page in the browser")

        def ok(account: Account) -> None:
            self.home.cancel.hide()
            self._set_busy(False)
            self._signed_in(account)
            self.home.set_status(f"Signed in as {account.name}.", "ok")
            if then:
                then()

        def fail(e: Exception) -> None:
            self.home.cancel.hide()
            self._set_busy(False)
            self._login_failed(e, silent=False)

        self._spawn(lambda p: auth.login(self.config, cancelled=cancel), ok, fail)

    def _cancel_login(self) -> None:
        if self.login_cancel:
            self.login_cancel.set()

    def _signed_in(self, account: Account) -> None:
        self.account = account
        self.account_public = account.public()
        try:
            self.store.save_refresh_token(account.refresh_token)
        except Exception as e:
            log.warning(f"Couldn't save the sign-in to Credential Manager: {e}")
        try:
            write_json_atomic(self.game_dir / "account.json", account.public())
        except OSError:
            pass
        log.info(f"Signed in as {account.name}")
        self._refresh_account_ui()
        if account.skin_url and self.network:
            self._spawn(lambda p: _download_skin(account.skin_url, self.game_dir / "cache" / "skin.png"), self._skin_ready, lambda e: None)

    def _skin_ready(self, data: bytes | None) -> None:
        if data:
            self.skin_png = data
            self._refresh_account_ui()

    def _login_failed(self, e: Exception, silent: bool) -> None:
        if isinstance(e, LoginError):
            log.warning(f"Login: {e.kind}: {e.message}" + (f" ({e.detail[:300]})" if e.detail else ""))
            if e.kind == "cancelled":
                self.home.set_status("Sign-in cancelled.", "")
                return
            if e.kind == "not_approved":
                self.home.banner.show_message(auth.NOT_APPROVED_MESSAGE, "warn")
                self.home.set_status("Mojang hasn't approved the app ID yet (see the note above). Install and updates still work.", "")
                self._refresh_account_ui(note="not approved yet")
                if not silent:
                    QMessageBox.information(self, APP_NAME, auth.NOT_APPROVED_MESSAGE)
                return
            if not silent:
                self.home.set_status(e.message, "error")
            self._refresh_account_ui()
            return
        log.error(f"Login failed: {e}")
        if not silent:
            self.home.set_status(f"Sign-in failed: {e}", "error")

    def _show_config_help(self) -> None:
        QMessageBox.information(
            self, APP_NAME,
            "Microsoft login needs your Azure app (client) ID.\n\n"
            "1. Copy config.example.json to config.json (next to the launcher).\n"
            "2. Replace the zeros with your app ID.\n"
            "3. Restart the launcher.\n\n"
            "Until Mojang approves the app ID, you can still install and update everything here and play through the "
            "official launcher (see USER_GUIDE.md).\n\n" + self._config_text())

    def _fill_account_menu(self) -> None:
        m = self.badge_menu
        m.clear()
        if self.account or self.account_public.get("name"):
            name = self.account.name if self.account else self.account_public.get("name")
            info = QAction(f"Signed in as {name}" if self.account else f"{name} (not signed in this session)", m)
            info.setEnabled(False)
            m.addAction(info)
            m.addSeparator()
            m.addAction("Log out", self.logout)
        else:
            m.addAction("Log in with Microsoft", lambda: self._login(then=None))
        m.addSeparator()
        m.addAction("Open config.json folder", lambda: self._open_folder(paths.app_dir()))

    def logout(self) -> None:
        self.store.clear()
        self.account = None
        self.account_public = {}
        self.skin_png = None
        for f in (self.game_dir / "account.json", self.game_dir / "cache" / "skin.png"):
            try:
                f.unlink(missing_ok=True)
            except OSError:
                pass
        log.info("Logged out; the stored sign-in was removed")
        self._refresh_home()
        self.home.set_status("Logged out.", "")

    def _launch(self) -> None:
        if self.account is None:
            return
        version_id = self.installer.version_id()
        if not version_id:
            self.home.set_status("The installation is incomplete. Press Repair.", "error")
            return
        try:
            options = launch.build_options(self.account, self.settings, self.game_dir)
            command = launch.build_command(version_id, self.game_dir, options)
        except Exception as e:
            self.home.set_status(f"Couldn't build the game command: {e}. Try Repair.", "error")
            log.error(f"Building the command failed: {e}")
            return
        log.info("Starting: " + " ".join(launch.redact(command, [self.account.access_token])))
        capture = not self.settings.close_on_launch
        try:
            self.game = launch.start(command, self.game_dir, capture_output=capture)
        except OSError as e:
            self.home.set_status(f"Minecraft couldn't start: {e}", "error")
            log.error(f"Start failed: {e}")
            return
        self.home.set_status("Minecraft is starting... it opens in its own window.", "ok")
        self._refresh_home()
        self.home.play.setEnabled(False)
        if capture:
            self.watcher = GameWatcher(self.game)
            self.watcher.line.connect(self.logs.append)
            self.watcher.exited.connect(self._game_exited)
            self.watcher.start()
        else:
            QTimer.singleShot(4000, self._close_after_launch)

    def _close_after_launch(self) -> None:
        if self.game is not None and self.game.poll() is None:
            log.info("Game started; closing the launcher as set in Settings")
            QGuiApplication.quit()
        else:
            code = self.game.poll() if self.game else None
            self.game = None
            self._refresh_home()
            self.home.set_status(f"Minecraft closed right away (code {code}). Turn off \"Close the launcher\" to see the log.", "error")

    def _game_exited(self, code: int) -> None:
        log.info(f"Minecraft closed (exit code {code})")
        self.game = None
        self.watcher = None
        self._refresh_home()
        if code == 0:
            self.home.set_status("Minecraft closed. Ready to play again.", "")
        else:
            self.home.set_status(f"Minecraft closed with an error (code {code}). The Logs tab has the details.", "error")


def launch_pretty(number: str) -> str:
    from .pages import pretty_version

    return pretty_version(number)


def _download_skin(url: str, target: Path) -> bytes | None:
    """Your skin from Mojang's texture server, for the avatar in the corner."""
    if not url.startswith(("http://textures.minecraft.net/", "https://textures.minecraft.net/")):
        return None
    r = requests.get(url.replace("http://", "https://", 1), timeout=10)
    r.raise_for_status()
    data = r.content[:2_000_000]
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return data
