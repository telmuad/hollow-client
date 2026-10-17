"""Data for the launcher's SkyBlock page: mayor and election, the SkyBlock calendar, Jacob's contests, bazaar movers
and margins, and the Hollow mod's last SkyBlock session.

Everything comes from public endpoints that need no API key (Hypixel's /v2/resources and /v2/skyblock/bazaar, and
the Elite API for Jacob's contest crops). Every fetch is cached on disk under skyblock_cache/; when a source can't
be reached the cached copy is shown as stale instead of an error. The calendar itself is plain math from the SkyBlock
epoch (checked against hypixelskyblock.minecraft.wiki/w/Time and /w/Calendar on 2026-10-16).

Only information is shown here; nothing is sent to the game.
"""

from __future__ import annotations

import json
import logging
import re
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

import requests

from . import VERSION
from .jsonfile import read_json, write_json_atomic

log = logging.getLogger("hollow")

# ---------------------------------------------------------------------------------------------------- calendar

EPOCH = 1560275700  # 1st of Early Spring, Year 1, 00:00 (Jun 11 2019 17:55 UTC)
DAY = 1200  # 20 real minutes
HOUR = DAY // 24  # 50 real seconds
MONTH = 31 * DAY
YEAR = 12 * MONTH
MONTHS = ["Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
          "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"]


@dataclass(frozen=True)
class SkyDate:
    year: int
    month: int  # 0..11
    day: int  # 1..31
    hour: int
    minute: int

    @property
    def month_name(self) -> str:
        return MONTHS[self.month]

    def __str__(self) -> str:
        suffix = "th" if 10 <= self.day % 100 <= 20 else {1: "st", 2: "nd", 3: "rd"}.get(self.day % 10, "th")
        return f"{self.month_name} {self.day}{suffix}, Year {self.year}"


def sky_date(t: float) -> SkyDate:
    s = int(t) - EPOCH
    year, s = divmod(s, YEAR)
    month, s = divmod(s, MONTH)
    day, s = divmod(s, DAY)
    hour, s = divmod(s, HOUR)
    return SkyDate(year + 1, month, day + 1, hour, int(s * 60 / HOUR))


def sky_time(year: int, month: int, day: int, hour: int = 0) -> int:
    """Real unix time of a SkyBlock date (month 0..11, day 1..31)."""
    return EPOCH + (year - 1) * YEAR + month * MONTH + (day - 1) * DAY + hour * HOUR


# name, first month, first day, last month, last day (inclusive), what it is
YEARLY = [
    ("Hoppity's Hunt", 0, 1, 2, 31, "Chocolate rabbit eggs all over SkyBlock"),
    ("Election closes", 2, 27, 2, 27, "The new mayor takes office"),
    ("Traveling Zoo", 3, 1, 3, 3, "Oringo sells pets in the Hub"),
    ("Election booth opens", 5, 27, 5, 27, "Vote for the next mayor"),
    ("Harvest Feast", 6, 1, 8, 31, "Autumn farming event"),
    ("Spooky Festival", 7, 29, 7, 31, "Candy, Trick or Treat mobs and the Fear Mongerer"),
    ("Traveling Zoo", 9, 1, 9, 3, "Oringo sells pets in the Hub"),
    ("Season of Jerry", 11, 24, 11, 26, "Jerry's Workshop defense"),
    ("New Year Celebration", 11, 29, 11, 31, "New Year Cake from Baker"),
]
JACOB_OFFSET = DAY  # contests start on day 2, then every 3 SkyBlock days (every real hour at :15)
JACOB_EVERY = 3 * DAY
DARK_AUCTION_EVERY = 3 * DAY  # every 3 SkyBlock days at midnight (real time: :55 every hour)
CULT_DAYS = (7, 14, 21, 28)  # Cult of the Fallen Star, 0 AM to 6 AM


def _next_periodic(now: float, offset: int, every: int, length: int) -> tuple[int, int]:
    """The running or next occurrence of something that repeats every `every` seconds from EPOCH + offset."""
    k = (int(now) - EPOCH - offset) // every
    start = EPOCH + offset + k * every
    if start + length <= now:
        start += every
    return start, start + length


