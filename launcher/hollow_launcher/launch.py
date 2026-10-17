"""Building the game command line and starting Minecraft."""

from __future__ import annotations

import shlex
import subprocess
import sys
from pathlib import Path
from typing import Any

import minecraft_launcher_lib as mll

from . import APP_NAME, VERSION
from .auth import Account
from .settings import LauncherSettings

REDACTED = "********"


def split_jvm_args(text: str) -> list[str]:
    """Split the "extra JVM arguments" box like a Windows command line: spaces separate arguments, double quotes
    group (backslashes in Windows paths are kept)."""
    text = (text or "").strip()
    if not text:
        return []
    lexer = shlex.shlex(text, posix=True)
    lexer.whitespace_split = True
    lexer.escape = ""  # keep backslashes (C:\path) as they are
    lexer.quotes = '"'
    try:
        return list(lexer)
    except ValueError:
        # An unclosed quote: fall back to plain splitting rather than refusing to start.
        return text.replace('"', "").split()


def memory_args(ram_mb: int) -> list[str]:
    return [f"-Xmx{ram_mb}M", f"-Xms{min(ram_mb, 1024)}M"]


HYPIXEL = "mc.hypixel.net"


def build_options(account: Account, settings: LauncherSettings, game_dir: Path, server: str | None = None) -> dict[str, Any]:
    """`game_dir` is the profile's instance folder. With `server`, the game connects there straight from the
    title screen (Minecraft's quick-play multiplayer argument); the player still types every command themselves."""
    options: dict[str, Any] = {
        "username": account.name,
        "uuid": account.uuid,
        "token": account.access_token,
        "jvmArguments": memory_args(settings.ram_mb) + split_jvm_args(settings.jvm_args),
        "launcherName": APP_NAME.replace(" ", ""),
        "launcherVersion": VERSION,
        "gameDirectory": str(game_dir),
    }
    if server:
        options["quickPlayMultiplayer"] = server
    if settings.resolution_width and settings.resolution_height:
        options["customResolution"] = True
        options["resolutionWidth"] = str(settings.resolution_width)
        options["resolutionHeight"] = str(settings.resolution_height)
    return options


def build_command(version_id: str, root: Path, options: dict[str, Any]) -> list[str]:
    """`root` holds the shared versions/libraries/assets; the game folder itself comes from options["gameDirectory"]."""
    return strip_unfilled(mll.command.get_minecraft_command(version_id, str(root), options))


def strip_unfilled(command: list[str]) -> list[str]:
    """Drop "--flag ${placeholder}" pairs the library leaves unfilled (--clientId, --xuid: telemetry only)."""
    out: list[str] = []
    i = 0
    while i < len(command):
        arg = command[i]
        nxt = command[i + 1] if i + 1 < len(command) else None
        if arg.startswith("--") and nxt is not None and nxt.startswith("${") and nxt.endswith("}"):
            i += 2
            continue
        out.append(arg)
        i += 1
    return out


def redact(command: list[str], secrets: list[str]) -> list[str]:
    """A copy safe to show in Logs: the access token (and anything else secret) replaced with ********."""
    out = []
    hide_next = False
    for arg in command:
        if hide_next:
            out.append(REDACTED)
            hide_next = False
            continue
        if arg in ("--accessToken", "--session"):
            out.append(arg)
            hide_next = True
            continue
        for s in secrets:
            if s and s in arg:
                arg = arg.replace(s, REDACTED)
        out.append(arg)
    return out


def start(command: list[str], game_dir: Path, capture_output: bool) -> subprocess.Popen:
    """Start the game without a console window. With capture_output the game's log lines can be read from
    .stdout; otherwise the output is discarded (used when the launcher closes after starting the game)."""
    flags = 0
    if sys.platform == "win32":
        flags = subprocess.CREATE_NO_WINDOW | subprocess.CREATE_NEW_PROCESS_GROUP
    return subprocess.Popen(
        command,
        cwd=str(game_dir),
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE if capture_output else subprocess.DEVNULL,
        stderr=subprocess.STDOUT if capture_output else subprocess.DEVNULL,
        text=capture_output,
        encoding="utf-8" if capture_output else None,
        errors="replace" if capture_output else None,
        creationflags=flags,
    )
