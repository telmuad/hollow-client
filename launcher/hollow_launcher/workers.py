"""Background work for the window: installs, logins, update checks and watching the game's output.
Everything slow runs off the UI thread and reports back through Qt signals."""

from __future__ import annotations

import subprocess
from typing import Any, Callable

from PySide6.QtCore import QObject, QThread, Signal


class SignalProgress(QObject):
    """Implements installer.Progress by emitting signals (safe to call from any thread)."""

    status_changed = Signal(str)
    progress_changed = Signal(int, int)
    logged = Signal(str)

    def status(self, text: str) -> None:
        self.status_changed.emit(text)
        self.logged.emit(text)

    def progress(self, current: int, maximum: int) -> None:
        self.progress_changed.emit(int(current), int(maximum))

    def log(self, text: str) -> None:
        self.logged.emit(text)


class Task(QThread):
    """Runs fn(progress) on a worker thread. Emits succeeded(result) or failed(exception)."""

    succeeded = Signal(object)
    failed = Signal(object)

    def __init__(self, fn: Callable[[SignalProgress], Any], progress: SignalProgress, parent: QObject | None = None):
        super().__init__(parent)
        self.fn = fn
        self.progress = progress

    def run(self) -> None:
        try:
            result = self.fn(self.progress)
        except Exception as e:  # reported to the UI, never crashes the launcher
            self.failed.emit(e)
        else:
            self.succeeded.emit(result)


class GameWatcher(QThread):
    """Reads the game's output line by line and reports when it exits."""

    line = Signal(str)
    exited = Signal(int)

    def __init__(self, process: subprocess.Popen, parent: QObject | None = None):
        super().__init__(parent)
        self.process = process

    def run(self) -> None:
        if self.process.stdout is not None:
            for text in self.process.stdout:
                self.line.emit(text.rstrip("\r\n"))
        self.exited.emit(self.process.wait())
