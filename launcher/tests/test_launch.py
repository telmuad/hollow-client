import json
import time

from hollow_launcher import launch
from hollow_launcher.auth import Account
from hollow_launcher.settings import LauncherSettings

ACCOUNT = Account("0123456789abcdef0123456789abcdef", "Steve", "secret-access-token", "secret-refresh", None, time.time())


def test_split_jvm_args_keeps_windows_paths():
    assert launch.split_jvm_args("") == []
    assert launch.split_jvm_args("-XX:+UseG1GC  -Dfoo=bar") == ["-XX:+UseG1GC", "-Dfoo=bar"]
    assert launch.split_jvm_args(r'-Dpath="C:\Program Files\x" -Xss2m') == [r"-Dpath=C:\Program Files\x", "-Xss2m"]
    assert launch.split_jvm_args('-Dbroken="unclosed') == ["-Dbroken=unclosed"]


def test_options_use_ram_args_and_resolution(tmp_path):
    s = LauncherSettings(ram_mb=6144, jvm_args="-XX:+UseZGC", resolution_width=1600, resolution_height=900)
    o = launch.build_options(ACCOUNT, s, tmp_path)
    assert o["jvmArguments"] == ["-Xmx6144M", "-Xms1024M", "-XX:+UseZGC"]
    assert o["customResolution"] is True and o["resolutionWidth"] == "1600" and o["resolutionHeight"] == "900"
    assert o["username"] == "Steve" and o["token"] == "secret-access-token"
    assert o["gameDirectory"] == str(tmp_path)
    assert o["launcherName"] == "HollowClient"


def test_default_resolution_is_left_to_minecraft(tmp_path):
    o = launch.build_options(ACCOUNT, LauncherSettings(), tmp_path)
    assert "customResolution" not in o
    assert o["jvmArguments"][:2] == ["-Xmx4096M", "-Xms1024M"]


def test_small_ram_sets_xms_equal():
    assert launch.memory_args(1024) == ["-Xmx1024M", "-Xms1024M"]


def test_redact_hides_the_token():
    cmd = ["java", "-Xmx4096M", "net.fabricmc.loader.impl.launch.knot.KnotClient", "--username", "Steve",
           "--accessToken", "secret-access-token", "--quickPlay", "x=secret-access-token"]
    out = launch.redact(cmd, ["secret-access-token"])
    assert "secret-access-token" not in " ".join(out)
    assert out[out.index("--accessToken") + 1] == launch.REDACTED
    assert cmd[6] == "secret-access-token", "the real command is not changed"


def test_build_command_against_a_fake_install(tmp_path):
    """minecraft-launcher-lib builds a full command from a version json; the token only appears after --accessToken."""
    vid = "fake-1.0"
    vdir = tmp_path / "versions" / vid
    vdir.mkdir(parents=True)
    (vdir / f"{vid}.json").write_text(json.dumps({
        "id": vid, "mainClass": "net.example.Main", "libraries": [], "type": "release",
        "arguments": {"game": ["--username", "${auth_player_name}", "--accessToken", "${auth_access_token}",
                               "--gameDir", "${game_directory}"], "jvm": ["-cp", "${classpath}"]},
        "assets": "x", "assetIndex": {"id": "x"},
    }))
    (vdir / f"{vid}.jar").write_bytes(b"")
    opts = launch.build_options(ACCOUNT, LauncherSettings(), tmp_path)
    opts["executablePath"] = "java"
    cmd = launch.build_command(vid, tmp_path, opts)
    assert cmd[0] == "java"
    assert "-Xmx4096M" in cmd
    assert cmd[cmd.index("--accessToken") + 1] == "secret-access-token"
    assert "secret-access-token" not in " ".join(launch.redact(cmd, [ACCOUNT.access_token]))


def test_unfilled_placeholders_are_dropped():
    cmd = ["java", "Main", "--accessToken", "t", "--clientId", "${clientid}", "--xuid", "${auth_xuid}", "--versionType", "release"]
    assert launch.strip_unfilled(cmd) == ["java", "Main", "--accessToken", "t", "--versionType", "release"]
