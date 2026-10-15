"""Launcher UI sounds: short original WAV files (made by tools/make_launcher_sounds.py) played with Windows' own
sound API, so no extra Qt module is needed. Quietly does nothing elsewhere or when sounds are off."""

from __future__ import annotations

import sys
from pathlib import Path

from PySide6.QtCore import Property, QObject, Signal, Slot

NAMES = ("click", "hover", "whoosh", "charge", "pop", "toggle", "intro")


class Sounds(QObject):
    enabledChanged = Signal()

    def __init__(self, folder: Path, enabled: bool = True, parent: QObject | None = None):
        super().__init__(parent)
        self.folder = folder
        self._enabled = enabled
        self.played: list[str] = []  # for tests

    def _get_enabled(self) -> bool:
        return self._enabled

    def _set_enabled(self, value: bool) -> None:
        if value != self._enabled:
            self._enabled = value
            self.enabledChanged.emit()

    enabled = Property(bool, _get_enabled, _set_enabled, notify=enabledChanged)

    @Slot(str)
    def play(self, name: str) -> None:
        if not self._enabled or name not in NAMES:
            return
        self.played.append(name)
        f = self.folder / f"{name}.wav"
        if sys.platform != "win32" or not f.is_file():
            return
        try:
            import winsound

            winsound.PlaySound(str(f), winsound.SND_FILENAME | winsound.SND_ASYNC | winsound.SND_NODEFAULT)
        except (ImportError, RuntimeError):
            pass
