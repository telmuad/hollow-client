"""Play statistics for the dashboard. The launcher writes down when the game starts, how long it ran and which
profile it was (launcher_stats.json in the root folder); everything else is counted from the profile's game folder."""

from __future__ import annotations

import json
import time
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

from ..jsonfile import read_json, write_json_atomic

MAX_SESSIONS = 2000


def stats_file(game_dir: Path) -> Path:
    return game_dir / "launcher_stats.json"


def load_sessions(game_dir: Path) -> list[dict[str, Any]]:
    data, _ = read_json(stats_file(game_dir))
    sessions = data.get("sessions") if isinstance(data, dict) else None
    out = []
    for s in sessions if isinstance(sessions, list) else []:
        if isinstance(s, dict) and isinstance(s.get("start"), (int, float)):
            secs = s.get("seconds")
            out.append({"start": float(s["start"]), "seconds": float(secs) if isinstance(secs, (int, float)) and secs >= 0 else None})
    return out


def record_session(root: Path, start: float, seconds: float | None, profile: str | None = None) -> None:
    data, _ = read_json(stats_file(root))
    old = data.get("sessions") if isinstance(data, dict) else None
    sessions = [s for s in old if isinstance(s, dict)] if isinstance(old, list) else []
    entry: dict[str, Any] = {"start": start, "seconds": seconds}
    if profile:
        entry["profile"] = profile
    sessions.append(entry)
    try:
        write_json_atomic(stats_file(root), {"version": 1, "sessions": sessions[-MAX_SESSIONS:]})
    except OSError:
        pass


def summarize(sessions: list[dict[str, Any]], today: date | None = None, days: int = 14) -> dict[str, Any]:
    """Minutes per day for the last `days` days (oldest first) and the totals."""
    today = today or date.today()
    per_day: dict[date, float] = {}
    total = 0.0
    longest = 0.0
    timed = 0
    played_days: set[date] = set()
    for s in sessions:
        d = datetime.fromtimestamp(s["start"]).date()
        played_days.add(d)
        secs = s.get("seconds")
        if secs is None:
            continue
        timed += 1
        total += secs
        longest = max(longest, secs)
        per_day[d] = per_day.get(d, 0.0) + secs / 60
    chart = []
    for i in range(days - 1, -1, -1):
        d = today - timedelta(days=i)
        chart.append({"label": d.strftime("%a")[:2], "date": d.isoformat(), "minutes": round(per_day.get(d, 0.0), 1)})
    streak = 0
    d = today
    while d in played_days:
        streak += 1
        d -= timedelta(days=1)
    return {
        "chart": chart,
        "totalHours": round(total / 3600, 1),
        "sessions": len(sessions),
        "longestMinutes": round(longest / 60),
        "averageMinutes": round(total / 60 / timed) if timed else 0,
        "streak": streak,
        "weekMinutes": round(sum(c["minutes"] for c in chart[-7:])),
    }


def _count(folder: Path, pattern: str = "*") -> int:
    try:
        return sum(1 for _ in folder.glob(pattern)) if folder.is_dir() else 0
    except OSError:
        return 0


def folder_counts(game_dir: Path) -> dict[str, int]:
    """Things you made or collected, counted from the game folder."""
    hollow = game_dir / "config" / "hollowclient"
    waypoints = 0
    worlds = hollow / "worlds"
    if worlds.is_dir():
        for f in worlds.glob("*.json"):
            try:
                data = json.loads(f.read_text(encoding="utf-8"))
                waypoints += len(data.get("waypoints", [])) if isinstance(data, dict) else 0
            except (OSError, ValueError):
                pass
    modules_on = 0
    try:
        cfg = json.loads((game_dir / "config" / "hollowclient.json").read_text(encoding="utf-8"))
        mods = cfg.get("modules", {}) if isinstance(cfg, dict) else {}
        modules_on = sum(1 for m in mods.values() if isinstance(m, dict) and m.get("enabled") is True)
    except (OSError, ValueError):
        pass
    return {
        "screenshots": _count(game_dir / "screenshots", "*.png"),
        "worlds": sum(1 for p in (game_dir / "saves").iterdir() if p.is_dir()) if (game_dir / "saves").is_dir() else 0,
        "waypoints": waypoints,
        "capes": _count(hollow / "capes"),
        "songs": _count(hollow / "music", "*.mp3") + _count(hollow / "music", "*.ogg"),
        "modulesOn": modules_on,
    }


def now() -> float:
    return time.time()
