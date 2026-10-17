import hashlib
import json

import pytest
import requests

from hollow_launcher import installer as inst_mod
from hollow_launcher.installer import Installer, InstallError, pick_loader, read_manifest, updates_needed
from hollow_launcher.modrinth import (FABRIC_API, SODIUM, ModrinthClient, ModrinthError, parse_version,
                                      select_version)
from hollow_launcher.profiles import ModRef, Profile

TEST_PROFILE = Profile(id="test", name="Test", tagline="", minecraft="1.21.11", min_loader="0.19.5",
                       mods=(ModRef(FABRIC_API, "Fabric API"), ModRef(SODIUM, "Sodium")))


def game_of(root):
    return root / "instances" / "test"


def version_json(project, vid, number, vtype, date, game=("1.21.11",), loaders=("fabric",), deps=(), content=b"jar",
                 filename=None, primary=True):
    filename = filename or f"{project}-{number}.jar"
    return {
        "id": vid, "project_id": project, "version_number": number, "version_type": vtype, "date_published": date,
        "game_versions": list(game), "loaders": list(loaders),
        "dependencies": [{"project_id": d, "dependency_type": "required"} for d in deps],
        "files": [{"filename": filename, "url": f"https://cdn.example/{filename}", "primary": primary, "size": len(content),
                   "hashes": {"sha512": hashlib.sha512(content).hexdigest(), "sha1": hashlib.sha1(content).hexdigest()}}],
    }


# Shaped like the real API on 2026-10-13: the newest Sodium build for 1.21.11 is a beta.
SODIUM_VERSIONS = [
    version_json(SODIUM, "beta15", "mc1.21.11-0.8.15-beta.1-fabric", "beta", "2026-10-01T00:00:00Z"),
    version_json(SODIUM, "rel14", "mc1.21.11-0.8.14-fabric", "release", "2026-08-28T21:00:18Z"),
    version_json(SODIUM, "beta14", "mc1.21.11-0.8.14-beta.2-fabric", "beta", "2026-08-01T00:00:00Z"),
    version_json(SODIUM, "rel13", "mc1.21.11-0.8.13-fabric", "release", "2026-07-01T00:00:00Z"),
]


def test_release_beats_newer_beta():
    versions = [parse_version(v) for v in SODIUM_VERSIONS]
    assert select_version(versions, "1.21.11").version_id == "rel14"


def test_falls_back_to_beta_then_alpha():
    only_betas = [parse_version(v) for v in SODIUM_VERSIONS if v["version_type"] == "beta"]
    assert select_version(only_betas, "1.21.11").version_id == "beta15"
    alpha = [parse_version(version_json(SODIUM, "a1", "x", "alpha", "2026-01-01"))]
    assert select_version(alpha, "1.21.11").version_id == "a1"


def test_filters_game_version_and_loader():
    versions = [
        parse_version(version_json(SODIUM, "neo", "n", "release", "2026-12-01", loaders=("neoforge",))),
        parse_version(version_json(SODIUM, "old", "o", "release", "2026-12-01", game=("1.21.10",))),
        parse_version(version_json(SODIUM, "ok", "k", "release", "2026-01-01")),
    ]
    assert select_version(versions, "1.21.11").version_id == "ok"
    assert select_version(versions, "1.20.1") is None


def test_parse_skips_versions_without_jars_and_uses_primary():
    v = version_json(FABRIC_API, "x", "1", "release", "d")
    v["files"] = [{"filename": "readme.txt", "url": "u", "hashes": {}}]
    assert parse_version(v) is None
    v2 = version_json(FABRIC_API, "y", "1", "release", "d")
    v2["files"].insert(0, {"filename": "extra.jar", "url": "u2", "primary": False, "hashes": {}})
    assert parse_version(v2).filename == "P7dR8mSH-1.jar"


class FakeResponse:
    def __init__(self, payload=None, content=b"", status=200):
        self.payload = payload
        self.content = content
        self.status_code = status
        self.headers = {"Content-Length": str(len(content))}

    def raise_for_status(self):
        if self.status_code >= 400:
            raise requests.HTTPError(f"{self.status_code}")

    def json(self):
        return self.payload

    def iter_content(self, chunk_size=1):
        yield self.content

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


