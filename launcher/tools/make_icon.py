"""Generate launcher/assets/icon.ico (16-256 px, PNG-compressed entries) and logo.png from the code-drawn logo.

Run from the launcher folder:  .venv\\Scripts\\python tools\\make_icon.py
"""

from __future__ import annotations

import struct
import sys
from pathlib import Path

from PySide6.QtCore import QBuffer, QByteArray, QIODevice
from PySide6.QtGui import QGuiApplication

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from hollow_launcher.ui.logo import render  # noqa: E402

SIZES = [16, 24, 32, 48, 64, 128, 256]


def png_bytes(size: int) -> bytes:
    data = QByteArray()
    buf = QBuffer(data)
    buf.open(QIODevice.OpenModeFlag.WriteOnly)
    render(size).save(buf, "PNG")
    buf.close()
    return bytes(data)


def write_ico(path: Path, images: list[tuple[int, bytes]]) -> None:
    header = struct.pack("<HHH", 0, 1, len(images))
    offset = 6 + 16 * len(images)
    entries = b""
    blobs = b""
    for size, data in images:
        dim = 0 if size >= 256 else size
        entries += struct.pack("<BBBBHHII", dim, dim, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
        blobs += data
    path.write_bytes(header + entries + blobs)


def main() -> None:
    app = QGuiApplication.instance() or QGuiApplication(sys.argv)
    assets = Path(__file__).resolve().parent.parent / "assets"
    assets.mkdir(exist_ok=True)
    write_ico(assets / "icon.ico", [(s, png_bytes(s)) for s in SIZES])
    render(512).save(str(assets / "logo.png"), "PNG")
    print(f"Wrote {assets / 'icon.ico'} and logo.png")
    del app


if __name__ == "__main__":
    main()
