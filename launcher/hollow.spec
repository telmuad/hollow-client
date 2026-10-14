# PyInstaller spec for HollowClient.exe (one folder: dist\HollowClient\HollowClient.exe).
# Build with launcher\build.bat, which runs:  python -m PyInstaller --noconfirm --clean hollow.spec
from pathlib import Path

from PyInstaller.utils.hooks import collect_data_files, collect_submodules

root = Path(SPECPATH)

datas = [(str(root / "assets"), "assets")]
datas += collect_data_files("minecraft_launcher_lib")  # it reads its own version.txt
jars = [j for j in (root / "bundled").glob("hollowclient-*.jar") if not j.name.endswith("-sources.jar")]
datas += [(str(j), "bundled") for j in jars]

hiddenimports = ["keyring.backends.Windows"] + collect_submodules("win32ctypes")

a = Analysis(
    [str(root / "run_launcher.py")],
    pathex=[str(root)],
    datas=datas,
    hiddenimports=hiddenimports,
    excludes=["tkinter", "unittest", "pydoc", "pytest", "_pytest", "setuptools", "pip"],
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
