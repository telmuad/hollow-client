"""Run from anywhere: python mod/tools/skyblock_scenarios.py. Writes the built-in SkyBlock simulator scenarios from the unit-test fixtures (so both stay in sync)."""
import json
import os

MOD = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIX = MOD + "/src/test/resources/skyblock/fixtures/"
OUT = MOD + "/src/main/resources/assets/hollowclient/skyblock/sim/"
os.makedirs(OUT, exist_ok=True)
sb = json.load(open(FIX + "sidebars.json", encoding="utf-8"))
tab = json.load(open(FIX + "tablists.json", encoding="utf-8"))
items = json.load(open(FIX + "items.json", encoding="utf-8"))


def loc(t, mode, mp, server="mini42AB"):
    return {"t": t, "type": "location", "serverName": server, "serverType": "SKYBLOCK", "lobbyName": "", "mode": mode, "map": mp}


def side(t, key, replace=None):
    lines = list(sb[key]["lines"])
    if replace:
        lines = [replace.get(l.split(":")[0], l) if ":" in l else l for l in lines]
    return {"t": t, "type": "sidebar", "title": sb[key]["title"], "lines": lines}


def tabl(t, key):
    return {"t": t, "type": "tab", "lines": tab[key]}


def chat(t, text):
    return {"t": t, "type": "chat", "text": text}


def bar(t, text):
    return {"t": t, "type": "actionbar", "text": text}


def give(t, key, slot=-1):
    it = dict(items[key])
    it["slot"] = slot
    it["count"] = 1
    return {"t": t, "type": "give", "item": it}


profile = [chat(400, "§aYou are playing on profile: §eApple"), chat(450, "§8Profile ID: 0b1c2d3e-0000-4000-8000-0000000000aa")]
health = "§c1,234/1,500❤     §a512§a❈ Defense     §b300/400✎ Mana"

