"""The Qt Quick launcher: backend logic, the shared theme file, stats, the skin viewer, and that the QML loads cleanly.
Runs off-screen with a temporary game folder and an in-memory keyring."""

import json
import os
import time
from datetime import date, datetime

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import pytest  # noqa: E402

pytest.importorskip("PySide6")

from PySide6.QtCore import qInstallMessageHandler  # noqa: E402
from PySide6.QtGui import QColor, QGuiApplication, QImage, QPainter  # noqa: E402

from hollow_launcher.auth import NOT_APPROVED_MESSAGE, LoginError  # noqa: E402
from hollow_launcher.qmlui import backend as backend_mod  # noqa: E402
from hollow_launcher.qmlui import stats  # noqa: E402
from hollow_launcher.qmlui.skin_view import Box, SkinView, face_quads, is_slim, placeholder_skin  # noqa: E402
from hollow_launcher.qmlui.theme_bridge import PRESETS, ThemeBridge, parse_color, read_theme_file, theme_from_json  # noqa: E402
from hollow_launcher.token_store import MemoryBackend, TokenStore  # noqa: E402


@pytest.fixture(scope="module")
def app():
    return QGuiApplication.instance() or QGuiApplication([])


@pytest.fixture
def be(app, tmp_path, monkeypatch):
    monkeypatch.setenv("APPDATA", str(tmp_path))
    b = backend_mod.Backend(network=False, store=TokenStore(MemoryBackend()))
    b.dialogs = []
    b.dialog.connect(lambda t, m: b.dialogs.append((t, m)))
    return b


# ---------------------------------------------------------------------------------------------------- backend


def test_not_installed_state(be):
    assert "Not installed yet" in be.status
    assert be.playSub.startswith("Installs first")
    assert be.canPlay
    assert be.versions == [{"name": "Minecraft", "value": "not installed yet"}]


def test_play_without_config_explains_setup(be, monkeypatch):
    monkeypatch.setattr(be.installer, "is_installed", lambda: True)
    monkeypatch.setattr(be, "_hollow_outdated", lambda: False)
    be.play()
    assert be.dialogs and "config.json" in be.dialogs[-1][1]
    assert be.statusKind == "error"


def test_not_approved_shows_the_exact_message(be):
    be.login_failed(LoginError("not_approved", NOT_APPROVED_MESSAGE), silent=False)
    assert be.bannerText == NOT_APPROVED_MESSAGE
    assert be.bannerKind == "warn"
    assert be.dialogs[-1][1] == NOT_APPROVED_MESSAGE
    be.dismissBanner()
    assert be.bannerText == ""


def test_logout_clears_the_stored_token(be):
    be.store.save_refresh_token("abc")
    be.logout()
    assert be.store.load_refresh_token() is None
    assert be.accountName == ""


def test_settings_are_saved(be):
    be.setRam(6144)
    be.setJvmArgs("-XX:+UseZGC")
    be.setLauncherOption("show_intro", False)
    be.setLauncherOption("not_a_setting", False)
    saved = json.loads((be.game_dir / "launcher_settings.json").read_text())
    assert saved["ram_mb"] == 6144 and saved["jvm_args"] == "-XX:+UseZGC" and saved["show_intro"] is False
    assert be.ramMb == 6144 and not be.showIntro


def test_install_steps_follow_the_status_lines():
    steps = [backend_mod.install_step(s) for s in (
        "Installing Minecraft 1.21.11", "Installing Fabric Loader", "Checking Fabric API and Sodium on Modrinth",
        "Downloading Sodium 0.8.14", "Installing the Hollow Client mod", "Everything is installed")]
    assert steps == [0, 1, 2, 2, 3, 4]


def test_pretty_versions():
    assert backend_mod.pretty_version("mc1.21.11-0.8.14-fabric") == "0.8.14"
    assert backend_mod.pretty_version("0.141.6+1.21.11") == "0.141.6"