def _months_later(sd: SkyDate, n: int) -> tuple[int, int]:
    m = sd.month + n
    return sd.year + m // 12, m % 12


def events(now: float, mayor_perks: list[str] | None = None, count: int = 10) -> list[dict[str, Any]]:
    """The running and upcoming calendar events, soonest first. Times are unix seconds."""
    out: list[dict[str, Any]] = []
    year = sky_date(now).year
    for y in (year, year + 1):
        for name, m1, d1, m2, d2, detail in YEARLY:
            start, end = sky_time(y, m1, d1), sky_time(y, m2, d2) + DAY
            if end > now:
                out.append({"name": name, "start": start, "end": end, "detail": detail, "kind": "yearly"})
    perks = {p.lower() for p in (mayor_perks or [])}
    sd = sky_date(now)
    if "fishing festival" in perks:  # Marina: the first 3 days of every month
        for i in range(3):
            start = sky_time(*_months_later(sd, i), 1)
            if start + 3 * DAY > now:
                out.append({"name": "Fishing Festival", "start": start, "end": start + 3 * DAY,
                            "detail": "Marina's sharks", "kind": "mayor"})
    da = _next_periodic(now, 0, DARK_AUCTION_EVERY, 5 * 60)
    out.append({"name": "Dark Auction", "start": da[0], "end": da[1], "detail": "Sirius in the Hub's wilderness", "kind": "hourly"})
    cult = next((t for i in range(2) for d in CULT_DAYS
                 if (t := sky_time(*_months_later(sd, i), d)) + 6 * HOUR > now), None)
    if cult is not None:
        out.append({"name": "Cult of the Fallen Star", "start": cult, "end": cult + 6 * HOUR,
                    "detail": "Dwarven Mines, 0 AM to 6 AM", "kind": "weekly"})
    out.sort(key=lambda e: (e["start"] > now, e["start"]))
    return out[:count]


def contests(now: float, elite: dict[str, Any] | None, count: int = 3) -> list[dict[str, Any]]:
    """The running and next Jacob's Farming Contests, with crops when the Elite API knew them."""
    crops: dict[int, list[str]] = {}
    if isinstance(elite, dict) and isinstance(elite.get("contests"), dict):
        for k, v in elite["contests"].items():
            if str(k).isdigit() and isinstance(v, list):
                crops[int(k)] = [str(c) for c in v]
    start, _ = _next_periodic(now, JACOB_OFFSET, JACOB_EVERY, DAY)
    out = []
    for i in range(count):
        s = start + i * JACOB_EVERY
        out.append({"start": s, "end": s + DAY, "crops": crops.get(s, [])})
    return out


def mayor_term_end(now: float) -> int:
    """When the current mayor's term ends (the next Late Spring 27th)."""
    sd = sky_date(now)
    t = sky_time(sd.year, 2, 27)
    return t if t > now else sky_time(sd.year + 1, 2, 27)


# ---------------------------------------------------------------------------------------------------- election

_COLOR = re.compile("§.")


def strip_colors(text: str) -> str:
    return _COLOR.sub("", text or "")


def _perks(raw: Any) -> list[dict[str, Any]]:
    out = []
    for p in raw if isinstance(raw, list) else []:
        if isinstance(p, dict) and isinstance(p.get("name"), str):
            out.append({"name": p["name"], "description": strip_colors(str(p.get("description", ""))),
                        "minister": bool(p.get("minister", False))})
    return out


