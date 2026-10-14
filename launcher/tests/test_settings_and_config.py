import json

from hollow_launcher import appconfig, paths
from hollow_launcher.settings import DEFAULT_RAM_MB, LauncherSettings, clamp_ram, max_ram_for_slider


def test_defaults_when_missing(tmp_path):
    s = LauncherSettings.load(tmp_path / "launcher_settings.json")
    assert s.ram_mb == DEFAULT_RAM_MB == 4096
    assert s.jvm_args == ""
    assert s.close_on_launch is False


def test_round_trip(tmp_path):
    f = tmp_path / "launcher_settings.json"
    s = LauncherSettings(ram_mb=6144, jvm_args="-XX:+UseG1GC", resolution_width=1920, resolution_height=1080, close_on_launch=True)
    s.save(f)
    back = LauncherSettings.load(f)
    assert back == s


def test_corrupt_file_gives_defaults_and_is_kept_aside(tmp_path):
    f = tmp_path / "launcher_settings.json"
    f.write_text("{ not json", encoding="utf-8")
    s = LauncherSettings.load(f)
    assert s.ram_mb == 4096
    assert not f.exists()
    assert any(p.name.startswith("launcher_settings.json.corrupt-") for p in tmp_path.iterdir())


def test_wrong_types_and_out_of_range(tmp_path):
    f = tmp_path / "launcher_settings.json"
    f.write_text(json.dumps({
        "ram_mb": 999999, "jvm_args": 5, "resolution_width": 100, "resolution_height": "big",
        "close_on_launch": "yes", "unknown": True,
    }), encoding="utf-8")
    s = LauncherSettings.load(f)
    assert s.ram_mb == 65536
    assert s.jvm_args == ""
    assert (s.resolution_width, s.resolution_height) == (0, 0), "a half-set resolution means default"
    assert s.close_on_launch is False


def test_bool_is_not_taken_as_int(tmp_path):
    f = tmp_path / "s.json"
    f.write_text(json.dumps({"ram_mb": True}), encoding="utf-8")
    assert LauncherSettings.load(f).ram_mb == 4096


def test_ram_clamping_and_slider_max():
    assert clamp_ram(100) == 1024
    assert clamp_ram(4100) == 4096
    assert clamp_ram(4400) == 4608
    assert max_ram_for_slider(16384) == 14336
    assert max_ram_for_slider(3000) == 2048


def test_resolution_is_clamped(tmp_path):
    s = LauncherSettings(resolution_width=10, resolution_height=99999).sanitize()
    assert (s.resolution_width, s.resolution_height) == (320, 4320)


def test_newlines_removed_from_jvm_args():
    s = LauncherSettings(jvm_args="-Xss2m\r\n-XX:+UseG1GC").sanitize()
    assert "\n" not in s.jvm_args and "\r" not in s.jvm_args


def test_config_missing(tmp_path):
    cfg = appconfig.load([tmp_path / "config.json"])
    assert not cfg.has_client_id
    assert cfg.source is None
    assert "config.example.json" in cfg.status_text()


def test_config_placeholder(tmp_path):
    f = tmp_path / "config.json"
    f.write_text(json.dumps({"azure_client_id": appconfig.PLACEHOLDER_ID, "redirect_uri": "http://localhost"}), encoding="utf-8")
    cfg = appconfig.load([f])
    assert not cfg.has_client_id
    assert "placeholder" in cfg.status_text()


def test_config_real_id(tmp_path):
    f = tmp_path / "config.json"
    f.write_text(json.dumps({"azure_client_id": "1a2b3c4d-1111-2222-3333-444455556666"}), encoding="utf-8-sig")
    cfg = appconfig.load([f])
    assert cfg.has_client_id
    assert cfg.redirect_uri == "http://localhost"
    assert cfg.problem is None


def test_config_bad_values(tmp_path):
    f = tmp_path / "config.json"
    f.write_text(json.dumps({"azure_client_id": "not-an-id"}), encoding="utf-8")
    assert "doesn't look like an app ID" in appconfig.load([f]).problem
    f.write_text(json.dumps({"azure_client_id": "1a2b3c4d-1111-2222-3333-444455556666", "redirect_uri": "https://example.com"}), encoding="utf-8")
    assert "redirect_uri" in appconfig.load([f]).problem
    f.write_text("[1, 2]", encoding="utf-8")
    assert "JSON object" in appconfig.load([f]).problem
    f.write_text("{oops", encoding="utf-8")
    assert "couldn't be read" in appconfig.load([f]).problem


def test_example_config_matches_the_placeholder():
    example = paths.launcher_dir() / "config.example.json"
    data = json.loads(example.read_text(encoding="utf-8"))
    assert data["azure_client_id"] == appconfig.PLACEHOLDER_ID
    assert data["redirect_uri"] == "http://localhost"


def test_find_mod_jar_picks_newest_and_skips_sources(tmp_path):
    a = tmp_path / "a"
    a.mkdir()
    for name in ["hollowclient-1.0.0.jar", "hollowclient-1.2.0-sources.jar", "hollowclient-1.10.0.jar",
                 "hollowclient-1.9.9.jar", "other-2.0.jar", "hollowclient-dev.jar"]:
        (a / name).write_bytes(b"x")
    assert paths.find_mod_jar([a]).name == "hollowclient-1.10.0.jar"


def test_find_mod_jar_prefers_first_folder(tmp_path):
    bundled, libs = tmp_path / "bundled", tmp_path / "libs"
    bundled.mkdir()
    libs.mkdir()
    (bundled / "hollowclient-1.0.0.jar").write_bytes(b"x")
    (libs / "hollowclient-2.0.0.jar").write_bytes(b"x")
    assert paths.find_mod_jar([bundled, libs]).parent == bundled
    assert paths.find_mod_jar([tmp_path / "missing", libs]).name == "hollowclient-2.0.0.jar"
    assert paths.find_mod_jar([tmp_path / "missing"]) is None


def test_game_dir_is_separate_from_dot_minecraft(monkeypatch, tmp_path):
    monkeypatch.setenv("APPDATA", str(tmp_path))
    assert paths.game_dir() == tmp_path / ".hollowclient"
    assert paths.settings_file() == tmp_path / ".hollowclient" / "launcher_settings.json"
