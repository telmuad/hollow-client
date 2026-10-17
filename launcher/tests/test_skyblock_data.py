"""The SkyBlock page's data: calendar math, election, contests, bazaar movers and margins, cache and outages."""

import json
from pathlib import Path

import pytest

from hollow_launcher import skyblock_data as sd

FIX = Path(__file__).parent / "fixtures" / "skyblock"
NOW = 1792195033  # 2026-10-16, Early Autumn 13th, Year 520 (when the fixtures were saved)


def fixture(name):
    return json.loads((FIX / name).read_text(encoding="utf-8"))


# ---------------------------------------------------------------------------------------------------- calendar


def test_epoch_and_units():
    assert sd.sky_date(sd.EPOCH) == sd.SkyDate(1, 0, 1, 0, 0)
    assert str(sd.sky_date(sd.EPOCH)) == "Early Spring 1st, Year 1"
    assert sd.YEAR == 446400 and sd.MONTH == 37200 and sd.DAY == 1200, "5 days 4 hours / 10 h 20 min / 20 min"
    assert sd.sky_date(sd.EPOCH + sd.DAY + 6 * sd.HOUR) == sd.SkyDate(1, 0, 2, 6, 0)
    assert sd.sky_date(sd.EPOCH + sd.YEAR - 1).month_name == "Late Winter"


def test_round_trip():
    for t in (sd.sky_time(520, 6, 13), sd.sky_time(3, 11, 31, 23), sd.sky_time(1, 0, 1)):
        d = sd.sky_date(t)
        assert sd.sky_time(d.year, d.month, d.day, d.hour) == t


def test_today_matches_the_fixture_day():
    assert str(sd.sky_date(NOW)) == "Early Autumn 13th, Year 520"


def test_events_running_and_next():
    ev = sd.events(NOW)
    names = [e["name"] for e in ev]
    running = [e for e in ev if e["start"] <= NOW < e["end"]]
    assert "Harvest Feast" in names and ev[:len(running)] == running, "running events come first"
    upcoming = ev[len(running):]
    assert all(e["start"] > NOW for e in upcoming)
    assert [e["start"] for e in upcoming] == sorted(e["start"] for e in upcoming)
    spooky = next(e for e in ev if e["name"] == "Spooky Festival")
    assert sd.sky_date(spooky["start"]) == sd.SkyDate(520, 7, 29, 0, 0)
    assert spooky["end"] - spooky["start"] == 3 * sd.DAY
    da = next(e for e in ev if e["name"] == "Dark Auction")
    assert (da["start"] - sd.EPOCH) % 3600 == 0 and da["start"] <= NOW < da["end"], "running right now"
    cult = next(e for e in ev if e["name"] == "Cult of the Fallen Star")
    assert sd.sky_date(cult["start"]).day in sd.CULT_DAYS and cult["end"] - cult["start"] == 300


def test_hoppity_spans_the_three_spring_months():
    start = sd.sky_time(521, 0, 1) - 10
    hop = next(e for e in sd.events(start) if e["name"] == "Hoppity's Hunt")
    assert hop["start"] == sd.sky_time(521, 0, 1) and hop["end"] == sd.sky_time(521, 3, 1)


def test_fishing_festival_only_with_marina():
    assert "Fishing Festival" not in [e["name"] for e in sd.events(NOW, ["Stock Exchange"])]
    fest = [e for e in sd.events(NOW, ["Fishing Festival"], count=30) if e["name"] == "Fishing Festival"]
    assert fest and sd.sky_date(fest[0]["start"]).day == 1 and fest[0]["end"] - fest[0]["start"] == 3 * sd.DAY


def test_contests_use_elite_crops_and_hourly_times():
    c = sd.contests(NOW, fixture("contests.json"))
    assert len(c) == 3
    assert c[0]["start"] == 1792196100 and c[0]["crops"] == ["Melon", "Nether Wart", "Wheat"]
    assert all(x["start"] % 3600 == 900 for x in c), "every real hour at :15"
    assert [x["start"] for x in c] == [c[0]["start"] + i * 3600 for i in range(3)]
    unknown = sd.contests(NOW, None)
    assert [x["start"] for x in unknown] == [x["start"] for x in c] and unknown[0]["crops"] == []


def test_mayor_term_ends_on_late_spring_27():
    end = sd.mayor_term_end(NOW)
    assert sd.sky_date(end) == sd.SkyDate(521, 2, 27, 0, 0)


# ---------------------------------------------------------------------------------------------------- election


def test_parse_election():
    el = sd.parse_election(fixture("election.json"))
    assert el["mayor"]["name"] == "Diaz"
    assert [p["name"] for p in el["mayor"]["perks"]] == ["Stock Exchange", "Volume Trading"]
    assert "§" not in el["mayor"]["perks"][0]["description"]
    assert el["minister"]["name"] == "Finnegan" and el["minister"]["perk"]["name"] == "Blooming Business"
    vote = el["election"]
    assert vote["year"] == 520 and vote["candidates"][0]["name"] == "Jerry"
    assert abs(sum(c["share"] for c in vote["candidates"]) - 1) < 0.001
    assert sd.parse_election({"success": True}) == {} and sd.parse_election(None) == {}


def test_election_without_an_open_booth():
    data = fixture("election.json")
    data.pop("current")
    assert "election" not in sd.parse_election(data)


# ---------------------------------------------------------------------------------------------------- bazaar