def parse_election(data: Any) -> dict[str, Any]:
    """Mayor, minister and (while the booth is open) the running election, from /v2/resources/skyblock/election."""
    if not isinstance(data, dict) or not isinstance(data.get("mayor"), dict):
        return {}
    mayor = data["mayor"]
    out: dict[str, Any] = {"mayor": {"name": str(mayor.get("name", "?")), "key": str(mayor.get("key", "")),
                                     "perks": _perks(mayor.get("perks"))}}
    minister = mayor.get("minister")
    if isinstance(minister, dict):
        perk = minister.get("perk") if isinstance(minister.get("perk"), dict) else {}
        out["minister"] = {"name": str(minister.get("name", "?")), "key": str(minister.get("key", "")),
                           "perk": _perks([perk])[0] if perk else None}
    current = data.get("current")
    if isinstance(current, dict) and isinstance(current.get("candidates"), list):
        cands = []
        for c in current["candidates"]:
            if isinstance(c, dict):
                votes = c.get("votes")
                cands.append({"name": str(c.get("name", "?")), "key": str(c.get("key", "")),
                              "votes": int(votes) if isinstance(votes, (int, float)) else 0,
                              "perks": [p["name"] for p in _perks(c.get("perks"))]})
        total = sum(c["votes"] for c in cands)
        for c in cands:
            c["share"] = round(c["votes"] / total, 4) if total else 0.0
        cands.sort(key=lambda c: -c["votes"])
        out["election"] = {"year": int(current.get("year", 0) or 0), "candidates": cands, "totalVotes": total}
    return out


# ---------------------------------------------------------------------------------------------------- bazaar

ROMAN = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"]


def pretty_product(pid: str, names: dict[str, str] | None = None) -> str:
    """A readable name for a bazaar product id (item names from Hypixel's item list when we have it)."""
    if names and pid in names:
        return strip_colors(names[pid])
    words = pid.split("_")
    if words[0] == "ENCHANTMENT":
        words = words[1:]
        if words and words[0] == "ULTIMATE":
            words = words[1:]
        level = ""
        if words and words[-1].isdigit():
            n = int(words.pop())
            level = " " + (ROMAN[n] if n < len(ROMAN) else str(n))
        return " ".join(w.capitalize() for w in words) + level
    if words[0] in ("ESSENCE", "SHARD") and len(words) > 1:
        return " ".join(w.capitalize() for w in words[1:]) + " " + words[0].capitalize()
    return " ".join(w.capitalize() for w in words)


def compact_bazaar(data: Any) -> dict[str, list[float]]:
    """product id -> [insta-buy price, insta-sell price, weekly bought, weekly sold]."""
    out: dict[str, list[float]] = {}
    products = data.get("products") if isinstance(data, dict) else None
    for pid, p in products.items() if isinstance(products, dict) else []:
        q = p.get("quick_status") if isinstance(p, dict) else None
        if not isinstance(q, dict):
            continue
        try:
            out[str(pid)] = [round(float(q.get("buyPrice", 0)), 1), round(float(q.get("sellPrice", 0)), 1),
                             float(q.get("buyMovingWeek", 0)), float(q.get("sellMovingWeek", 0))]
        except (TypeError, ValueError):
            continue
    return out


MIN_WEEKLY = 1000  # ignore barely traded products (their prices jump around)
MIN_PRICE = 50.0


def movers(now: dict[str, list[float]], before: dict[str, list[float]], names: dict[str, str] | None = None,
           count: int = 5) -> dict[str, list[dict[str, Any]]]:
    """Biggest insta-buy price changes between two snapshots, among products that actually trade."""
    changes = []
    for pid, (buy, sell, wb, ws) in now.items():
        old = before.get(pid)
        if not old or min(wb, ws) < MIN_WEEKLY or buy < MIN_PRICE or old[0] < MIN_PRICE:
            continue
        pct = (buy - old[0]) / old[0]
        changes.append({"id": pid, "name": pretty_product(pid, names), "price": buy, "was": old[0], "change": round(pct, 4)})
    up = sorted((c for c in changes if c["change"] > 0), key=lambda c: -c["change"])[:count]
    down = sorted((c for c in changes if c["change"] < 0), key=lambda c: c["change"])[:count]
    return {"up": up, "down": down}


MARGIN_MIN_WEEKLY = 100_000


