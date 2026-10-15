"""Small helpers for JSON files that must never be left half-written."""

from __future__ import annotations

import json
import os
import time
from pathlib import Path
from typing import Any


def write_json_atomic(path: Path, data: Any) -> None:
    """Write to a temp file next to the target, then replace the target in one step."""
    write_text_atomic(path, json.dumps(data, indent=2))


def write_text_atomic(path: Path, text: str, attempts: int = 12) -> None:
    """Write text to a temp file, then swap it in. On Windows the swap fails with "access denied" while another
    program (the game reading theme.json) has the file open for a moment, so it is retried briefly."""
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(text)
        f.flush()
        os.fsync(f.fileno())
    for attempt in range(attempts):
        try:
            os.replace(tmp, path)
            return
        except PermissionError:
            if attempt == attempts - 1:
                raise
            time.sleep(0.015 * (attempt + 1))


def read_json(path: Path) -> tuple[Any, bool]:
    """Returns (data, ok). A missing file gives (None, True); an unreadable one is renamed to
    <name>.corrupt-<time> and gives (None, False)."""
    if not path.exists():
        return None, True
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f), True
    except (OSError, ValueError):
        try:
            path.replace(path.with_name(f"{path.name}.corrupt-{time.strftime('%Y%m%d-%H%M%S')}"))
        except OSError:
            pass
        return None, False