class FakeSession:
    """Answers /project/<id>/version and file downloads from in-memory data."""

    def __init__(self, versions_by_project, files):
        self.headers = {}
        self.versions_by_project = versions_by_project
        self.files = files
        self.calls = []

    def get(self, url, params=None, timeout=None, stream=False):
        self.calls.append(url)
        if "/project/" in url:
            pid = url.split("/project/")[1].split("/")[0]
            return FakeResponse(self.versions_by_project.get(pid, []))
        name = url.rsplit("/", 1)[1]
        if name not in self.files:
            return FakeResponse(status=404)
        return FakeResponse(content=self.files[name])


def test_resolve_follows_required_dependencies():
    dep = "DEPxxxxx"
    data = {
        FABRIC_API: [version_json(FABRIC_API, "fa1", "0.141.6+1.21.11", "release", "2026-09-01")],
        SODIUM: [version_json(SODIUM, "so1", "0.8.14", "release", "2026-08-01", deps=(dep,))],
        dep: [version_json(dep, "dep1", "1.0", "release", "2026-01-01")],
    }
    client = ModrinthClient(FakeSession(data, {}))
    out = client.resolve([FABRIC_API, SODIUM], "1.21.11")
    assert set(out) == {FABRIC_API, SODIUM, dep}
    assert client.session.headers["User-Agent"].startswith("hollow-client-launcher/")


def test_resolve_errors_when_nothing_matches():
    client = ModrinthClient(FakeSession({FABRIC_API: []}, {}))
    with pytest.raises(ModrinthError, match="no fabric build of Fabric API"):
        client.resolve([FABRIC_API], "1.21.11")


def test_download_checks_sha512(tmp_path):
    good = parse_version(version_json(SODIUM, "s", "1", "release", "d", content=b"real jar", filename="s.jar"))
    client = ModrinthClient(FakeSession({}, {"s.jar": b"real jar"}))
    client.download(good, tmp_path / "s.jar")
    assert (tmp_path / "s.jar").read_bytes() == b"real jar"

    client_bad = ModrinthClient(FakeSession({}, {"s.jar": b"tampered"}))
    with pytest.raises(ModrinthError, match="checksum"):
        client_bad.download(good, tmp_path / "t.jar")
    assert not (tmp_path / "t.jar").exists()
    assert not (tmp_path / "t.jar.part").exists()


def test_pick_loader():
    assert pick_loader(["0.19.6", "0.19.5", "0.18.4", "0.20.0"], "0.19.5") == "0.20.0"
    assert pick_loader(["0.19.5"], "0.19.5") == "0.19.5"
    with pytest.raises(InstallError, match="26.2"):
        pick_loader(["0.18.0"], "0.19.5", "26.2")


class QuietProgress:
    def __init__(self):
        self.lines = []

    def status(self, t):
        self.lines.append(t)

    def progress(self, c, m):
        pass

    def log(self, t):
        self.lines.append(t)


def make_installer(tmp_path, sodium=("so14", "0.8.14", b"sodium14", "2026-08-01")):
    sid, number, content, date = sodium
    fapi = version_json(FABRIC_API, "fa1", "0.141.6+1.21.11", "release", "2026-09-01", content=b"fapi",
                        filename="fabric-api-0.141.6+1.21.11.jar")
    sod = version_json(SODIUM, sid, number, "release", date, content=content, filename=f"sodium-{number}.jar")
    files = {"fabric-api-0.141.6+1.21.11.jar": b"fapi", f"sodium-{number}.jar": content}
    return Installer(tmp_path, TEST_PROFILE, QuietProgress(), ModrinthClient(FakeSession({FABRIC_API: [fapi], SODIUM: [sod]}, files)))


def test_install_mods_downloads_then_is_idempotent(tmp_path):
    inst = make_installer(tmp_path)
    changed = inst.install_mods(force=False)
    assert len(changed) == 2
    mods = game_of(tmp_path) / "mods"
    assert (mods / "fabric-api-0.141.6+1.21.11.jar").is_file()
    assert (mods / "sodium-0.8.14.jar").is_file()
    manifest = read_manifest(mods)
    assert manifest["projects"][SODIUM]["version_id"] == "so14"
    assert inst.install_mods(force=False) == [], "second run downloads nothing"


