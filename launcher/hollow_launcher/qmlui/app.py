"""Starting the Qt Quick launcher window (or its self-test / page screenshots)."""

from __future__ import annotations

import os
import sys
from pathlib import Path

from PySide6.QtCore import QPointF, QUrl

from .. import APP_NAME, paths

QML_DIR = Path(__file__).resolve().parent.parent / "qml"
PAGES = ["home", "wardrobe", "stats", "settings", "logs"]


def intro_points(count: int = 300) -> list[list[float]]:
    """Points spread over the H sigil (x, y in -0.5..0.5) for the intro's particles to fly to."""
    from ..ui.logo import sigil_path

    path = sigil_path(1.0)
    pts = []
    step = 0.012
    y = 0.0
    while y <= 1.0:
        x = 0.0
        while x <= 1.0:
            if path.contains(QPointF(x, y)):
                pts.append([round(x - 0.5, 4), round(y - 0.5, 4)])
            x += step
        y += step
    if len(pts) > count:
        stride = len(pts) / count
        pts = [pts[int(i * stride)] for i in range(count)]
    return pts


def sigil_file(game_dir: Path) -> Path:
    """The sigil without a background, rendered once into the launcher cache for QML to show."""
    from ..ui.logo import render

    target = game_dir / "cache" / "sigil_512.png"
    if not target.is_file():
        target.parent.mkdir(parents=True, exist_ok=True)
        render(512, background=False).save(str(target))
    return target


def create_engine(backend, theme, sounds, skip_intro: bool):
    from PySide6.QtQml import QQmlApplicationEngine, qmlRegisterType

    from .skin_view import SkinView

    qmlRegisterType(SkinView, "Hollow", 1, 0, "SkinView")
    engine = QQmlApplicationEngine()
    ctx = engine.rootContext()
    fonts = paths.assets_dir() / "fonts"
    ctx.setContextProperty("backend", backend)
    ctx.setContextProperty("theme", theme)
    ctx.setContextProperty("sounds", sounds)
    ctx.setContextProperty("introPoints", intro_points())
    ctx.setContextProperty("sigilImage", QUrl.fromLocalFile(str(sigil_file(backend.game_dir))).toString())
    ctx.setContextProperty("fontMedium", QUrl.fromLocalFile(str(fonts / "Inter-Medium.ttf")).toString())
    ctx.setContextProperty("fontSemiBold", QUrl.fromLocalFile(str(fonts / "Inter-SemiBold.ttf")).toString())
    ctx.setContextProperty("fontBold", QUrl.fromLocalFile(str(fonts / "Inter-Bold.ttf")).toString())
    ctx.setContextProperty("skipIntro", skip_intro)
    engine.load(QUrl.fromLocalFile(str(QML_DIR / "Main.qml")))
    return engine


def run(selftest: bool = False, screenshots: str | None = None) -> int:
    if selftest:
        os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
        windir = os.environ.get("WINDIR", r"C:\Windows")
        os.environ.setdefault("QT_QPA_FONTDIR", os.path.join(windir, "Fonts"))
    if sys.platform == "win32":
        try:
            import ctypes

            # Own taskbar group and icon instead of python.exe's.
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("telmuad.HollowClient.Launcher")
        except Exception:
            pass

    from PySide6.QtGui import QGuiApplication, QIcon

    from .backend import Backend
    from .sounds import Sounds
    from .theme_bridge import ThemeBridge

    app = QGuiApplication.instance() or QGuiApplication(sys.argv)
    app.setApplicationName(APP_NAME)
    ico = paths.assets_dir() / "icon.ico"
    if ico.is_file():
        app.setWindowIcon(QIcon(str(ico)))
    offline = selftest or bool(screenshots)
    backend = Backend(network=not offline)
    theme = ThemeBridge(backend.game_dir / "config" / "hollowclient" / "theme.json")
    sounds = Sounds(paths.assets_dir() / "sounds", enabled=backend.settings.ui_sounds and not offline)
    engine = create_engine(backend, theme, sounds, skip_intro=offline)
    roots = engine.rootObjects()
    if not roots:
        print("SELFTEST FAILED: the QML window didn't load", file=sys.stderr)
        return 1
    window = roots[0]
    if selftest:
        return _selftest(app, window, backend)
    if screenshots:
        return _screenshots(app, window, backend, Path(screenshots))
    return app.exec()