def margins(now: dict[str, list[float]], names: dict[str, str] | None = None, count: int = 5) -> list[dict[str, Any]]:
    """The biggest gaps between insta-buy and insta-sell on busy products (before bazaar tax)."""
    out = []
    for pid, (buy, sell, wb, ws) in now.items():
        if min(wb, ws) < MARGIN_MIN_WEEKLY or sell <= 0 or buy <= sell:
            continue
        out.append({"id": pid, "name": pretty_product(pid, names), "buy": buy, "sell": sell,
                    "margin": round(buy - sell, 1), "percent": round((buy - sell) / sell, 4), "weekly": min(wb, ws)})
    out.sort(key=lambda m: -m["margin"])
    return out[:count]


def pick_baseline(snapshots: list[dict[str, Any]], now: float, target_age: float = 24 * 3600,
                  min_age: float = 10 * 60) -> dict[str, Any] | None:
    """The saved snapshot closest to `target_age` old (at least `min_age` old)."""
    old = [s for s in snapshots if isinstance(s.get("t"), (int, float)) and now - s["t"] >= min_age]
    return min(old, key=lambda s: abs((now - s["t"]) - target_age)) if old else None


def add_snapshot(snapshots: list[dict[str, Any]], snap: dict[str, list[float]], now: float,
                 every: float = 3600, keep: float = 26 * 3600) -> list[dict[str, Any]]:
    """Keep about one snapshot per hour for the last day."""
    out = [s for s in snapshots if isinstance(s.get("t"), (int, float)) and now - s["t"] <= keep]
    if not out or now - max(s["t"] for s in out) >= every:
        out.append({"t": now, "p": snap})
    return out


# ---------------------------------------------------------------------------------------------------- the mod's session


def read_session(instance: Path) -> dict[str, Any]:
    """The newest session_stats.json the Hollow mod wrote for any SkyBlock profile in this instance.

    The mod writes config/hollowclient/skyblock/profiles/<profile>/session_stats.json:
    {"version": 1, "updated": <unix s>, "profile": "<SkyBlock profile name>", "mode": "", "rows": [{"label", "value"}]}
    """
    base = instance / "config" / "hollowclient" / "skyblock" / "profiles"
    best: tuple[float, dict[str, Any]] | None = None
    for f in base.glob("*/session_stats.json") if base.is_dir() else []:
        data, _ = read_json(f)
        if not isinstance(data, dict) or not isinstance(data.get("rows"), list):
            continue
        updated = data.get("updated") if isinstance(data.get("updated"), (int, float)) else f.stat().st_mtime
        if best is None or updated > best[0]:
            rows = [{"label": str(r.get("label", ""))[:40], "value": str(r.get("value", ""))[:40]}
                    for r in data["rows"][:12] if isinstance(r, dict)]
            best = (updated, {"profile": str(data.get("profile", ""))[:24], "mode": str(data.get("mode", ""))[:16],
                              "updated": updated, "rows": rows})
    return best[1] if best else {}


# ---------------------------------------------------------------------------------------------------- fetching and caching

USER_AGENT = f"HollowClient/{VERSION} (+https://github.com/telmuad/hollow-client)"
SOURCES = {
    "election": ("https://api.hypixel.net/v2/resources/skyblock/election", 10 * 60),
    "bazaar": ("https://api.hypixel.net/v2/skyblock/bazaar", 5 * 60),
    "contests": ("https://api.elitebot.dev/contests/at/now", 3 * 3600),
    "items": ("https://api.hypixel.net/v2/resources/skyblock/items", 24 * 3600),
}


class Cache:
    """skyblock_cache/<name>.json = {"fetched": <unix s>, "data": ...}"""

    def __init__(self, folder: Path):
        self.folder = folder

    def get(self, name: str) -> tuple[Any, float]:
        data, _ = read_json(self.folder / f"{name}.json")
        if isinstance(data, dict) and isinstance(data.get("fetched"), (int, float)):
            return data.get("data"), float(data["fetched"])
        return None, 0.0

    def put(self, name: str, data: Any, fetched: float) -> None:
        try:
            self.folder.mkdir(parents=True, exist_ok=True)
            write_json_atomic(self.folder / f"{name}.json", {"fetched": fetched, "data": data})
        except OSError as e:
            log.warning(f"SkyBlock cache: couldn't save {name}: {e}")