def test_log_model_kinds_and_limit(be):
    m = be.logs
    m.clear()
    m.append("[12:00:00] [Render thread/ERROR]: boom")
    m.append("[12:00:01] [Hollow] hello")
    assert [k for _, k in m.lines] == ["error", "hollow"]
    for i in range(backend_mod.MAX_LOG_LINES + 10):
        m.append(f"line {i}")
    assert len(m.lines) <= backend_mod.MAX_LOG_LINES
    assert m.lines[-1][0] == f"line {backend_mod.MAX_LOG_LINES + 9}"


def test_cosmetics_come_from_the_mod_config(be):
    cfg = be.game_dir / "config" / "hollowclient.json"
    cfg.parent.mkdir(parents=True)
    capes = be.game_dir / "config" / "hollowclient" / "capes"
    capes.mkdir(parents=True)
    (capes / "mine.png").write_bytes(b"png")
    cfg.write_text(json.dumps({"modules": {
        "cape": {"enabled": True, "settings": {"cape": "custom:mine.png"}},
        "halo": {"enabled": True, "settings": {"themeColor": False, "tint": "#FF00FF00"}},
        "wings": {"enabled": False}}}))
    c = be.cosmetics
    assert c["cape"].endswith("mine.png")
    assert c["halo"] == "#FF00FF00"
    assert c["wings"] == ""


def test_a_cape_outside_the_capes_folder_is_ignored(be):
    cfg = be.game_dir / "config" / "hollowclient.json"
    cfg.parent.mkdir(parents=True)
    cfg.write_text(json.dumps({"modules": {"cape": {"enabled": True, "settings": {"cape": "custom:../../secret.png"}}}}))
    (be.game_dir / "config" / "secret.png").write_bytes(b"x")  # exactly where ../../ points
    assert be.cosmetics["cape"] == ""


# ---------------------------------------------------------------------------------------------------- theme


def test_theme_file_round_trip_keeps_custom_themes(app, tmp_path):
    f = tmp_path / "theme.json"
    f.write_text(json.dumps({"version": 1, "current": PRESETS[0].to_json(), "custom": [{"name": "Mine", "accent": "#FF112233"}]}))
    bridge = ThemeBridge(f, poll=False)
    assert "Mine" in bridge.names
    assert bridge.applyPreset("Inferno")
    data = json.loads(f.read_text())
    assert data["current"]["name"] == "Inferno"
    assert data["custom"][0]["name"] == "Mine"
    assert bridge.applyPreset("mine")
    assert bridge.accent == QColor("#112233")
    assert not bridge.applyPreset("Nope")


def test_theme_follows_the_file(app, tmp_path):
    f = tmp_path / "theme.json"
    bridge = ThemeBridge(f, poll=False)
    assert bridge.name == "Void"
    seen = []
    bridge.changed.connect(lambda: seen.append(1))
    time.sleep(0.01)
    f.write_text(json.dumps({"current": {"name": "Frost", "accent": "#7DD3FC", "roundness": 8, "animationSpeed": 9}}))
    bridge._poll()
    assert bridge.name == "Frost" and seen
    assert bridge.radius == 20
    assert bridge.speed == 3.0  # clamped like the mod does


def test_bad_theme_values_fall_back():
    t = theme_from_json({"accent": "nope", "glow": "x", "roundness": 99, "rainbow": 1})
    assert t.accent == PRESETS[0].accent and t.roundness == 8 and t.rainbow is False
    assert parse_color("#abc", 0) == 0xFFAABBCC
    assert read_theme_file(__import__("pathlib").Path("missing.json"))[0] == PRESETS[0]


# ---------------------------------------------------------------------------------------------------- stats


def test_stats_summary():
    today = date(2026, 10, 15)

    def at(d, h=20):
        return datetime(d.year, d.month, d.day, h).timestamp()

    sessions = [
        {"start": at(date(2026, 10, 15)), "seconds": 3600},
        {"start": at(date(2026, 10, 14)), "seconds": 1800},
        {"start": at(date(2026, 10, 13)), "seconds": None},   # closed the launcher on launch: counted, not timed
        {"start": at(date(2026, 10, 1)), "seconds": 600},
    ]
    s = stats.summarize(sessions, today)
    assert s["sessions"] == 4
    assert s["streak"] == 3
    assert s["longestMinutes"] == 60
    assert s["totalHours"] == 1.7
    assert len(s["chart"]) == 14 and s["chart"][-1]["minutes"] == 60.0
    assert s["chart"][0]["date"] == "2026-10-02"