def _pump(app, ms: int) -> None:
    from PySide6.QtCore import QElapsedTimer

    t = QElapsedTimer()
    t.start()
    while t.elapsed() < ms:
        app.processEvents()


def _find_item(item, name: str):
    """Find a QML item by objectName in the visual tree (Loader-created items aren't QObject children)."""
    if item.objectName() == name:
        return item
    for child in item.childItems():
        found = _find_item(child, name)
        if found is not None:
            return found
    return None


def _selftest(app, window, backend) -> int:
    """Used to check a packaged build: the window loads, the shader and fonts are there, the mod jar is bundled."""
    problems = []
    _pump(app, 300)
    if app.windowIcon().isNull():
        problems.append("window icon missing (assets/icon.ico)")
    if not (QML_DIR / "shaders" / "nebula.frag.qsb").is_file():
        problems.append("compiled shader missing (qml/shaders/nebula.frag.qsb)")
    jar = paths.find_mod_jar()
    if jar is None:
        problems.append("Hollow Client mod jar not found (bundled/ or mod/build/libs)")
    if paths.is_frozen() and (jar is None or not str(jar).startswith(str(paths.bundle_dir()))):
        problems.append("the packaged app has no bundled mod jar")
    try:
        import keyring  # noqa: F401
        import minecraft_launcher_lib  # noqa: F401
        import requests  # noqa: F401

        from ..token_store import default_backend

        kr = default_backend().get_keyring()
        if "fail" in type(kr).__name__.lower():
            problems.append(f"no usable keyring backend ({type(kr).__name__})")
    except Exception as e:
        problems.append(f"import failed: {e}")
    print(f"{APP_NAME} self-test: frozen={paths.is_frozen()} jar={jar.name if jar else None}")
    for p in problems:
        print(f"PROBLEM: {p}")
    print("SELFTEST OK" if not problems else "SELFTEST FAILED")
    window.close()
    return 0 if not problems else 1


def _screenshots(app, window, backend, folder: Path) -> int:
    """Saves a PNG of every page (the real window on screen, so the shaders render)."""
    folder.mkdir(parents=True, exist_ok=True)
    window.setProperty("instant", True)
    _pump(app, 1200)
    for i, name in enumerate(PAGES):
        window.setProperty("page", i)
        _pump(app, 900)
        window.grabWindow().save(str(folder / f"{i + 1}_{name}.png"))
    window.setProperty("page", 0)
    _pump(app, 700)
    from PySide6.QtCore import QMetaObject

    play = _find_item(window.contentItem(), "playButton")
    if play is not None:
        backend.play = lambda: None  # the burst only; don't start an install from the screenshot run
        QMetaObject.invokeMethod(play, "chargeUp")
        _pump(app, 380)
        window.grabWindow().save(str(folder / "7_play_charging.png"))
        _pump(app, 260)
        window.grabWindow().save(str(folder / "8_play_burst.png"))
        _pump(app, 900)
    QMetaObject.invokeMethod(window, "playIntro")
    _pump(app, 650)
    window.grabWindow().save(str(folder / "9_intro_gathering.png"))
    _pump(app, 950)
    window.grabWindow().save(str(folder / "10_intro_logo.png"))
    _pump(app, 1600)
    from ..auth import NOT_APPROVED_MESSAGE, LoginError

    backend.login_failed(LoginError("not_approved", NOT_APPROVED_MESSAGE), silent=True)
    _pump(app, 700)
    window.grabWindow().save(str(folder / "6_home_banner.png"))
    backend.dismissBanner()
    # an install in progress (simulated: only the UI state, nothing is downloaded)
    backend._set_busy(True, "Installing...")
    backend._on_status("Downloading Sodium 0.8.14")
    backend._on_progress(58, 100)
    _pump(app, 900)
    window.grabWindow().save(str(folder / "11_installing.png"))
    backend._set_busy(False)
    print(f"Saved screenshots to {folder}")
    window.close()
    return 0
