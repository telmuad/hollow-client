"""Version profiles, instance folders, the 1.x -> 2.x migration and the overlapping-mod scan."""

import json
import zipfile

import pytest

from hollow_launcher import conflicts, migration, paths, profiles
from hollow_launcher.qmlui.theme_bridge import ThemeBridge, read_theme_file


def write_jar(path, mod: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("fabric.mod.json", json.dumps(mod))
    return path


# ---------------------------------------------------------------------------------------------------- profiles


def test_bundled_profiles():
    all_profiles, default = profiles.load()
    ids = [p.id for p in all_profiles]
    assert default == "hollow-26.2" and ids == ["hollow-26.2", "legacy-1.21.11"]
    main = profiles.by_id(all_profiles, None, default)
    assert main.minecraft == "26.2" and main.hypixel
    assert {m.name for m in main.mods} == {"Fabric API", "Sodium", "Hypixel Mod API"}
    legacy = profiles.by_id(all_profiles, "legacy-1.21.11", default)
    assert legacy.minecraft == "1.21.11" and not legacy.hypixel
    assert "Hypixel Mod API" not in legacy.mod_names().values()
    assert profiles.by_id(all_profiles, "gone", default) is main


def test_instance_folders(tmp_path):
    all_profiles, _ = profiles.load()
    dirs = {p.instance_dir(tmp_path) for p in all_profiles}
    assert dirs == {tmp_path / "instances" / "hollow-26.2", tmp_path / "instances" / "legacy-1.21.11"}


def test_jars_are_matched_by_their_minecraft_version(tmp_path):
    write_jar(tmp_path / "hollowclient-2.0.0.jar", {"id": "hollowclient", "depends": {"minecraft": "~26.2"}})
    write_jar(tmp_path / "hollowclient-1.1.0.jar", {"id": "hollowclient", "depends": {"minecraft": "~1.21.11"}})
    assert paths.find_mod_jar([tmp_path], minecraft="26.2").name == "hollowclient-2.0.0.jar"
    assert paths.find_mod_jar([tmp_path], minecraft="1.21.11").name == "hollowclient-1.1.0.jar"
    assert paths.find_mod_jar([tmp_path], minecraft="26.3") is None
    assert paths.find_mod_jar([tmp_path]).name == "hollowclient-2.0.0.jar"


def test_a_folder_without_the_right_jar_is_skipped(tmp_path):
    main, legacy = tmp_path / "main", tmp_path / "legacy"
    write_jar(main / "hollowclient-2.0.0.jar", {"depends": {"minecraft": "~26.2"}})
    write_jar(legacy / "hollowclient-1.1.0.jar", {"depends": {"minecraft": "~1.21.11"}})
    assert paths.find_mod_jar([main, legacy], minecraft="1.21.11").parent == legacy


# ---------------------------------------------------------------------------------------------------- migration


def old_install(root):
    """A launcher 1.x folder: everything in the root."""
    for d in ("mods", "config/hollowclient", "saves/World", "versions/x", "libraries/a", "assets/indexes", "runtime/j",
              "screenshots", "resourcepacks"):
        (root / d).mkdir(parents=True, exist_ok=True)
    (root / "hollow_install.json").write_text('{"version_id": "fabric-loader-0.19.5-1.21.11"}')
    (root / "options.txt").write_text("fov:0.5")
    (root / "mods" / "sodium.jar").write_bytes(b"s")
    (root / "launcher_settings.json").write_text("{}")
    (root / "account.json").write_text("{}")
    (root / "launcher.log").write_text("log")


def test_migration_moves_the_game_files_and_keeps_shared_ones(tmp_path):
    old_install(tmp_path)
    assert migration.needs_migration(tmp_path)
    moved = migration.migrate(tmp_path)
    legacy = tmp_path / "instances" / "legacy-1.21.11"
    assert set(moved) == {"config", "hollow_install.json", "mods", "options.txt", "resourcepacks", "saves", "screenshots"}
    assert (legacy / "mods" / "sodium.jar").read_bytes() == b"s"
    assert (legacy / "options.txt").read_text() == "fov:0.5"
    assert (legacy / "saves" / "World").is_dir()
    for shared in ("versions", "libraries", "assets", "runtime", "launcher_settings.json", "account.json", "launcher.log"):
        assert (tmp_path / shared).exists(), shared
    assert (tmp_path / "migration.txt").is_file()
    assert not migration.needs_migration(tmp_path)
    assert migration.migrate(tmp_path) == [], "runs once"


def test_fresh_install_needs_no_migration(tmp_path):
    assert not migration.needs_migration(tmp_path)
    (tmp_path / "launcher_settings.json").write_text("{}")
    assert migration.migrate(tmp_path) == []
    assert not (tmp_path / "instances").exists()


def test_migration_never_overwrites(tmp_path):
    old_install(tmp_path)
    legacy = tmp_path / "instances" / "legacy-1.21.11"
    (tmp_path / "instances").mkdir()
    # an instance that already exists means there's nothing to migrate
    legacy.mkdir()
    assert migration.migrate(tmp_path) == []
    assert (tmp_path / "options.txt").is_file()


def test_the_installed_legacy_profile_is_recognised_after_migration(tmp_path):
    from hollow_launcher.installer import Installer

    old_install(tmp_path)
    vid = "fabric-loader-0.19.5-1.21.11"
    (tmp_path / "versions" / vid).mkdir(parents=True)
    (tmp_path / "versions" / vid / f"{vid}.json").write_text("{}")
    migration.migrate(tmp_path)
    all_profiles, default = profiles.load()
    inst = Installer(tmp_path, profiles.by_id(all_profiles, "legacy-1.21.11", default), progress=None)
    assert inst.version_id() == vid


# ---------------------------------------------------------------------------------------------------- conflicts


def test_overlapping_mods_are_found_by_mod_id(tmp_path):
    mods = tmp_path / "mods"
    write_jar(mods / "renamed.jar", {"id": "skyblocker"})
    write_jar(mods / "sh.jar", {"id": "skyhanni"})
    write_jar(mods / "sodium.jar", {"id": "sodium"})
    write_jar(mods / "off.jar.disabled", {"id": "firmament"})
    (mods / "broken.jar").write_bytes(b"not a zip")
    assert conflicts.find(mods) == ["SkyHanni", "Skyblocker"]
    assert "SkyHanni and Skyblocker are" in conflicts.message(conflicts.find(mods))
    assert conflicts.find(tmp_path / "missing") == [] and conflicts.message([]) == ""


# ---------------------------------------------------------------------------------------------------- theme sync


def theme_json(name):
    return {"version": 1, "current": {"name": name, "accent": "#FF7C5CFF", "start": "#FF7C5CFF", "end": "#FF22D3EE"},
            "custom": []}


def test_theme_sync_across_instances(tmp_path):
    a = tmp_path / "a" / "config" / "hollowclient" / "theme.json"
    b = tmp_path / "b" / "config" / "hollowclient" / "theme.json"
    never = tmp_path / "never" / "config" / "hollowclient" / "theme.json"
    for f in (a, b):
        f.parent.mkdir(parents=True)
    a.write_text(json.dumps(theme_json("Void")))
    b.write_text(json.dumps(theme_json("Inferno")))
    import os
    os.utime(b, ns=(a.stat().st_mtime_ns + 10**9,) * 2)
    bridge = ThemeBridge([a, b, never], poll=False)
    assert bridge.theme.name == "Inferno", "the newest file wins"
    assert read_theme_file(a)[0].name == "Inferno", "and is copied to the other instance"
    assert not never.exists(), "profiles that never ran are left alone"
    assert bridge.applyPreset("SkyBlock")
    assert read_theme_file(a)[0].name == read_theme_file(b)[0].name == "SkyBlock"
    assert not never.exists()


# ---------------------------------------------------------------------------------------------------- backend


@pytest.fixture
def be(tmp_path, monkeypatch):
    from PySide6.QtGui import QGuiApplication

    from hollow_launcher.qmlui import backend as backend_mod
    from hollow_launcher.token_store import MemoryBackend, TokenStore

    QGuiApplication.instance() or QGuiApplication([])
    monkeypatch.setenv("APPDATA", str(tmp_path))
    return backend_mod.Backend(network=False, store=TokenStore(MemoryBackend()))


def test_switching_profiles(be, tmp_path):
    root = tmp_path / ".hollowclient"
    assert be.root == root and be.game_dir == root / "instances" / "hollow-26.2"
    assert [r["name"] for r in be.modRows] == ["Fabric API", "Sodium", "Hypixel Mod API", "Hollow Client"]
    assert [p["id"] for p in be.profileList] == ["hollow-26.2", "legacy-1.21.11"]
    be.selectProfile("legacy-1.21.11")
    assert be.profileId == "legacy-1.21.11" and be.mcVersion == "1.21.11" and not be.profileHypixel
    assert be.game_dir == root / "instances" / "legacy-1.21.11"
    assert [r["name"] for r in be.modRows] == ["Fabric API", "Sodium", "Hollow Client"]
    assert json.loads((root / "launcher_settings.json").read_text())["profile"] == "legacy-1.21.11"
    be.selectProfile("does-not-exist")
    assert be.profileId == "legacy-1.21.11"


def test_switching_is_blocked_while_busy(be):
    be.busy = True
    be.selectProfile("legacy-1.21.11")
    assert be.profileId == "hollow-26.2"


def test_join_hypixel_switches_profile_and_passes_the_server(be, monkeypatch):
    from hollow_launcher import launch

    be.selectProfile("legacy-1.21.11")
    seen = {}

    def fake_play(server):
        seen["server"] = server
        seen["profile"] = be.profile.id

    monkeypatch.setattr(be, "_play", fake_play)
    be.joinHypixel()
    assert seen == {"server": launch.HYPIXEL, "profile": "hollow-26.2"}


def test_launch_uses_the_instance_folder_and_quick_play(be, monkeypatch):
    from hollow_launcher import launch
    from hollow_launcher.auth import Account

    be.account = Account(uuid="0" * 32, name="Player", access_token="tok", refresh_token="r", skin_url=None,
                         obtained_at=0)
    monkeypatch.setattr(be.installer, "version_id", lambda: "fabric-loader-0.19.5-26.2")
    captured = {}

    def fake_command(version_id, root, options):
        captured.update(version_id=version_id, root=root, options=options)
        return ["java", "Main"]

    class FakeProcess:
        def poll(self):
            return None

    monkeypatch.setattr(launch, "build_command", fake_command)
    monkeypatch.setattr(launch, "start", lambda cmd, cwd, capture_output: captured.update(cwd=cwd) or FakeProcess())
    be.settings.close_on_launch = True
    monkeypatch.setattr("hollow_launcher.qmlui.backend.QTimer.singleShot", lambda *a: None)
    be._server = launch.HYPIXEL
    be._launch()
    assert captured["root"] == be.root
    assert captured["options"]["gameDirectory"] == str(be.game_dir) and captured["cwd"] == be.game_dir
    assert captured["options"]["quickPlayMultiplayer"] == "mc.hypixel.net"
    assert be._server is None, "only this launch joins Hypixel"


def test_conflict_banner(be):
    write_jar(be.game_dir / "mods" / "x.jar", {"id": "firmament"})
    be.refresh()
    assert "Firmament is in this profile" in be.conflictText


def test_first_play_copies_settings_from_the_other_profile(tmp_path):
    from hollow_launcher.installer import Installer

    all_profiles, default = profiles.load()
    legacy = profiles.by_id(all_profiles, "legacy-1.21.11", default).instance_dir(tmp_path)
    (legacy / "config" / "hollowclient" / "capes").mkdir(parents=True)
    (legacy / "config" / "hollowclient.json").write_text('{"modules": {"discord": {"enabled": true}}}')
    (legacy / "config" / "hollowclient" / "capes" / "mine.png").write_bytes(b"png")
    (legacy / "options.txt").write_text("fov:0.5")
    (legacy / "config" / "sodium-options.json").write_text("{}")  # other mods' config stays per version
    main = Installer(tmp_path, profiles.by_id(all_profiles, "hollow-26.2", default), progress=QuietLog())
    others = [legacy, tmp_path / "instances" / "missing"]
    assert main.seed_settings(others) == ["config/hollowclient.json", "config/hollowclient", "options.txt"]
    game = main.game_dir
    assert (game / "config" / "hollowclient" / "capes" / "mine.png").read_bytes() == b"png"
    assert (game / "options.txt").read_text() == "fov:0.5"
    assert not (game / "config" / "sodium-options.json").exists()
    (game / "options.txt").write_text("fov:1.0")
    assert main.seed_settings(others) == [], "only the first time"
    assert (game / "options.txt").read_text() == "fov:1.0"


class QuietLog:
    def status(self, t):
        pass

    def progress(self, c, m):
        pass

    def log(self, t):
        pass
