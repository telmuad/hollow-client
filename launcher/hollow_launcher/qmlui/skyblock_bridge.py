"""The SkyBlock page's data for QML. Building it can hit the network, so it runs on a worker thread; offline
(self-test, screenshots) it only reads the cache."""

from __future__ import annotations

import logging
import time
from pathlib import Path
from typing import Any, Callable

from PySide6.QtCore import Property, QObject, Signal, Slot

from .. import skyblock_data
from ..workers import SignalProgress, Task

log = logging.getLogger("hollow")
REFRESH_AFTER = 4 * 60  # seconds before the page fetches again when it's opened


class SkyBlockBridge(QObject):
    changed = Signal()

    def __init__(self, cache_dir: Path, instance: Callable[[], Path | None], network: bool = True,
                 parent: QObject | None = None):
        super().__init__(parent)
        self.dashboard = skyblock_data.Dashboard(cache_dir, network=network)
        self.instance = instance
        self.network = network
        self._data: dict[str, Any] = {}
        self._loading = False
        self._built_at = 0.0
        self._task: Task | None = None
        self._build_now(force=False)  # from the cache: the page shows something at once

    def _build_now(self, force: bool) -> None:
        net = self.dashboard.network
        self.dashboard.network = False
        try:
            self._data = self.dashboard.build(self.instance(), force=False)
        except Exception as e:
            log.warning(f"SkyBlock page: {e}")
        finally:
            self.dashboard.network = net
        self.changed.emit()

    data = Property("QVariantMap", lambda self: self._data, notify=changed)
    loading = Property(bool, lambda self: self._loading, notify=changed)

    @Slot()
    def refreshIfDue(self) -> None:  # noqa: N802
        if time.time() - self._built_at >= REFRESH_AFTER:
            self.refresh()

    @Slot()
    def refresh(self) -> None:
        if self._loading or not self.network:
            if not self.network:
                self._build_now(force=False)
            return
        self._loading = True
        self.changed.emit()
        instance = self.instance()
        task = Task(lambda p: self.dashboard.build(instance, force=False), SignalProgress())
        task.succeeded.connect(self._done)
        task.failed.connect(self._failed)
        self._task = task
        task.start()

    def _done(self, data: dict[str, Any]) -> None:
        self._data = data
        self._built_at = time.time()
        self._loading = False
        self._task = None
        self.changed.emit()

    def _failed(self, e: Exception) -> None:
        log.warning(f"SkyBlock page couldn't refresh: {e}")
        self._loading = False
        self._task = None
        self.changed.emit()
