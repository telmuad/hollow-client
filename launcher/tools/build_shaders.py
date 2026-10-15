"""Compiles the launcher's QML shaders (hollow_launcher/qml/shaders/*.frag) into .qsb files with Qt's qsb tool.

The compiled .qsb files are committed, so building or running the launcher never needs this. It is only for
changing a shader. qsb ships in the PySide6-Addons wheel (not needed at runtime):

    pip install PySide6-Addons==6.11.2
    python tools/build_shaders.py

or point HOLLOW_QSB at a qsb.exe.
"""

import os
import shutil
import subprocess
import sys
from pathlib import Path

SHADERS = Path(__file__).resolve().parents[1] / "hollow_launcher" / "qml" / "shaders"


def find_qsb() -> str:
    if os.environ.get("HOLLOW_QSB"):
        return os.environ["HOLLOW_QSB"]
    try:
        import PySide6

        candidate = Path(PySide6.__file__).parent / ("qsb.exe" if sys.platform == "win32" else "qsb")
        if candidate.is_file():
            return str(candidate)
    except ImportError:
        pass
    found = shutil.which("qsb") or shutil.which("pyside6-qsb")
    if not found:
        sys.exit("qsb not found: install PySide6-Addons or set HOLLOW_QSB")
    return found


def main() -> None:
    qsb = find_qsb()
    for src in sorted(SHADERS.glob("*.frag")) + sorted(SHADERS.glob("*.vert")):
        out = src.with_name(src.name + ".qsb")
        # GLSL for OpenGL, HLSL for Direct3D 11 (Qt's default on Windows), MSL for macOS
        cmd = [qsb, "--glsl", "100 es,120,150", "--hlsl", "50", "--msl", "12", "-o", str(out), str(src)]
        subprocess.run(cmd, check=True)
        print("compiled", out.name)


if __name__ == "__main__":
    main()