def test_update_replaces_the_old_file(tmp_path):
    inst = make_installer(tmp_path)
    inst.install_mods(force=False)
    inst2 = make_installer(tmp_path, sodium=("so15", "0.8.15", b"sodium15", "2026-10-10"))
    updates = {u.project_id: u for u in inst2.check_updates()}
    assert updates[SODIUM].needed and updates[SODIUM].installed == "0.8.14" and updates[SODIUM].latest == "0.8.15"
    assert not updates[FABRIC_API].needed
    inst2.install_mods(force=False)
    mods = game_of(tmp_path) / "mods"
    assert (mods / "sodium-0.8.15.jar").is_file()
    assert not (mods / "sodium-0.8.14.jar").exists()


def test_deleted_mod_file_counts_as_missing(tmp_path):
    inst = make_installer(tmp_path)
    inst.install_mods(force=False)
    mods = game_of(tmp_path) / "mods"
    (mods / "sodium-0.8.14.jar").unlink()
    updates = {u.project_id: u for u in updates_needed(read_manifest(mods), mods, inst.latest_mods())}
    assert updates[SODIUM].needed and updates[SODIUM].installed is None
    assert inst.install_mods(force=False) == ["Sodium 0.8.14"]


def test_corrupted_mod_file_is_redownloaded(tmp_path):
    inst = make_installer(tmp_path)
    inst.install_mods(force=False)
    jar = game_of(tmp_path) / "mods" / "sodium-0.8.14.jar"
    jar.write_bytes(b"broken")
    assert inst.install_mods(force=False) == ["Sodium 0.8.14"]
    assert jar.read_bytes() == b"sodium14"


def test_hollow_jar_is_copied_and_old_versions_removed(tmp_path):
    inst = make_installer(tmp_path)
    mods = game_of(tmp_path) / "mods"
    mods.mkdir(parents=True)
    (mods / "hollowclient-0.9.0.jar").write_bytes(b"old")
    src_dir = tmp_path / "libs"
    src_dir.mkdir()
    jar = src_dir / "hollowclient-1.0.0.jar"
    jar.write_bytes(b"new mod")
    assert inst.install_hollow_jar(jar) == "hollowclient-1.0.0.jar"
    assert (mods / "hollowclient-1.0.0.jar").read_bytes() == b"new mod"
    assert not (mods / "hollowclient-0.9.0.jar").exists()
    assert read_manifest(mods)["hollowclient"]["filename"] == "hollowclient-1.0.0.jar"


def test_missing_hollow_jar_is_explained(tmp_path, monkeypatch):
    inst = make_installer(tmp_path)
    monkeypatch.setattr(inst_mod, "find_mod_jar", lambda **kw: None)
    with pytest.raises(InstallError, match="build_all"):
        inst.install_hollow_jar()


def test_summary_and_is_installed(tmp_path):
    inst = make_installer(tmp_path)
    game = game_of(tmp_path)
    assert not inst.is_installed()
    inst.install_mods(force=False)
    src = tmp_path / "hollowclient-1.0.0.jar"
    src.write_bytes(b"m")
    inst.install_hollow_jar(src)
    vid = "fabric-loader-0.19.5-1.21.11"
    (tmp_path / "versions" / vid).mkdir(parents=True)
    (tmp_path / "versions" / vid / f"{vid}.json").write_text("{}")
    (game / "hollow_install.json").write_text(json.dumps({"version_id": vid, "loader_version": "0.19.5"}))
    assert inst.is_installed()
    s = inst.summary()
    assert s["Fabric"] == "0.19.5" and s["Sodium"] == "0.8.14" and s["Hollow"] == "1.0.0"


def test_versions_are_shared_and_mods_are_per_profile(tmp_path):
    """Minecraft versions live in the root (shared); each profile's mods and install record in its instance."""
    inst = make_installer(tmp_path)
    assert inst.game_dir == tmp_path / "instances" / "test"
    assert inst.mods == tmp_path / "instances" / "test" / "mods"
    assert inst.names()[SODIUM] == "Sodium"
    other = Installer(tmp_path, Profile(id="other", name="O", tagline="", minecraft="26.2", min_loader="0.19.5"),
                      QuietProgress())
    assert other.game_dir != inst.game_dir and other.minecraft == "26.2"


def test_missing_hollow_jar_names_the_minecraft_version(tmp_path, monkeypatch):
    inst = make_installer(tmp_path)
    seen = {}
    monkeypatch.setattr(inst_mod, "find_mod_jar", lambda **kw: seen.update(kw))
    with pytest.raises(InstallError, match="1.21.11"):
        inst.install_hollow_jar()
    assert seen == {"minecraft": "1.21.11"}