scenarios = {
    "hub": ("Joins SkyBlock in the Hub: scoreboard, tab list, profile, purse changes, a rare drop and skill XP", [
        loc(0, "hub", "Hub"), side(100, "hub"), tabl(200, "hub"), *profile, chat(500, "§eWelcome to Hypixel SkyBlock!"),
        bar(1000, health),
        side(2000, "hub", {"Purse": "Purse: §61,240,000 §e(+5,433)"}),
        bar(2500, "§c1,234/1,500❤     §3+12.5 Farming (1,234/5,000)     §b300/400✎ Mana"),
        chat(3000, "§6§lRARE DROP! §5Shard of the Shredded §b(+312% §b✯ Magic Find§b)"),
        bar(3500, "§c1,234/1,500❤     §3+12.5 Farming (1,246.5/5,000)     §b300/400✎ Mana"),
        side(4000, "hub", {"Purse": "Purse: §61,251,000 §e(+11,000)", "Bits": "Bits: §b1,484 §3(+250)"}),
    ]),
    "garden": ("The Garden: copper, three visitors, Farming XP", [
        loc(0, "garden", "Garden"), side(100, "garden"), tabl(200, "garden"), *profile,
        bar(800, "§c900/900❤   §3+48 Farming (12,000/50,000)   §b80/80✎ Mana"),
        chat(1500, "§aLiam §ehas arrived on your Garden!"),
        bar(2000, "§c900/900❤   §3+48 Farming (12,048/50,000)   §b80/80✎ Mana"),
        chat(2600, "§fFarmer Jon §ehas arrived on your Garden!"),
        chat(3000, "§6§lYUCK! §e3 §6ൠ Pests §7have spawned in §aPlot §7- §b7§7!"),
        {"t": 3500, "type": "container", "title": "§8Liam", "items": [
            {"slot": 29, "material": "minecraft:lime_terracotta", "count": 1, "name": "§aAccept Offer",
             "lore": ["§7Items Required:", " §aEnchanted Wheat §8x10", " §9Enchanted Hay Bale §8x2", "",
                      "§7Rewards:", " §8+§c30 Copper", " §8+§315,000 §7Farming XP", "", "§eClick to give!"], "custom_data": {}},
            {"slot": 33, "material": "minecraft:red_terracotta", "count": 1, "name": "§cRefuse Offer", "lore": [], "custom_data": {}}]},
    ]),
    "slayer": ("A Sven Packmaster IV quest in the Park: progress, your boss spawns (a frozen wolf with its name tags), "
               "quest complete, a drop", [
        loc(0, "foraging_1", "The Park"), side(100, "slayer_progress"), tabl(200, "hub"), *profile,
        chat(600, "§5§l  SLAYER QUEST STARTED!"),
        bar(800, "§c1,234/1,500❤   §3+120 Combat (2,000/10,000)"),
        side(2000, "slayer_boss"),
        # The boss's labels as Hypixel stacks them: name and health on top, the timer, then "Spawned by" lowest
        {"t": 2000, "type": "summon", "entity": "minecraft:wolf", "dx": 3, "dy": 0, "dz": 4},
        {"t": 2050, "type": "summon", "name": "§c☠ §bSven Packmaster §a1.2M§c❤", "dx": 3, "dy": 1.9, "dz": 4},
        {"t": 2050, "type": "summon", "name": "§c02:58", "dx": 3, "dy": 1.6, "dz": 4},
        {"t": 2050, "type": "summon", "name": "§7Spawned by: §b{me}", "dx": 3, "dy": 1.3, "dz": 4},
        # Someone else's boss nearby, which must never be picked
        {"t": 2100, "type": "summon", "entity": "minecraft:wolf", "dx": -4, "dy": 0, "dz": 4},
        {"t": 2150, "type": "summon", "name": "§c☠ §bSven Packmaster §a2.0M§c❤", "dx": -4, "dy": 1.9, "dz": 4},
        {"t": 2150, "type": "summon", "name": "§7Spawned by: §bSomeoneElse", "dx": -4, "dy": 1.3, "dz": 4},
        {"t": 5800, "type": "clear_summons"},
        chat(6000, "§a§l  NICE! SLAYER BOSS SLAIN!"),
        chat(6100, "§a§l  SLAYER QUEST COMPLETE!"),
        chat(6300, "§9§lVERY RARE DROP! §7(§f2x§7) §9Grizzly Bait §b(+150% §b✯ Magic Find§b)"),
    ]),
    "dungeon": ("Catacombs F7: cleared percent, secrets, a boss line and the score", [
        loc(0, "dungeon", "Catacombs"), side(100, "dungeon"), tabl(200, "dungeon"),
        chat(300, "§eStarting in 1 second."),
        chat(600, "§ePlayerTwo §ehas obtained §8Wither Key§e!"),
        chat(1200, "§cThe §c§lBLOOD DOOR§c has been opened!"),
        bar(800, "§c2,000/2,000❤   §73/7 Secrets   §b500/500✎ Mana"),
        chat(2000, "§4[BOSS] Necron§r§c: §r§cFinally, I heard so much about you."),
        chat(3000, "§e  Team Score: §a300 §e(§6§lS+§e)"),
    ]),
    "rift": ("The Rift: motes and the time left", [
        loc(0, "rift", "The Rift"), side(100, "rift"),
        bar(500, "§a5m 20sф Left    §c600/600❤"),
        bar(1500, "§a5m 19sф Left    §c600/600❤"),
    ]),
    "general": ("General area: status bars from the action bar, your pet, a power orb and a big damage number nearby, a fairy soul", [
        loc(0, "hub", "Hub"), side(100, "hub"), tabl(200, "pet_xp"), *profile,
        bar(500, "§c1,234/1,500❤     §a512§a❈ Defense     §b300/400✎ Mana §3200ʬ"),
        {"t": 600, "type": "summon", "name": "§aOverflux §e45s", "dx": 3, "dy": 1, "dz": 3},
        {"t": 650, "type": "summon", "name": "§f✧§e1,234,567§6✧", "dx": -2, "dy": 1.5, "dz": 3},
        bar(1500, "§c1,180/1,500❤     §a512§a❈ Defense     §b260/400✎ Mana §3200ʬ"),
        chat(2000, "§d§lSOUL! §eYou found a §dFairy Soul§e!"),
        bar(2500, "§c1,180/1,500❤     §a512§a❈ Defense     §b260/400✎ Mana §3200ʬ"),
    ]),
    "economy": ("Bazaar orders set up and filled, an auction sold, a growing purse", [
        loc(0, "hub", "Hub"), side(100, "hub"), tabl(200, "hub"), *profile,
        chat(600, "§6[Bazaar] §r§7§7Buy Order Setup! §r§a64§r§7x §r§fEnchanted Diamond §r§7for §r§69,600 coins§r§7."),
        chat(700, "§6[Bazaar] §r§7§7Sell Offer Setup! §r§a160§r§7x §r§aEnchanted Gold §r§7for §r§645,000 coins§r§7."),
        chat(1500, "§6[Bazaar] §r§eYour §r§aBuy Order §r§efor §r§a64§r§7x §r§fEnchanted Diamond §r§ewas filled!"),
        chat(2000, "§6[Auction] §b[MVP§c+§b] Player1 §ebought §fHyperion §efor §6456,000,000 coins §lCLICK"),
        side(2500, "hub", {"Purse": "Purse: §61,500,000 §e(+265,433)"}),
    ]),
    "items": ("Gives a Hyperion, a Golden Dragon pet, an Ultimate Wise book and a drill, and opens a menu with them", [
        loc(0, "hub", "Hub"), side(100, "hub"), tabl(200, "hub"),
        give(300, "hyperion"), give(350, "pet"), give(400, "book"), give(450, "soulbound"),
        {"t": 600, "type": "container", "title": "§8Your Bags",
         "items": [dict(items[k], slot=i, count=1) for i, k in enumerate(["hyperion", "pet", "book", "soulbound", "menu_glass"])]},
        {"t": 1600, "type": "container_closed"},
    ]),
}

index = {"_comment": "Built-in SkyBlock simulator scenarios (/hollow sbsim <name>). Generated from the unit-test fixtures.",
         "scenarios": [{"name": k, "description": v[0]} for k, v in scenarios.items()]}
json.dump(index, open(OUT + "index.json", "w", encoding="utf-8", newline="\n"), indent=2, ensure_ascii=False)
for name, (_, steps) in scenarios.items():
    with open(OUT + name + ".jsonl", "w", encoding="utf-8", newline="\n") as f:
        for s in steps:
            f.write(json.dumps(s, ensure_ascii=False) + "\n")
print("wrote", list(scenarios))