def test_product_names():
    assert sd.pretty_product("ENCHANTMENT_ULTIMATE_WISE_5") == "Wise V"
    assert sd.pretty_product("ENCHANTMENT_SHARPNESS_10") == "Sharpness X"
    assert sd.pretty_product("ESSENCE_DRAGON") == "Dragon Essence"
    assert sd.pretty_product("SHARD_SALMON") == "Salmon Shard"
    assert sd.pretty_product("ENCHANTED_DIAMOND") == "Enchanted Diamond"
    assert sd.pretty_product("ENCHANTED_DIAMOND", {"ENCHANTED_DIAMOND": "§aEnchanted Diamond"}) == "Enchanted Diamond"


def test_movers_and_margins():
    snap = sd.compact_bazaar(fixture("bazaar.json"))
    assert set(snap) >= {"ENCHANTED_DIAMOND", "SUSPICIOUS_SCRAP"}
    before = {k: [v[0] / 1.2, v[1], v[2], v[3]] for k, v in snap.items()}  # everything was 20% cheaper
    before["GRIFFIN_FEATHER"][0] = snap["GRIFFIN_FEATHER"][0] * 1.5
    m = sd.movers(snap, before)
    assert m["down"][0]["id"] == "GRIFFIN_FEATHER" and m["down"][0]["change"] < 0
    assert all(abs(x["change"] - 0.2) < 0.01 for x in m["up"])
    assert "FACTION_RABBIT_WALKER" not in [x["id"] for x in m["up"]], "barely traded products are skipped"
    mg = sd.margins(snap)
    assert mg[0]["id"] == "SUSPICIOUS_SCRAP" and mg[0]["margin"] > 0
    assert [x["margin"] for x in mg] == sorted((x["margin"] for x in mg), reverse=True)


def test_snapshots_hourly_and_baseline():
    snaps = []
    for h in range(30):
        snaps = sd.add_snapshot(snaps, {"X": [h, 0, 0, 0]}, 1000 + h * 3600)
        snaps = sd.add_snapshot(snaps, {"X": [h, 0, 0, 0]}, 1000 + h * 3600 + 60)  # too soon, not kept
    assert len(snaps) == 26, "about one per hour for the last 26 hours"
    now = 1000 + 29 * 3600 + 60
    base = sd.pick_baseline(snaps, now)
    assert abs((now - base["t"]) - 24 * 3600) <= 3600
    assert sd.pick_baseline([{"t": now - 60, "p": {}}], now) is None, "younger than 10 minutes doesn't count"


# ---------------------------------------------------------------------------------------------------- session file


def test_newest_session_file(tmp_path):
    base = tmp_path / "config" / "hollowclient" / "skyblock" / "profiles"
    for name, updated in (("aaa", 100), ("bbb", 200)):
        (base / name).mkdir(parents=True)
        (base / name / "session_stats.json").write_text(json.dumps(
            {"version": 1, "updated": updated, "profile": name.title(), "rows": [{"label": "Coins", "value": "+1.2M"}]}))
    (base / "broken").mkdir()
    (base / "broken" / "session_stats.json").write_text("{not json")
    s = sd.read_session(tmp_path)
    assert s["profile"] == "Bbb" and s["rows"] == [{"label": "Coins", "value": "+1.2M"}]
    assert sd.read_session(tmp_path / "nothing") == {}


# ---------------------------------------------------------------------------------------------------- fetching


def fake_fetch(calls, fail=()):
    def fetch(url):
        calls.append(url)
        for name, (u, _) in sd.SOURCES.items():
            if u == url:
                if name in fail:
                    raise OSError("offline")
                if name == "items":
                    return {"success": True, "items": [{"id": "SUSPICIOUS_SCRAP", "name": "Suspicious Scrap"}]}
                return fixture({"election": "election.json", "bazaar": "bazaar.json", "contests": "contests.json"}[name])
        raise AssertionError(url)
    return fetch


def test_dashboard_fetches_then_uses_the_cache(tmp_path):
    calls = []
    clock = [NOW]
    dash = sd.Dashboard(tmp_path, fetch=fake_fetch(calls), clock=lambda: clock[0])
    out = dash.build()
    assert out["election"]["mayor"]["name"] == "Diaz" and out["contests"][0]["crops"]
    assert out["margins"] and not out["offline"]
    assert len(calls) == 4
    clock[0] += 60
    dash.build()
    assert len(calls) == 4, "everything was still fresh"
    clock[0] += 11 * 60
    out = dash.build()
    assert len(calls) == 6, "election and bazaar are due again"
    assert out["moversSince"] == NOW and "up" in out["movers"]


def test_outage_shows_the_old_copy_as_stale(tmp_path):
    clock = [NOW]
    sd.Dashboard(tmp_path, fetch=fake_fetch([]), clock=lambda: clock[0]).build()
    clock[0] += 3600
    out = sd.Dashboard(tmp_path, fetch=fake_fetch([], fail=("election", "bazaar")), clock=lambda: clock[0]).build()
    assert out["offline"] and out["sources"]["election"]["stale"]
    assert out["election"]["mayor"]["name"] == "Diaz", "still shown"


def test_offline_with_nothing_cached(tmp_path):
    out = sd.Dashboard(tmp_path, network=False).build()
    assert out["election"] == {} and out["margins"] == [] and out["sources"]["bazaar"]["missing"]
    assert out["events"], "the calendar needs no network"


def test_refused_request_counts_as_an_outage(tmp_path):
    dash = sd.Dashboard(tmp_path, fetch=lambda url: {"success": False, "cause": "Key throttle"}, clock=lambda: NOW)
    out = dash.build()
    assert out["offline"] and "throttle" in dash.errors["election"].lower()


@pytest.mark.parametrize("bad", [None, [], "x", {"products": {"A": "nope"}}])
def test_compact_bazaar_ignores_junk(bad):
    assert sd.compact_bazaar(bad) == {}
