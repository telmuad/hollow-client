"""The launcher's colors come from the same theme.json the game uses (config/hollowclient/theme.json in the game
folder), so a theme picked in either place shows up in both. The file is checked once a second."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from PySide6.QtCore import Property, QObject, QTimer, Signal, Slot
from PySide6.QtGui import QColor

from ..jsonfile import write_text_atomic


@dataclass(frozen=True)
class Theme:
    name: str
    accent: int
    gradient_start: int
    gradient_end: int
    glow: float
    blur: float
    roundness: int
    speed: float
    rainbow: bool

    def to_json(self) -> dict[str, Any]:
        return {"name": self.name, "accent": hex_color(self.accent), "gradientStart": hex_color(self.gradient_start),
                "gradientEnd": hex_color(self.gradient_end), "glow": self.glow, "blur": self.blur,
                "roundness": self.roundness, "animationSpeed": self.speed, "rainbow": self.rainbow}


# The same presets as the mod (HollowTheme.PRESETS).
PRESETS = [
    Theme("Void", 0xFF9B6CFF, 0xFF8B5CF6, 0xFF22D3EE, 0.65, 0.6, 6, 1.0, False),
    Theme("Inferno", 0xFFFF7A2F, 0xFFFFB02E, 0xFFE11D48, 0.7, 0.55, 5, 1.1, False),
    Theme("Toxic", 0xFF7CFF4F, 0xFFB6FF3B, 0xFF10B981, 0.75, 0.5, 4, 1.0, False),
    Theme("Frost", 0xFF7DD3FC, 0xFFE0F2FE, 0xFF38BDF8, 0.55, 0.75, 8, 0.85, False),
    Theme("Bloodmoon", 0xFFFF2D55, 0xFFFF4D6D, 0xFF7F1D1D, 0.7, 0.6, 5, 0.9, False),
    Theme("Rainbow", 0xFFFF5CAA, 0xFFFF5CAA, 0xFF5CC8FF, 0.7, 0.6, 6, 1.0, True),
    Theme("SkyBlock", 0xFF38BDF8, 0xFF38BDF8, 0xFFF5C542, 0.65, 0.6, 6, 1.0, False),
]
DEFAULT = PRESETS[0]


def hex_color(argb: int) -> str:
    return f"#{argb & 0xFFFFFFFF:08X}"


def hex_rgb(argb: int) -> str:
    return f"#{argb & 0xFFFFFF:06X}"


def parse_color(text: Any, fallback: int) -> int:
    """'#RGB', '#RRGGBB' or '#AARRGGBB' (the # is optional), like the mod's ColorUtil.parseHex."""
    if not isinstance(text, str):
        return fallback
    s = text.strip().lstrip("#")
    try:
        if len(s) == 3:
            r, g, b = (int(c, 16) * 17 for c in s)
            return 0xFF000000 | r << 16 | g << 8 | b
        if len(s) == 6:
            return 0xFF000000 | int(s, 16)
        if len(s) == 8:
            return int(s, 16)
    except ValueError:
        pass
    return fallback


def _num(v: Any, fallback: float, lo: float, hi: float) -> float:
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        return fallback
    return max(lo, min(hi, float(v)))


def theme_from_json(data: Any, fallback: Theme = DEFAULT) -> Theme:
    if not isinstance(data, dict):
        return fallback
    name = data.get("name") if isinstance(data.get("name"), str) and data.get("name").strip() else fallback.name
    return Theme(
        name=name[:24],
        accent=parse_color(data.get("accent"), fallback.accent),
        gradient_start=parse_color(data.get("gradientStart"), fallback.gradient_start),
        gradient_end=parse_color(data.get("gradientEnd"), fallback.gradient_end),
        glow=_num(data.get("glow"), fallback.glow, 0, 1),
        blur=_num(data.get("blur"), fallback.blur, 0, 1),
        roundness=int(_num(data.get("roundness"), fallback.roundness, 0, 8)),
        speed=round(_num(data.get("animationSpeed"), fallback.speed, 0.25, 3), 2),
        rainbow=data.get("rainbow") if isinstance(data.get("rainbow"), bool) else fallback.rainbow,
    )


def read_theme_file(path: Path) -> tuple[Theme, list[dict[str, Any]]]:
    """The current theme and the custom themes list (kept as-is when the launcher writes the file)."""
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return DEFAULT, []
    if not isinstance(data, dict):
        return DEFAULT, []
    custom = data.get("custom") if isinstance(data.get("custom"), list) else []
    return theme_from_json(data.get("current")), [c for c in custom if isinstance(c, dict)]


def write_theme_file(path: Path, theme: Theme, custom: list[dict[str, Any]]) -> None:
    text = json.dumps({"version": 1, "current": theme.to_json(), "custom": custom}, indent=2)
    write_text_atomic(path, text)


def _qcolor(argb: int) -> QColor:
    return QColor.fromRgba(argb & 0xFFFFFFFF)


class ThemeBridge(QObject):
    """The theme for QML. Colors are QColors; radius and speed are already scaled for the launcher.

    With several theme files (one per version profile), the most recently changed one wins and the others are kept
    in sync, so a theme picked in either game or in the launcher shows up everywhere."""

    changed = Signal()

    def __init__(self, path: Path | list[Path], poll: bool = True, parent: QObject | None = None):
        super().__init__(parent)
        self.paths = [path] if isinstance(path, Path) else list(path)
        self.path = self.paths[0]
        self._mtimes: tuple = ()
        newest = self._newest()
        self._theme, self._custom = read_theme_file(newest)
        self._sync(newest)
        self._remember_mtime()
        if poll:
            self._timer = QTimer(self)
            self._timer.setInterval(1000)
            self._timer.timeout.connect(self._poll)
            self._timer.start()

    @staticmethod
    def _mtime(path: Path):
        try:
            return path.stat().st_mtime_ns
        except OSError:
            return None

    def _newest(self) -> Path:
        existing = [(self._mtime(f), i, f) for i, f in enumerate(self.paths) if self._mtime(f) is not None]
        return max(existing, key=lambda t: (t[0], -t[1]))[2] if existing else self.path

    def _remember_mtime(self) -> None:
        self._mtimes = tuple(self._mtime(f) for f in self.paths)

    def _sync(self, source: Path) -> None:
        """Copy the current theme into the other files whose current theme differs (only folders that exist)."""
        for f in self.paths:
            if f == source or not f.parent.parent.is_dir():
                continue
            theme, custom = read_theme_file(f)
            if theme != self._theme or (custom != self._custom and self._custom):
                try:
                    write_theme_file(f, self._theme, self._custom or custom)
                except OSError:
                    pass

    @Slot()
    def _poll(self) -> None:
        now = tuple(self._mtime(f) for f in self.paths)
        if now != self._mtimes:
            newest = self._newest()
            theme, custom = read_theme_file(newest)
            self._custom = custom
            if theme != self._theme:
                self._theme = theme
                self.changed.emit()
            if len(self.paths) > 1:
                self._sync(newest)
            self._remember_mtime()

    @property
    def theme(self) -> Theme:
        return self._theme

    @Slot(str, result=bool)
    def applyPreset(self, name: str) -> bool:  # noqa: N802 (Qt naming)
        all_themes = PRESETS + [theme_from_json(c) for c in self._custom]
        for t in all_themes:
            if t.name.lower() == name.lower():
                self._theme = t
                for i, f in enumerate(self.paths):
                    if i > 0 and not f.parent.parent.is_dir():
                        continue  # a profile that was never installed
                    try:
                        write_theme_file(f, t, self._custom)
                    except OSError:
                        pass
                self._remember_mtime()
                self.changed.emit()
                return True
        return False

    def _get_name(self) -> str:
        return self._theme.name

    def _get_accent(self) -> QColor:
        return _qcolor(self._theme.accent)

    def _get_start(self) -> QColor:
        return _qcolor(self._theme.gradient_start)

    def _get_end(self) -> QColor:
        return _qcolor(self._theme.gradient_end)

    def _get_glow(self) -> float:
        return self._theme.glow

    def _get_blur(self) -> float:
        return self._theme.blur

    def _get_radius(self) -> int:
        # the game's 0..8 GUI pixels become 4..20 launcher pixels
        return 4 + self._theme.roundness * 2

    def _get_speed(self) -> float:
        return self._theme.speed

    def _get_rainbow(self) -> bool:
        return self._theme.rainbow

    def _get_names(self) -> list[str]:
        names = [t.name for t in PRESETS]
        for c in self._custom:
            n = theme_from_json(c).name
            if n not in names:
                names.append(n)
        return names

    def _get_preset_colors(self) -> dict[str, list[str]]:
        out = {t.name: [hex_rgb(t.gradient_start), hex_rgb(t.gradient_end)] for t in PRESETS}
        for c in self._custom:
            t = theme_from_json(c)
            out.setdefault(t.name, [hex_rgb(t.gradient_start), hex_rgb(t.gradient_end)])
        return out

    name = Property(str, _get_name, notify=changed)
    presetColors = Property("QVariantMap", _get_preset_colors, notify=changed)
    accent = Property(QColor, _get_accent, notify=changed)
    gradientStart = Property(QColor, _get_start, notify=changed)
    gradientEnd = Property(QColor, _get_end, notify=changed)
    glow = Property(float, _get_glow, notify=changed)
    blur = Property(float, _get_blur, notify=changed)
    radius = Property(int, _get_radius, notify=changed)
    speed = Property(float, _get_speed, notify=changed)
    rainbow = Property(bool, _get_rainbow, notify=changed)
    names = Property(list, _get_names, notify=changed)
