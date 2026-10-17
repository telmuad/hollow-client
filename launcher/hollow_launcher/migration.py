"""Moving a launcher 1.x install (one game folder) into the instance layout of launcher 2.x.

Launcher 1.x kept everything in %APPDATA%\\.hollowclient. From 2.0 each version profile plays in its own folder,
instances\\<id>. The first time 2.x starts on an old install, the game's own files (mods, config, saves, options,
screenshots, resource packs, logs, ...) move into instances\\legacy-1.21.11, which is what 1.x always installed.
Files shared by all profiles (Minecraft versions, libraries, assets, Java runtimes) and the launcher's own files stay
where they are. Nothing is deleted: everything is renamed within the same drive, and a note is written.
"""

from __future__ import annotations

import logging
import shutil
from pathlib import Path

from .profiles import instances_dir

log = logging.getLogger("hollow")

LEGACY_ID = "legacy-1.21.11"
OLD_STATE = "hollow_install.json"
# Stays in the root: shared by every profile, or the launcher's own.
SHARED = {"assets", "libraries", "versions", "runtime", "cache", "instances", "launcher.log", "launcher.log.1",
          "launcher.log.2", "launcher_settings.json", "launcher_stats.json", "account.json", "migration.txt",
          "skyblock_cache"}


def needs_migration(root: Path) -> bool:
    """An old single-folder install: its install record sits in the root and there is no legacy instance yet."""
    return (root / OLD_STATE).is_file() and not (instances_dir(root) / LEGACY_ID).exists()


def migrate(root: Path) -> list[str]:
    """Move the old game folder's own files into instances/legacy-1.21.11. Returns the moved names."""
    if not needs_migration(root):
        return []
    target = instances_dir(root) / LEGACY_ID
    target.mkdir(parents=True, exist_ok=True)
    moved = []
    for item in sorted(root.iterdir(), key=lambda p: p.name.lower()):
        if item.name in SHARED or item.name.startswith(("launcher.log", ".tmp")):
            continue
        dest = target / item.name
        if dest.exists():
            log.warning(f"Migration: {item.name} already exists in {LEGACY_ID}; left in place")
            continue
        try:
            shutil.move(str(item), str(dest))
            moved.append(item.name)
        except OSError as e:
            log.warning(f"Migration: couldn't move {item.name}: {e}")
    note = ("Hollow Client launcher 2.0 moved the 1.21.11 game folder's own files into instances\\" + LEGACY_ID +
            ":\n" + "\n".join("  " + m for m in moved) + "\nMinecraft versions, libraries, assets and Java stay here, "
            "shared by all profiles.\n")
    (root / "migration.txt").write_text(note, encoding="utf-8")
    log.info(f"Migrated the old install into instances/{LEGACY_ID}: {', '.join(moved)}")
    return moved
