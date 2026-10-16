# Hollow Client — SkyBlock Mode, 26.2 Port & Rebrand

This is the third plan, after `HOLLOW_PLAN.md` and `HOLLOW_VISUALS.md`. It adds:

1. A port of Hollow Client to Minecraft 26.2 (the version Hypixel SkyBlock recommends),
2. A brand-new logo and rebrand, and
3. A full Hypixel SkyBlock Mode: a big set of quality-of-life features covering every major part of SkyBlock, like Lunar Client's SkyBlock features, Skyblocker and SkyHanni, built into Hollow.

The owner can't test until everything is finished, so build the whole thing end to end, then make it as testable as possible without Hypixel (section 8).

## 0. How to work (same rules as before, plus a few more)

* Don't stop to ask for approval. Make decisions, log them in `DECISIONS.md`, track every item in `PROGRESS.md`, build and commit after each milestone, and keep going.
* Only stop if you're truly blocked. If so, write what's needed in `PROGRESS.md` and continue with everything else.
* This is the largest part of the project and will span many sessions. Keep `PROGRESS.md` detailed enough that a fresh session can pick up exactly where you left off.
* Look things up instead of guessing. SkyBlock changes constantly. Before building each area, check the current content on the official Hypixel SkyBlock Wiki (`hypixelskyblock.minecraft.wiki`) and recent changelogs. If something in this plan no longer exists in the game, skip it and note that in `DECISIONS.md`. If something important is missing from this plan, add it.
* Order of work:
   1. Bring whatever is in progress to a building state and commit it.
   2. Port to 26.2 (section 2).
   3. Rebrand (section 3).
   4. Launcher updates (section 4).
   5. SkyBlock foundation (section 5).
   6. SkyBlock features area by area (section 6).
   7. Testing tools and docs (sections 8 and 9).
   8. Any unfinished items from the first two plans get built on 26.2.

## 1. Hypixel rules (hard rules — never break these)

Hypixel treats all mods as use-at-your-own-risk and bans anything that gives an unfair advantage. The owner plays on their real account, so every SkyBlock feature must be display-only information or a cosmetic change. Based on Hypixel's published disallowed-modification rules, never build:

* Automation of any kind: no clicking, typing, moving, attacking, aiming, using items, or sending commands/chat for the player in response to game events. That means no macros (farming, mining, foraging, fishing), no auto-reel/auto-fish, no auto-clickers, no auto-reparty, and no auto-replies.
* Automatic interaction with menus/GUIs: no auto-terminals, auto-experiments, auto-harp, auto-melody, auto-sell, auto-buy, auto-salvage, or auto-clicking in the Chocolate Factory. Solvers may only highlight the answer. The player always does every click.
* Revealing hidden information: no showing invisible entities (ghosts, invisible mobs, hidden boss phases, etc.), no through-wall rendering of entities (ESP), no X-ray of blocks (ores, gemstones, chests), and no revealing unexplored dungeon rooms beyond what the dungeon map item shows.
* Movement or packet changes: nothing that changes movement or physics, modifies or fakes packets, or cancels vanilla actions. The one allowed exception is item protection: preventing the player from dropping, selling or salvaging items they've locked.
* Ghost blocks, "secret aura", auto-leap, or anything similar from cheat clients.

Allowed:

