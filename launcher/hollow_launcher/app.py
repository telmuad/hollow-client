"""Starting the Qt app (window, self-test, or page screenshots)."""

from __future__ import annotations

import os
import sys
from pathlib import Path

from . import APP_NAME, paths


def run(selftest: bool = False, screenshots: str | None = None) -> int:
    if selftest or screenshots:
        os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
        # The offscreen platform has no font list of its own; give it the Windows fonts.
        windir = os.environ.get("WINDIR", r"C:\Windows")
        os.environ.setdefault("QT_QPA_FONTDIR", os.path.join(windir, "Fonts"))
    if sys.platform == "win32":
        try:
            import ctypes

            # Own taskbar group and icon instead of python.exe's.
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("telmuad.HollowClient.Launcher")
        except Exception:
            pass

    from PySide6.QtWidgets import QApplication

    from .ui import theme
    from .ui.main_window import MainWindow

    app = QApplication.instance() or QApplication(sys.argv)
    app.setApplicationName(APP_NAME)
    app.setStyleSheet(theme.STYLE)
    window = MainWindow(network=not (selftest or screenshots))

    if selftest:
        return _selftest(app, window)
    if screenshots:
        return _screenshots(app, window, Path(screenshots))
    window.show()
    return app.exec()


def _selftest(app, window) -> int:
    """Used to check a packaged build: the window builds, the logo/icon load, and the mod jar is bundled."""
    problems = []
    window.resize(1180, 740)
    window.show()
    app.processEvents()
    if window.windowIcon().isNull():
        problems.append("window icon missing (assets/icon.ico)")
    jar = paths.find_mod_jar()
    if jar is None:
        problems.append("Hollow Client mod jar not found (bundled/ or mod/build/libs)")
    if paths.is_frozen() and (jar is None or not str(jar).startswith(str(paths.bundle_dir()))):
        problems.append("the packaged app has no bundled mod jar")
    try:
        import keyring  # noqa: F401
        import minecraft_launcher_lib  # noqa: F401
        import requests  # noqa: F401

        from .token_store import default_backend

        backend = default_backend().get_keyring()
        if "fail" in type(backend).__name__.lower():
            problems.append(f"no usable keyring backend ({type(backend).__name__})")
    except Exception as e:
        problems.append(f"import failed: {e}")
    print(f"{APP_NAME} self-test: frozen={paths.is_frozen()} jar={jar.name if jar else None}")
    for p in problems:
        print(f"PROBLEM: {p}")
    print("SELFTEST OK" if not problems else "SELFTEST FAILED")
    window.close()
    return 0 if not problems else 1


def _screenshots(app, window, folder: Path) -> int:
    folder.mkdir(parents=True, exist_ok=True)
    window.resize(1180, 740)
    window.show()
    names = ["home", "styles_mods", "settings", "logs"]
    for i, name in enumerate(names):
        window.show_page(i)
        for _ in range(5):
            app.processEvents()
        window.grab().save(str(folder / f"{i + 1}_{name}.png"))
    # Also the "not approved yet" state, as the owner will see it until Mojang approves the app ID.
    from .auth import LoginError, NOT_APPROVED_MESSAGE

    window.show_page(0)
    window._login_failed(LoginError("not_approved", NOT_APPROVED_MESSAGE), silent=True)
    for _ in range(5):
        app.processEvents()
    window.grab().save(str(folder / "5_home_not_approved.png"))
    window.resize(980, 640)
    for _ in range(5):
        app.processEvents()
    window.grab().save(str(folder / "6_home_small_window.png"))
    print(f"Saved screenshots to {folder}")
    window.close()
    return 0
