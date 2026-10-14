"""The launcher's pages: Home, Styles & Mods, Settings and Logs. They only lay things out; the main window wires
them to the installer, login and game."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QFont, QTextCursor
from PySide6.QtWidgets import (QCheckBox, QGridLayout, QHBoxLayout, QLabel, QLineEdit, QPlainTextEdit, QProgressBar,
                               QScrollArea, QSlider, QSpinBox, QVBoxLayout, QWidget)

from .. import MC_VERSION, VERSION
from ..settings import MIN_RAM_MB, RAM_STEP_MB, LauncherSettings
from .widgets import Banner, Card, HeroPanel, PlayButton, button, label, repolish


def _page() -> tuple[QWidget, QVBoxLayout]:
    w = QWidget()
    lay = QVBoxLayout(w)
    lay.setContentsMargins(0, 0, 0, 0)
    lay.setSpacing(14)
    return w, lay


def _scroll(inner: QWidget) -> QScrollArea:
    area = QScrollArea()
    area.setWidget(inner)
    area.setWidgetResizable(True)
    area.setFrameShape(QScrollArea.Shape.NoFrame)
    area.setStyleSheet("QScrollArea { background: transparent; } QScrollArea > QWidget > QWidget { background: transparent; }")
    return area


def pretty_version(number: str) -> str:
    """Modrinth version numbers like 'mc1.21.11-0.8.14-fabric' -> '0.8.14'."""
    n = number
    if n.startswith("mc") and "-" in n:
        n = n.split("-", 1)[1]
    for suffix in ("-fabric", "+1.21.11", "+mc1.21.11"):
        if n.endswith(suffix):
            n = n[: -len(suffix)]
    return n


class HomePage(QWidget):
    def __init__(self) -> None:
        super().__init__()
        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(14)
        self.banner = Banner()
        lay.addWidget(self.banner)

        self.hero = HeroPanel()
        hero_lay = QVBoxLayout(self.hero)
        hero_lay.setContentsMargins(34, 30, 34, 28)
        hero_lay.setSpacing(6)
        kicker = QLabel(f"MINECRAFT {MC_VERSION}  ·  FABRIC  ·  SODIUM")
        kicker.setStyleSheet("color: #b8a8ff; font-size: 11px; font-weight: 700; letter-spacing: 2px; background: transparent;")
        title = QLabel("Hollow Client")
        title.setStyleSheet("font-size: 34px; font-weight: 800; background: transparent;")
        sub = QLabel("Your own client: HUD, zoom, toggle sprint, styles and more.\nPress Right Shift in game to open the Hollow menu.")
        sub.setStyleSheet("color: #b9bccb; font-size: 13px; background: transparent;")
        hero_lay.addWidget(kicker)
        hero_lay.addWidget(title)
        hero_lay.addWidget(sub)
        hero_lay.addStretch(1)
        row = QHBoxLayout()
        row.setSpacing(18)
        self.play = PlayButton()
        row.addWidget(self.play)
        col = QVBoxLayout()
        col.setSpacing(6)
        self.status = QLabel("Checking your installation...")
        self.status.setObjectName("status")
        self.status.setWordWrap(True)
        self.status.setStyleSheet("background: transparent;")
        self.progress = QProgressBar()
        self.progress.setTextVisible(False)
        self.progress.setFixedHeight(6)
        self.progress.hide()
        self.cancel = button("Cancel sign-in", "link")
        self.cancel.hide()
        col.addStretch(1)
        col.addWidget(self.status)
        col.addWidget(self.progress)
        col.addWidget(self.cancel, 0, Qt.AlignmentFlag.AlignLeft)
        col.addStretch(1)
        row.addLayout(col, 1)
        hero_lay.addLayout(row)
        lay.addWidget(self.hero, 1)

        cards = QHBoxLayout()
        cards.setContentsMargins(0, 0, 0, 0)
        cards.setSpacing(14)
        self.game_card = Card("Installed")
        self.versions = QGridLayout()
        self.versions.setHorizontalSpacing(18)
        self.versions.setVerticalSpacing(4)
        self.game_card.body.addLayout(self.versions)
        self.game_card.body.addStretch(1)
        cards.addWidget(self.game_card, 1)

        self.account_card = Card("Account")
        self.account_text = label("Not signed in.")
        self.account_card.body.addWidget(self.account_text)
        self.account_card.body.addStretch(1)
        cards.addWidget(self.account_card, 1)

        actions = Card("Quick actions")
        self.repair = button("Repair installation")
        self.open_game = button("Open game folder")
        actions.body.addWidget(self.repair)
        actions.body.addWidget(self.open_game)
        actions.body.addStretch(1)
        cards.addWidget(actions, 1)
        holder = QWidget()
        holder.setLayout(cards)
        holder.setMaximumHeight(190)
        lay.addWidget(holder, 0)

    def set_status(self, text: str, kind: str = "") -> None:
        self.status.setText(text)
        self.status.setProperty("kind", kind)
        repolish(self.status)

    def set_versions(self, summary: dict[str, str]) -> None:
        while self.versions.count():
            item = self.versions.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        rows = summary or {"Minecraft": "not installed yet"}
        for i, (k, v) in enumerate(rows.items()):
            self.versions.addWidget(label(k, "cardText", False), i, 0)
            self.versions.addWidget(label(pretty_version(v), "value", False), i, 1)


class ModsPage(QWidget):
    def __init__(self) -> None:
        super().__init__()
        inner, lay = _page()
        mods = Card("Mods Hollow installs")
        mods.body.addWidget(label("Fabric API and Sodium come from Modrinth (the official mod site); the Hollow Client mod "
                                  "comes with the launcher. Updates replace only the files Hollow installed."))
        self.grid = QGridLayout()
        self.grid.setHorizontalSpacing(18)
        self.grid.setVerticalSpacing(8)
        mods.body.addLayout(self.grid)
        row = QHBoxLayout()
        self.check = button("Check for updates")
        self.update_all = button("Update all", "primary")
        self.update_all.setEnabled(False)
        self.open_mods = button("Open mods folder")
        row.addWidget(self.check)
        row.addWidget(self.update_all)
        row.addStretch(1)
        row.addWidget(self.open_mods)
        mods.body.addLayout(row)
        self.update_status = label("", "faint")
        mods.body.addWidget(self.update_status)
        lay.addWidget(mods)

        styles = Card("Styles")
        styles.body.addWidget(label("Capes, the hat and wings, texture packs and presets are picked inside the game: press "
                                    "Right Shift, then open Styles. Only you can see your styles."))
        row2 = QHBoxLayout()
        self.open_packs = button("Open resourcepacks folder")
        self.open_capes = button("Open imported capes folder")
        row2.addWidget(self.open_packs)
        row2.addWidget(self.open_capes)
        row2.addStretch(1)
        styles.body.addLayout(row2)
        lay.addWidget(styles)

        extra = Card("Adding your own mods")
        extra.body.addWidget(label("You can drop other Fabric mods for 1.21.11 into the mods folder. Only add mods you "
                                   "trust, and keep to fair-play mods on servers."))
        lay.addWidget(extra)
        lay.addStretch(1)
        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.addWidget(_scroll(inner))

    def set_rows(self, rows: list[tuple[str, str, str, str]]) -> None:
        """rows: (name, installed, latest, state) where state is 'ok', 'update' or 'missing'."""
        while self.grid.count():
            item = self.grid.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        for c, head in enumerate(["Mod", "Installed", "Newest", ""]):
            self.grid.addWidget(label(head, "faint", False), 0, c)
        colors = {"ok": "#5bd18b", "update": "#ffc24a", "missing": "#f2706b", "unknown": "#9ea3b0"}
        texts = {"ok": "Up to date", "update": "Update available", "missing": "Not installed", "unknown": "-"}
        for r, (name, installed, latest, state) in enumerate(rows, start=1):
            self.grid.addWidget(label(name, "value", False), r, 0)
            self.grid.addWidget(label(pretty_version(installed) if installed else "-", "cardText", False), r, 1)
            self.grid.addWidget(label(pretty_version(latest) if latest else "-", "cardText", False), r, 2)
            pill = QLabel(texts.get(state, state))
            pill.setStyleSheet(f"color: {colors.get(state, '#9ea3b0')}; font-size: 12px; font-weight: 600;")
            self.grid.addWidget(pill, r, 3)
        self.grid.setColumnStretch(3, 1)


class SettingsPage(QWidget):
    changed = Signal()

    def __init__(self, settings: LauncherSettings, max_ram: int, total_ram: int) -> None:
        super().__init__()
        self.settings = settings
        inner, lay = _page()

        mem = Card("Memory (RAM)")
        row = QHBoxLayout()
        self.ram = QSlider(Qt.Orientation.Horizontal)
        self.ram.setRange(MIN_RAM_MB // RAM_STEP_MB, max(MIN_RAM_MB, max_ram) // RAM_STEP_MB)
        self.ram.setValue(min(settings.ram_mb, max_ram) // RAM_STEP_MB)
        self.ram_label = QLabel()
        self.ram_label.setObjectName("value")
        self.ram_label.setMinimumWidth(70)
        row.addWidget(self.ram, 1)
        row.addWidget(self.ram_label)
        mem.body.addLayout(row)
        mem.body.addWidget(label(f"How much memory Minecraft may use. 4 GB is plenty for most players. "
                                 f"This PC has {total_ram / 1024:.0f} GB.", "hint"))
        lay.addWidget(mem)

        jvm = Card("Extra Java arguments")
        self.jvm = QLineEdit(settings.jvm_args)
        self.jvm.setPlaceholderText("Optional, for example -XX:+UseZGC")
        jvm.body.addWidget(self.jvm)
        jvm.body.addWidget(label("Leave empty unless you know you need something. Memory is set above.", "hint"))
        lay.addWidget(jvm)

        win = Card("Game window")
        self.default_size = QCheckBox("Use Minecraft's normal window size")
        self.default_size.setChecked(not (settings.resolution_width and settings.resolution_height))
        size_row = QHBoxLayout()
        self.width = QSpinBox()
        self.width.setRange(320, 7680)
        self.width.setValue(settings.resolution_width or 1280)
        self.height = QSpinBox()
        self.height.setRange(240, 4320)
        self.height.setValue(settings.resolution_height or 720)
        for sb in (self.width, self.height):
            sb.setButtonSymbols(QSpinBox.ButtonSymbols.NoButtons)
            sb.setFixedWidth(90)
        size_row.addWidget(QLabel("Width"))
        size_row.addWidget(self.width)
        size_row.addSpacing(10)
        size_row.addWidget(QLabel("Height"))
        size_row.addWidget(self.height)
        size_row.addStretch(1)
        win.body.addWidget(self.default_size)
        win.body.addLayout(size_row)
        lay.addWidget(win)

        launcher = Card("Launcher")
        self.close_on_launch = QCheckBox("Close the launcher when the game starts")
        self.close_on_launch.setChecked(settings.close_on_launch)
        launcher.body.addWidget(self.close_on_launch)
        launcher.body.addWidget(label("Keep it open to see the game's log in the Logs tab.", "hint"))
        row3 = QHBoxLayout()
        self.open_game = button("Open game folder")
        row3.addWidget(self.open_game)
        row3.addStretch(1)
        launcher.body.addLayout(row3)
        lay.addWidget(launcher)

        login = Card("Microsoft login")
        self.config_status = label("")
        login.body.addWidget(self.config_status)
        row4 = QHBoxLayout()
        self.open_config = button("Open config.json folder")
        row4.addWidget(self.open_config)
        row4.addStretch(1)
        login.body.addLayout(row4)
        lay.addWidget(login)

        about = Card("About")
        about.body.addWidget(label(f"Hollow Client launcher {VERSION} for Minecraft {MC_VERSION}. A personal project; not "
                                   "affiliated with or endorsed by Mojang or Microsoft. Game files come only from official "
                                   "sources, and login uses Microsoft's own sign-in page.", "hint"))
        lay.addWidget(about)
        lay.addStretch(1)

        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.addWidget(_scroll(inner))

        self._sync_ram_label()
        self._sync_size_enabled()
        self.ram.valueChanged.connect(self._on_change)
        self.jvm.editingFinished.connect(self._on_change)
        self.default_size.toggled.connect(self._on_change)
        self.width.editingFinished.connect(self._on_change)
        self.height.editingFinished.connect(self._on_change)
        self.close_on_launch.toggled.connect(self._on_change)

    def _sync_ram_label(self) -> None:
        self.ram_label.setText(f"{self.ram.value() * RAM_STEP_MB / 1024:.1f} GB")

    def _sync_size_enabled(self) -> None:
        on = not self.default_size.isChecked()
        self.width.setEnabled(on)
        self.height.setEnabled(on)

    def _on_change(self, *args) -> None:
        self._sync_ram_label()
        self._sync_size_enabled()
        s = self.settings
        s.ram_mb = self.ram.value() * RAM_STEP_MB
        s.jvm_args = self.jvm.text()
        if self.default_size.isChecked():
            s.resolution_width = s.resolution_height = 0
        else:
            s.resolution_width, s.resolution_height = self.width.value(), self.height.value()
        s.close_on_launch = self.close_on_launch.isChecked()
        self.changed.emit()


class LogsPage(QWidget):
    MAX_LINES = 20000

    def __init__(self) -> None:
        super().__init__()
        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(10)
        row = QHBoxLayout()
        self.copy = button("Copy logs", "primary")
        self.clear = button("Clear")
        self.open_logs = button("Open logs folder")
        self.follow = QCheckBox("Follow new lines")
        self.follow.setChecked(True)
        row.addWidget(self.copy)
        row.addWidget(self.clear)
        row.addWidget(self.open_logs)
        row.addStretch(1)
        row.addWidget(self.follow)
        lay.addLayout(row)
        self.text = QPlainTextEdit()
        self.text.setObjectName("log")
        self.text.setReadOnly(True)
        self.text.setMaximumBlockCount(self.MAX_LINES)
        self.text.setLineWrapMode(QPlainTextEdit.LineWrapMode.NoWrap)
        self.text.setFont(QFont("Cascadia Mono", 9))
        lay.addWidget(self.text, 1)
        self.clear.clicked.connect(self.text.clear)

    def append(self, line: str) -> None:
        self.text.appendPlainText(line)
        if self.follow.isChecked():
            self.text.moveCursor(QTextCursor.MoveOperation.End)