def test_sessions_are_recorded(tmp_path):
    stats.record_session(tmp_path, 1000.0, 42.0)
    stats.record_session(tmp_path, 2000.0, None)
    assert stats.load_sessions(tmp_path) == [{"start": 1000.0, "seconds": 42.0}, {"start": 2000.0, "seconds": None}]


def test_folder_counts(tmp_path):
    (tmp_path / "screenshots").mkdir()
    for i in range(3):
        (tmp_path / "screenshots" / f"{i}.png").write_bytes(b"")
    (tmp_path / "saves" / "World").mkdir(parents=True)
    worlds = tmp_path / "config" / "hollowclient" / "worlds"
    worlds.mkdir(parents=True)
    (worlds / "World.json").write_text(json.dumps({"waypoints": [{}, {}]}))
    c = stats.folder_counts(tmp_path)
    assert c["screenshots"] == 3 and c["worlds"] == 1 and c["waypoints"] == 2


# ---------------------------------------------------------------------------------------------------- skin viewer


def test_box_faces_use_the_minecraft_skin_layout():
    faces = {f[0]: f[4] for f in face_quads(Box(-4, 24, -4, 8, 8, 8, 0, 0))}
    assert faces["front"] == (8, 8, 8, 8)   # the face
    assert faces["top"] == (8, 0, 8, 8)
    assert faces["right"] == (0, 8, 8, 8)
    assert faces["back"] == (24, 8, 8, 8)


def test_slim_skins_are_detected(app):
    img = QImage(64, 64, QImage.Format.Format_ARGB32)
    img.fill(QColor(200, 100, 50))
    assert not is_slim(img)
    for y in range(16, 32):
        for x in (50, 51):
            img.setPixelColor(x, y, QColor(0, 0, 0, 0))
    assert is_slim(img)
    assert placeholder_skin(QColor("#9B6CFF")).size().width() == 64


def test_viewer_draws_the_player(app):
    v = SkinView()
    v.setWidth(120)
    v.setHeight(240)
    v.setProperty("animate", False)
    img = QImage(120, 240, QImage.Format.Format_ARGB32)
    img.fill(QColor(0, 0, 0, 0))
    p = QPainter(img)
    v.paint(p)
    p.end()
    # the body is drawn in the middle
    assert img.pixelColor(60, 120).alpha() == 255


# ---------------------------------------------------------------------------------------------------- QML


def test_qml_loads_without_errors(app, be, tmp_path):
    from hollow_launcher.qmlui.app import create_engine
    from hollow_launcher.qmlui.sounds import Sounds

    messages = []
    qInstallMessageHandler(lambda mode, ctx, msg: messages.append(msg))
    try:
        theme = ThemeBridge(tmp_path / "theme.json", poll=False)
        sounds = Sounds(tmp_path, enabled=False)
        engine = create_engine(be, theme, sounds, skip_intro=True)
        assert engine.rootObjects(), "Main.qml didn't load"
        window = engine.rootObjects()[0]
        for i in range(5):
            window.setProperty("page", i)
            app.processEvents()
        window.close()
    finally:
        qInstallMessageHandler(None)
    problems = [m for m in messages if ".qml" in m and "ShaderEffect" not in m]
    assert not problems, problems


def test_a_rebuilt_hollow_jar_counts_as_an_update(be):
    from hollow_launcher import paths
    from hollow_launcher.installer import write_manifest

    bundled = paths.find_mod_jar()
    if bundled is None:
        pytest.skip("no mod jar built")
    mods = be.game_dir / "mods"
    mods.mkdir(parents=True)
    (mods / bundled.name).write_bytes(b"an older build with the same version number")
    write_manifest(mods, {"projects": {}, "hollowclient": {"filename": bundled.name, "sha1": "old"}})
    be.refresh()
    assert be._hollow_outdated()
    assert [r for r in be.modRows if r["name"] == "Hollow Client"][0]["state"] == "update"
    assert be.updatesAvailable
    (mods / bundled.name).write_bytes(bundled.read_bytes())
    be.refresh()
    assert not be._hollow_outdated()