def _get_json(url: str) -> Any:
    r = requests.get(url, timeout=20, headers={"User-Agent": USER_AGENT})
    r.raise_for_status()
    return r.json()


class Dashboard:
    """Fetches what's due, keeps the cache, and builds the page's data. `fetch` is swappable for tests."""

    def __init__(self, cache_dir: Path, network: bool = True, fetch: Callable[[str], Any] | None = None,
                 clock: Callable[[], float] = time.time):
        self.cache = Cache(cache_dir)
        self.network = network
        self.fetch = fetch or _get_json
        self.clock = clock
        self.errors: dict[str, str] = {}

    def _source(self, name: str, transform: Callable[[Any], Any], force: bool = False) -> tuple[Any, float, bool]:
        """(data, fetched at, stale). Uses the cache while it's fresh; on a failed fetch the old copy is stale."""
        url, ttl = SOURCES[name]
        cached, fetched = self.cache.get(name)
        now = self.clock()
        if cached is not None and not force and now - fetched < ttl:
            return cached, fetched, False
        if not self.network:
            return cached, fetched, cached is not None
        try:
            raw = self.fetch(url)
            if isinstance(raw, dict) and raw.get("success") is False:
                raise ValueError(str(raw.get("cause", "request refused")))
            data = transform(raw)
            self.cache.put(name, data, now)
            self.errors.pop(name, None)
            return data, now, False
        except Exception as e:  # network, HTTP, JSON: show the old copy
            self.errors[name] = str(e)[:200]
            log.info(f"SkyBlock data: {name} unavailable ({str(e)[:120]}); using the cached copy")
            return cached, fetched, True

    def _item_names(self) -> dict[str, str]:
        def names(raw: Any) -> dict[str, str]:
            items = raw.get("items") if isinstance(raw, dict) else None
            return {str(i["id"]): str(i["name"]) for i in items or [] if isinstance(i, dict) and "id" in i and "name" in i}

        data, _, _ = self._source("items", names)
        return data if isinstance(data, dict) else {}

    def build(self, instance: Path | None = None, force: bool = False) -> dict[str, Any]:
        now = self.clock()
        out: dict[str, Any] = {"now": now, "skyDate": str(sky_date(now)), "sources": {}}

        def note(name: str, fetched: float, stale: bool) -> None:
            out["sources"][name] = {"fetched": fetched, "stale": stale, "missing": fetched == 0}

        election, f, stale = self._source("election", parse_election, force)
        note("election", f, stale)
        out["election"] = election or {}
        perks = [p["name"] for p in (election or {}).get("mayor", {}).get("perks", [])]
        out["events"] = events(now, perks)
        out["termEnds"] = mayor_term_end(now)

        elite, f, stale = self._source("contests", lambda raw: raw, force)
        out["contests"] = contests(now, elite)
        # Elite lists the whole SkyBlock year, so an old copy is only stale once it no longer covers the next contest
        note("contests", f, stale and not out["contests"][0]["crops"])

        bazaar, f, stale = self._source("bazaar", compact_bazaar, force)
        note("bazaar", f, stale)
        names = self._item_names()
        out["movers"], out["margins"], out["moversSince"] = {"up": [], "down": []}, [], 0
        if isinstance(bazaar, dict) and bazaar:
            snaps_data, _ = self.cache.get("bazaar_history")
            snaps = snaps_data if isinstance(snaps_data, list) else []
            base = pick_baseline(snaps, f or now)
            if base:
                out["movers"] = movers(bazaar, base["p"], names)
                out["moversSince"] = base["t"]
            kept = add_snapshot(snaps, bazaar, f) if not stale and f else snaps
            if kept is not snaps and (len(kept) != len(snaps) or kept[-1]["t"] != snaps[-1]["t"]):
                self.cache.put("bazaar_history", kept, f)
            out["margins"] = margins(bazaar, names)
        out["session"] = read_session(instance) if instance else {}
        out["offline"] = any(s["stale"] for s in out["sources"].values())
        return out
