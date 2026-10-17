"""Launcher settings, saved to %APPDATA%\\.hollowclient\\launcher_settings.json."""

from __future__ import annotations

from dataclasses import asdict, dataclass, fields
from pathlib import Path

from .jsonfile import read_json, write_json_atomic

MIN_RAM_MB = 1024
MAX_RAM_MB = 65536
RAM_STEP_MB = 512
DEFAULT_RAM_MB = 4096
MAX_JVM_ARGS_LEN = 2000


@dataclass
class LauncherSettings:
    ram_mb: int = DEFAULT_RAM_MB
    jvm_args: str = ""
    resolution_width: int = 0  # 0 = Minecraft's default window size
    resolution_height: int = 0
    close_on_launch: bool = False
    last_update_check: float = 0.0
    # the launcher window itself
    animated_background: bool = True
    ui_sounds: bool = True
    show_intro: bool = True
    # the selected version profile (profiles.json id); empty = the default profile
    profile: str = ""

    def sanitize(self) -> "LauncherSettings":
        self.ram_mb = clamp_ram(self.ram_mb)
        self.jvm_args = self.jvm_args.replace("\r", " ").replace("\n", " ").strip()[:MAX_JVM_ARGS_LEN]
        if not (self.resolution_width and self.resolution_height):
            self.resolution_width = self.resolution_height = 0
        else:
            self.resolution_width = max(320, min(7680, self.resolution_width))
            self.resolution_height = max(240, min(4320, self.resolution_height))
        self.last_update_check = max(0.0, float(self.last_update_check))
        self.profile = str(self.profile or "")[:64]
        return self

    @classmethod
    def load(cls, path: Path) -> "LauncherSettings":
        """Load, falling back to defaults for anything missing or of the wrong type. Never raises."""
        data, _ok = read_json(path)
        s = cls()
        if isinstance(data, dict):
            for f in fields(cls):
                if f.name not in data:
                    continue
                value = data[f.name]
                default = getattr(s, f.name)
                # bool is a subclass of int, so check it first
                if isinstance(default, bool):
                    if isinstance(value, bool):
                        setattr(s, f.name, value)
                elif isinstance(default, int):
                    if isinstance(value, (int, float)) and not isinstance(value, bool):
                        setattr(s, f.name, int(value))
                elif isinstance(default, float):
                    if isinstance(value, (int, float)) and not isinstance(value, bool):
                        setattr(s, f.name, float(value))
                elif isinstance(default, str):
                    if isinstance(value, str):
                        setattr(s, f.name, value)
        return s.sanitize()

    def save(self, path: Path) -> None:
        write_json_atomic(path, asdict(self.sanitize()))


def clamp_ram(mb: int) -> int:
    mb = int(mb)
    mb = max(MIN_RAM_MB, min(MAX_RAM_MB, mb))
    return int(round(mb / RAM_STEP_MB) * RAM_STEP_MB)


def total_ram_mb() -> int:
    """Physical memory of this PC in MB (Windows), or 16 GB if it can't be read."""
    try:
        import ctypes

        class MEMORYSTATUSEX(ctypes.Structure):
            _fields_ = [
                ("dwLength", ctypes.c_ulong),
                ("dwMemoryLoad", ctypes.c_ulong),
                ("ullTotalPhys", ctypes.c_ulonglong),
                ("ullAvailPhys", ctypes.c_ulonglong),
                ("ullTotalPageFile", ctypes.c_ulonglong),
                ("ullAvailPageFile", ctypes.c_ulonglong),
                ("ullTotalVirtual", ctypes.c_ulonglong),
                ("ullAvailVirtual", ctypes.c_ulonglong),
                ("sullAvailExtendedVirtual", ctypes.c_ulonglong),
            ]

        stat = MEMORYSTATUSEX()
        stat.dwLength = ctypes.sizeof(MEMORYSTATUSEX)
        if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(stat)):  # type: ignore[attr-defined]
            return int(stat.ullTotalPhys // (1024 * 1024))
    except (AttributeError, OSError, ValueError):
        pass
    return 16384


def max_ram_for_slider(total_mb: int) -> int:
    """Leave about 2 GB for Windows; never below 2 GB or above the hard maximum."""
    return clamp_ram(max(2048, min(MAX_RAM_MB, total_mb - 2048)))
