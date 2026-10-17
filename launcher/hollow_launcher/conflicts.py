"""Other SkyBlock mods in an instance's mods folder, which overlap with Hollow's SkyBlock Mode.

They're found by the mod id in each jar's fabric.mod.json (not by file name), so renamed jars count too. Hollow
doesn't remove anything: the launcher shows a note, and the mod's compatibility screen offers to switch off the
overlapping Hollow features.
"""

from __future__ import annotations

import json
import zipfile
from pathlib import Path

OVERLAPPING = {"skyblocker": "Skyblocker", "skyhanni": "SkyHanni", "firmament": "Firmament"}


def mod_id(jar: Path) -> str | None:
    try:
        with zipfile.ZipFile(jar) as z:
            data = json.loads(z.read("fabric.mod.json").decode("utf-8"))
    except (OSError, KeyError, ValueError, zipfile.BadZipFile):
        return None
    mid = data.get("id") if isinstance(data, dict) else None
    return mid if isinstance(mid, str) else None


def find(mods: Path) -> list[str]:
    """Display names of the overlapping mods in this mods folder (enabled jars only), sorted."""
    if not mods.is_dir():
        return []
    found = set()
    for f in mods.iterdir():
        if f.is_file() and f.name.lower().endswith(".jar"):
            name = OVERLAPPING.get(mod_id(f) or "")
            if name:
                found.add(name)
    return sorted(found)


def message(names: list[str]) -> str:
    if not names:
        return ""
    return (f"{' and '.join(names)} {'is' if len(names) == 1 else 'are'} in this profile's mods folder. They overlap "
            "with Hollow's SkyBlock Mode; in game, Hollow offers to switch off its matching features.")