* HUDs, overlays, tooltips, timers, trackers and alerts.
* Highlights of things the player can already see (depth-tested, so they don't show through walls).
* Waypoints for fixed, known map locations (fairy souls, enigma souls, dungeon secret locations per room, NPCs and structures).
* Solvers that only display the answer.
* Keybinds or buttons where one press by the player = one action, like opening a menu or running one command.

When in doubt, leave it out and list it in `DECISIONS.md` under "Skipped for Hypixel rules".

## 2. Port to Minecraft 26.2

Hypixel SkyBlock has dropped older versions and recommends 26.2. Hypixel has said it only keeps supporting the two newest Minecraft releases.

* Before starting, tag the current 1.21.11 state as `legacy-1.21.11` and create a `legacy/1.21.11` branch. Freeze it: no new features there, it's just kept so the old version still works.
* Port `main` to 26.2. Read Fabric's porting guides and blog posts for 26.1 and 26.2 first (`fabricmc.net`, `docs.fabricmc.net`). Key changes, as of 26.1 (check whether 26.2 changed them):
   * Minecraft is no longer obfuscated. Fabric now uses Mojang's official names, and Yarn is no longer supported. Migrate all names.
   * Use the new `net.fabricmc.fabric-loom` plugin, which doesn't remap. Replace `modImplementation`/`modCompileOnly` with `implementation`/`compileOnly`, and `remapJar` with `jar`. Use current Loom, Gradle and Fabric Loader versions.
   * Java 25 is required, both to build and to run the game.
   * Many Fabric API names changed. For example, `HudRenderCallback` was replaced by `HudElementRegistry`. Depend on `fabric-api`, not `fabric`.
   * `ItemStack` can't be created before a world loads. Use `ItemStackTemplate` where needed.
   * Raw OpenGL calls must go through the Blaze3D API. 26.2 can also run on Vulkan, so all Hollow rendering, shaders and post-processing must work through Blaze3D on both backends. Any effect that can't work on Vulkan gets disabled automatically there.
* Get every existing feature from the first two plans working on 26.2, then continue with this plan.
* Write `VERSION_BUMP.md`: an exact checklist for moving Hollow to the next Minecraft version (e.g. 26.3). Make version-specific values (game version, dependency versions) live in one place (`gradle.properties` + launcher config), so a future bump is mostly mechanical.

## 3. New logo & rebrand

Design a new, fully original Hollow Client identity. It must not look like Minecraft's, Hypixel's, Lunar's or any other client's logo.

* Direction: a hollow geometric emblem, e.g. a faceted ring, hexagon or crystal with an "H" cut out as negative space so the center is literally hollow. Give it a glowing violet→cyan edge, and maybe a small orbiting spark. It should read clearly at 16×16 and look premium at 1024×1024.
* Create 3 concepts as SVGs in `branding/concepts/`. Pick the strongest, explain why in `DECISIONS.md`, and keep the others so the owner can swap.
* Build the chosen logo as:
   * a master SVG
   * full-color glow, flat single-color (white and black) and simplified small-size versions
   * PNGs at 16, 32, 48, 64, 128, 256, 512 and 1024
   * a multi-size `.ico` for the launcher exe
   * the mod icon (`icon.png`)
   * a wordmark: "HOLLOW" bold with "CLIENT" small, using the OFL font from the visuals plan
   * a SkyBlock Mode badge: the emblem with a small floating island
* Animated logo: update the intro, splash screen, title screen and launcher animations from `HOLLOW_VISUALS.md` to use the new emblem (e.g. facets assembling, the H "hollowing" out, glow pulse).
* Apply the rebrand everywhere: launcher, window icon, mod icon, splash, title screen, mod menu header, built-in capes and README.
* Make a GitHub banner and a 1280×640 social preview image. Document how the owner can upload the social preview in repo settings.
* Add a "SkyBlock" theme preset: sky blue + gold accents on the void base.

## 4. Launcher updates

* Version profiles, each with its own instance folder under `%APPDATA%\.hollowclient\instances\`:
   * Hollow 26.2 — SkyBlock ready (default): Minecraft 26.2, the latest Fabric Loader for 26.2, Fabric API, Sodium, the Hypixel Mod API mod (official, MIT license, needed for SkyBlock detection) and the Hollow 26.2 jar.
   * Hollow 1.21.11 — Legacy: the frozen legacy build with its old dependencies.
   * All dependencies are downloaded from Modrinth at install time for the right game version. Never bundle third-party jars.
* Java: the 26.2 profile uses a Java 25 runtime and the legacy profile uses Java 21. Use Mojang's official runtimes through minecraft-launcher-lib.
* "Join Hypixel" button: launches the 26.2 profile straight into `mc.hypixel.net` using Minecraft's quick-play multiplayer launch argument (check the exact argument for 26.2). The owner then types `/skyblock` themselves.
* SkyBlock dashboard page in the launcher, in the crazy-UI style, using public data that needs no API key:
   * current mayor/election and perks
   * bazaar top movers
   * upcoming events (Dark Auction, Jacob's contests, festivals, etc.)
   * Hollow's own session stats from the mod
* Detect if Skyblocker, SkyHanni or Firmament were added to the 26.2 instance, and warn about duplicate features (see 5.7).
* Update tests and `build_all.bat` for both profiles.

## 5. SkyBlock Mode — foundation

### 5.1 Detection & "SkyBlock Mode"

* Use the Hypixel Mod API (location and party info) to know when you're on Hypixel, in SkyBlock, and on which island/area. Fall back to scoreboard and tab-list parsing if it's missing.
* When SkyBlock is detected, Hollow switches into SkyBlock Mode:
   * a slick animated toast with the SkyBlock badge
   * the SkyBlock HUD layout loads
   * SkyBlock modules turn on
   * the active features change automatically per island (Garden, Dungeons, Dwarven Mines, Crimson Isle, Rift, etc.)
* SkyBlock modules do nothing anywhere else.
* Detect the current profile (name, ID, game mode like Ironman/Stranded/Bingo) and store all tracked data per profile.

### 5.2 Data layer (architecture)

Build this layer cleanly so features never parse raw text themselves:

* Sources: chat, sidebar scoreboard, tab list widgets, action bar, titles, boss bar, open container/menu contents, item data, sounds, particles and the dungeon map item.
* Parsers turn sources into a single `SkyBlockState`: island, area, purse, bits, health/mana/defense, slayer quest, dungeon state, garden visitors, pests, commissions, etc. Features read only from `SkyBlockState` and an event bus (e.g. `SlayerBossSpawned`, `RareDrop`, `VisitorArrived`).
* Items: identify SkyBlock items by their SkyBlock item ID in the item's custom data, never by vanilla material or name. Hypixel now uses many plain vanilla items plus an official resource pack. Parse UUID, reforge, enchants, stars, gems, hot potato books, recombobulation, pet info, etc.
* Keep all text patterns (regexes, known strings) in data files, not scattered through code, so they're easy to fix after testing.

### 5.3 Item data & prices

* Item database: use the community item repo (NEU repo) that current mods use. Check its license and download it at runtime.
* Hypixel public API endpoints that don't need a key (bazaar, auctions, election, resources). Respect rate limits and cache aggressively.
* Lowest-BIN / average price data: find which public sources current mods use (check their source code), and handle outages gracefully.
* Optional Hypixel API key field, disabled by default, only for extras that truly need it. Store it securely and never log it.

### 5.4 SkyBlock menu & HUD

* A SkyBlock category in the Right Shift menu, with sub-tabs for each area below, plus search, per-feature toggles and settings, all in the Hollow crazy-UI style.
* SkyBlock HUD elements plug into the existing HUD editor, with island-specific HUD presets.
* SkyBlock Hub screen (keybind, default `H`): a full-screen animated dashboard of timers, trackers, profit, prices and alerts.

### 5.5 Alerts system

* Configurable alerts (title + sound + toast) driven by events and chat patterns, with dozens of built-in alerts listed in section 6 and an editor for custom ones.

### 5.6 Setup checker

* A screen that checks whether everything each feature needs is available (Mod API present, required tab list widgets enabled in Hypixel's settings, prices loaded). For anything missing, it tells the owner exactly what to enable in-game.

### 5.7 Compatibility

* If Skyblocker, SkyHanni or Firmament are installed, detect them and offer to disable overlapping Hollow features, to avoid duplicate HUDs and mixin conflicts.

## 6. SkyBlock features

Build all of these as display-only, following section 1. Verify each against the current game first.

### 6.1 General & HUD

* Custom health / mana / overflow mana / defense / effective HP bars that replace the action bar text (toggle).
* Option to hide vanilla hearts, armor and hunger in SkyBlock.
* Fancy tab list: Hypixel's tab widgets reorganized into themed panels.
* Restyled sidebar scoreboard.
* Ability cooldowns on the hotbar, pet HUD (current pet, level, XP progress), quiver/arrow count and deployable timers (orbs, flares, totems).
* Compact damage numbers (e.g. "1.2M") and hiding other players' damage numbers.
* Visual-only clutter hiders: other players in busy hubs, far entities, lightning and particle spam.
* Fairy soul waypoints with found-tracking per profile (fixed locations).
* Waypoint system: create, share and import waypoints, plus clickable coordinates in chat.
* Island/area info HUD and a "time until next SkyBlock event" HUD.

### 6.2 Items, inventory & GUI

* Rich tooltips: NPC sell, bazaar buy/sell, lowest BIN, average BIN, craft cost, estimated total value (base + reforge + enchants + gems + stars + books + recomb), item age, motes and essence value, museum status. Show extra details while holding Shift.
* In-slot info: rarity backgrounds, dungeon stars, pet levels, enchanted book names, minion tiers, and skill/collection levels as stack sizes in menus.
* Item protection: lock slots/items so they can't be dropped, sold or salvaged.
* Inventory search that highlights matches.
* Storage overview: ender chest and backpack previews, plus a full storage overlay, all from menus you've opened.
* Item browser: search every item, see recipes, where to get it, and a wiki link.
* Sack contents overlay and accessory bag helper (missing accessories, magical power, cheapest upgrades by price).
* Reforge helper and enchanting price info (display only).
* Quick-nav buttons above inventories (one click = one command, e.g. pets, storage, wardrobe).
* Experimentation table helpers that only display (Chronomatron, Ultrasequencer, Superpairs memory) and harp helper highlights. Never click.

### 6.3 Economy

* Bazaar: price graphs on hover, order tracker with "order filled" alerts (from chat), and flip margins.
* Auction House: price comparison overlay on listings (underpriced/overpriced vs lowest BIN) and a tracker for your own auctions.
* Profit trackers per activity with coins/hour (slayer, dungeons, farming, mining, fishing, Diana, Kuudra).
* Minion helper: profit per minion, fuel/storage info and a hopper coins tracker.

### 6.4 Garden & farming

* Visitor helper: list of visitors, items they want, bazaar cost to fulfill, reward value (copper, items) and profit/loss per visitor, plus a "new visitor" alert.
* Pest tracker: pests per plot (from tab), a plot map overlay showing infested plots, pest cooldown and repellent timers, and alerts.
* Jacob's contests: next contests and crops (public contest calendar sources), plus a live medal-bracket estimate during contests.
* Farming HUD: blocks/sec, crops/hour, coins/hour (NPC vs bazaar), crop milestones, farming fortune info, tool counter/cultivating, and a yaw/pitch display.
* Composter HUD (organic matter, fuel, time left) with an "empty" alert.
* Garden level/XP, copper, plot names and borders.

### 6.5 Dungeons

* Dungeon map HUD from the map item: player markers with names/classes, room states and checkmarks. No revealing unexplored rooms.
* Room detection + secret waypoints for each room (fixed room data) and a secret counter.
* Puzzle solvers (display only): Three Weirdos, Creeper Beams, Higher or Lower/Blaze, Teleport Maze, Water Board, Tic Tac Toe, Ice Fill, Ice Path, Boulder and Quiz.
* Floor 7 / Master Mode terminals and devices: highlight the correct slots/blocks only (order, colors, starts-with, panes, rubix, Simon Says, lights, arrow alignment). Never click.
* Score calculator (S / S+ estimate from secrets, crypts, deaths, puzzles and time) with alerts at 270 and 300.
* Crypt counter, mimic/prince detection, blood room timer, boss phase split timers, wither/blood key tracker and an ultimate/class ability cooldown HUD.
* Livid finder (identifies the right Livid from visible info) and highlights for visible starred mobs. Depth-tested only.
* Spirit leap menu overlay (names + classes), and Bonzo/Spirit mask cooldown display.
* Croesus & reward chests: value of every chest, the most profitable highlighted, and a Kismet reminder.
* Party finder overlay: color-code listings by class/level shown in the menu.
* Dungeon run tracker: runs, time, score and profit per floor.

### 6.6 Slayers

* Slayer HUD: quest progress, boss HP/phase, spawn and kill timers, XP/hour, RNG meter progress, drop tracker with prices and profit/hour.
* Boss-specific helpers (display only), checked against the current game:
   * Revenant Horror, Tarantula Broodfather and Sven Packmaster: phase/spawn alerts.
   * Voidgloom Seraph: hits-shield phase, beacon alert, Yang glyph alerts and Nukekubi head count.
   * Inferno Demonlord: current attunement and which dagger to use, demon attunements and fire pillar countdown.
   * Riftstalker Bloodfiend: Twinclaws alert, Mania/Ichor info and a "steak stake" HP threshold cue.
* Only your boss is highlighted (detected from its "Spawned by" tag), and only when visible.
* Maddox batphone cooldown.

### 6.7 Mining

* Commissions HUD and completion alerts, powder trackers (mithril/gemstone/glacite per hour), pickaxe ability ready alert and mining speed stats.
* Dwarven Mines events and timers, Fetchur's item of the day, Puzzler solver and King/Queen info.
* Crystal Hollows: structure/NPC waypoints (fixed or discovered when you visit), crystal nucleus progress and a metal detector solver that shows the treasure location from the distance readings.
* Glacite Tunnels / Mineshafts: mineshaft type detection, cold meter HUD, corpse counter (visible only), fossil excavator solver (display) and glacite powder tracker.
* Forge timers with "ready" alerts.

### 6.8 Fishing

* Big "fish on!" visual and sound cue. Never auto-reel.
* Sea creature tracker and counter, rare catch alerts with a clickable "share to party" button (the player clicks to send), and a lobby cap warning.
* Trophy fish tracker (collection progress per fish and tier), fishing profit tracker, and Golden Fish and event timers (Fishing Festival, Spooky, Jerry's Workshop).
* Hide other players' bobbers (visual).

### 6.9 Foraging, Hunting & newer content

* Foraging and any newer skills or areas currently in the game (e.g. the Foraging update areas and Hunting, if present). Check the wiki and build trackers and helpers for them in the same style.

### 6.10 Crimson Isle & Kuudra

* Kuudra: phase timers, supply/fuel progress, static pile/drop-off waypoints, paid chest profit values and key reminders.
* Crimson Isle: faction reputation, daily quest tracker, miniboss timers, Dojo hints (display only) and attribute/shard pricing as it currently works.

### 6.11 The Rift

* Rift time remaining HUD, motes tracker, Enigma soul waypoints with found-tracking, effigy status display, and display-only puzzle paths/helpers for current Rift puzzles.

### 6.12 Events & calendar

* Mythological Ritual (Diana): burrow guess from the spade trail particles, waypoints for burrows you detect, Inquisitor alert with a clickable share button, loot tracker and Griffin pet reminder. Optionally, a keybind that warps to the nearest warp when pressed.
* Chocolate Factory / Hoppity: best upgrade suggestion, Time Tower and full-barn alerts, stray rabbit highlight in the menu (never auto-click), and an egg locator from the locator item's particles.
* Dark Auction, Jacob's, Spooky Festival, Season of Jerry, Traveling Zoo, Cult of the Fallen Star and New Year timers. Mayor/election perks display.
* Bingo card overlay with goal progress.

### 6.13 Progression & stats

* Skill XP/hour and time-to-level for every skill, skill average, collections tracker, HOTM/powder overview, bestiary progress (from menus), museum tracker and magical power.
* Reminders: Booster Cookie and God Potion expiry, Experimentation table reset, daily/commission resets and bank interest.

### 6.14 Chat & party

* SkyBlock chat filters by category (ability spam, lobby joins, NPC talk, etc.) with show/hide toggles.
* Rare drop announcer with item price in chat.
* Party HUD (members, classes in dungeons).
* Clickable coordinates and shareable waypoints in chat.

## 7. Using code from other SkyBlock mods

You may study and reuse code from open-source SkyBlock mods, but only legally:

* Good references: Skyblocker (Java, Fabric, the closest match to Hollow), SkyHanni and Firmament (both Kotlin, so port their logic to Java rather than pasting), and older 1.8.9 mods like NotEnoughUpdates and Skytils for logic ideas. Also use the Hypixel Mod API (MIT).
* Check each project's license before copying anything, and only copy from projects whose license allows it. Never copy from projects with no license, and never copy from cheat clients.
* Skyblocker, Firmament and SkyHanni use GPL/LGPL licenses. If you copy their code or data, license Hollow Client as GPL-3.0-or-later: add a `LICENSE` file, keep the original copyright and license headers on copied files, and list every source in `THIRD_PARTY_NOTICES.md`.
* Prefer re-implementing in Hollow's own architecture (section 5.2). Copy only where it saves real work, like solver logic, room/secret data or parsing patterns.

## 8. Testing without Hypixel

You can't join Hypixel, so make the owner's testing easy and make bugs easy to fix later:

* Parser unit tests for every parser, with realistic fixture strings for scoreboard lines, tab widgets, chat messages, action bars and item data, gathered from wiki pages and other mods' test data. Aim for high coverage of the data layer.
* SkyBlock Simulator: a dev command (e.g. `/hollow sbsim <scenario>`) for singleplayer that feeds fake data into the data layer, so the owner can preview HUDs and overlays for scenarios like garden with visitors and pests, a dungeon run, a slayer boss fight, mining commissions and the Rift.
* Debug recorder (toggle) for real Hypixel sessions. It saves scoreboard, tab list, action bar and unrecognized chat lines, plus a keybind to dump the hovered item's data, into `hollow-debug/`. Exclude private messages by default. The owner can hand these files to Claude Code to fix parsers.
* Feature health screen: lists each SkyBlock feature with ✓/✗ for whether its data is being detected right now.
* `TESTING_SKYBLOCK.md`: a checklist per area for the owner to test in-game, in a sensible order.

## 9. Finish

* `gradlew build` passes on 26.2, the legacy branch still builds, all tests pass, and the launcher builds with both profiles.
* Update `USER_GUIDE.md`:
   * SkyBlock Mode overview and the setup checker
   * which Hypixel tab widgets to enable
   * keybinds (Right Shift menu, H SkyBlock Hub, etc.)
   * the debug recorder
   * how to play SkyBlock before the launcher's Microsoft login is approved: install Fabric for 26.2 with the official installer, then put Fabric API, Hypixel Mod API, Sodium and the Hollow jar in the mods folder and launch from the official Minecraft Launcher
* Update `README.md` with the new branding, SkyBlock Mode and the license info.
* Commit and push. End `PROGRESS.md` with a summary: what's done, what to test first, what was skipped for Hypixel rules, and what's left.
