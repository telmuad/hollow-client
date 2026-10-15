# PyInstaller spec for HollowClient.exe (one folder: dist\HollowClient\HollowClient.exe).
# Build with launcher\build.bat, which runs:  python -m PyInstaller --noconfirm --clean hollow.spec
from pathlib import Path

from PyInstaller.utils.hooks import collect_data_files, collect_submodules

root = Path(SPECPATH)

datas = [(str(root / "assets"), "assets")]
# the Qt Quick window: QML files and the compiled nebula shader (looked up next to the hollow_launcher package)
datas += [(str(root / "hollow_launcher" / "qml"), "hollow_launcher/qml")]
datas += collect_data_files("minecraft_launcher_lib")  # it reads its own version.txt
jars = [j for j in (root / "bundled").glob("hollowclient-*.jar") if not j.name.endswith("-sources.jar")]
datas += [(str(j), "bundled") for j in jars]

hiddenimports = ["keyring.backends.Windows", "PySide6.QtQml", "PySide6.QtQuick"] + collect_submodules("win32ctypes")

a = Analysis(
    [str(root / "run_launcher.py")],
    pathex=[str(root)],
    datas=datas,
    hiddenimports=hiddenimports,
    excludes=["tkinter", "unittest", "pydoc", "pytest", "_pytest", "setuptools", "pip", "PySide6.QtWidgets"],
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="HollowClient",
    icon=str(root / "assets" / "icon.ico"),
    console=False,
    upx=False,
)
coll = COLLECT(exe, a.binaries, a.datas, name="HollowClient", upx=False)
