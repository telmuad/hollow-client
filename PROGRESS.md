# Hollow Client — progress

This file is the source of truth for where the build is. A new session should read it first and pick up at the first unticked item.

## Resume here

**Plan 3 (26.2 port, rebrand, SkyBlock Mode)** — the full request is in `plans/HOLLOW_SKYBLOCK.md`. Current step: the
first unticked item below. Plans 1 and 2 (Minecraft 1.21.11) are finished and frozen on the `legacy/1.21.11` branch
(tag `legacy-1.21.11`); their log is further down.

## Plan 3 milestones

- [x] **P0. Freeze legacy**: tag `legacy-1.21.11`, branch `legacy/1.21.11` (README note says frozen), pushed
- [x] **P1–P3. Port to Minecraft 26.2**
  - [x] Build: `net.fabricmc.fabric-loom` 1.18.4 (no remapping), Gradle 9.7.1, Java 25 (`release 25`), MC 26.2, Loader 0.19.5, Fabric API 0.161.0+26.2, Sodium mc26.2-0.9.2 for `-PwithSodium`; access widener in the `official` namespace; mod version 2.0.0
  - [x] Fixed on the way: nebula pipeline declared `Globals` twice (OpenGL refused it); chat timestamp gray spilling into the message; per-letter HUD text drifting and getting clipped
  - [x] Code: GUI (`GuiGraphics` → `GuiGraphicsExtractor`, `render` → `extractRenderState`, screens via `mc.gui`, HUD via `mc.gui.hud`), chat classes moved, `LightTexture` → `LightCoordsUtil`/lightmap extractor, keybinds (`KeyMappingHelper`), level render events (`LevelRenderEvents.COLLECT_SUBMITS` + submit nodes), render pipelines (bind group layouts, `ColorTargetState`, reversed depth), post-processing passes, `Immediate` helper instead of Tesselator, item icons before a world loads
  - [x] All 44 mixin targets re-checked against the 26.2 bytecode (script + javap); FOV moved to `Camera.calculateFov`, title-screen swap to `Gui.setScreen`, time of day to `ClientClockManager`, hotbar/scoreboard/titles to `Hud`
  - [x] Gametest suite green on OpenGL: 61/61 checks (new: Hollow's shaders compile) + FPS benchmark, screenshots reviewed
  - [x] Gametest 61/61 with Sodium 0.9.2 and on Vulkan (`-Pvulkan` → `--graphicsBackend vulkan`)
  - [x] Mixin config `required: true`; 99 JUnit tests incl. `NoRawOpenGlTest`; `tools/mixincheck.py` reports 0 problems
  - [x] `VERSION_BUMP.md`; TESTING.md FPS tables for 26.2 (OpenGL, Sodium, Vulkan); README/USER_GUIDE mod versions (launcher text follows in L1)
- [x] **R1. Rebrand**
  - [x] `branding/tools/emblem.py` draws everything from one geometry; 3 concepts in `branding/concepts/` (A Facet Hex chosen, reasons in DECISIONS)
  - [x] Master SVG + small/flat white/flat black SVGs, PNGs 16–1024, multi-size ICO, mod `icon.png`, wordmark (Inter), SkyBlock badge, `banner.png`, 1280×640 `social-preview.png` (+ upload steps in `branding/README.md`)
  - [x] In game: theme-tinted emblem layers (`gui/Emblem.java`) with the intro (facets fly in, H hollows out, glow breathes) on the title and loading screens; menu header, toasts and all other sigils use it; new wordmark; brand cape carries a pixel emblem
  - [x] Launcher: icon, title bar, home page, new intro (particles → facets → hollow H → glow → wordmark); old code-drawn logo removed
  - [x] SkyBlock theme preset (sky blue → gold) in mod and launcher
  - [x] Launcher tests no longer read the real `config.json` or open a browser (autouse fixture)
- [x] **L1–L2. Launcher** (launcher 2.0.0)
  - [x] `profiles.json` + `profiles.py`: "Hollow 26.2 — SkyBlock ready" (default; Fabric API, Sodium, Hypixel Mod API) and "Hollow 1.21.11 — Legacy" (Fabric API, Sodium); profile switch on Home, remembered in settings
  - [x] Shared root (versions/libraries/assets/runtime) + `instances/<id>` game folders; installer, launch (`--gameDir`, cwd), Java runtime per version json, jars matched by `depends.minecraft`
  - [x] Migration of a 1.x install into `instances/legacy-1.21.11` (rename, never delete, `migration.txt`, runs once)
  - [x] Join Hypixel (Home and SkyBlock page): switches to the 26.2 profile, `quickPlayMultiplayer = mc.hypixel.net` for that launch only
  - [x] SkyBlock page + `skyblock_data.py`: mayor/minister/election, calendar math (wiki-checked), Jacob's contests (Elite), bazaar movers (hourly snapshots) and margins, mod session totals; disk cache, stale-when-offline
  - [x] Duplicate-feature note for Skyblocker/SkyHanni/Firmament (by mod id); theme sync across instances; stats per profile
  - [x] `build_all.bat` builds the legacy jar from the `build/legacy` worktree and bundles both; `hollow.spec` bundles `profiles.json`; self-test checks a jar per profile
  - [x] pytest 121 (new: profiles, migration, conflicts, theme sync, quick play, backend profile switching, calendar, election, contests, bazaar, cache/outages, SkyBlock page QML); screenshots of Home and SkyBlock reviewed
  - [x] `build_all.bat` end to end: both jars built and bundled, launcher packaged, packaged self-test OK
  - [x] Real install into the real `%APPDATA%\.hollowclient` (2026-10-16, started outside the app container): the 1.x install migrated into `instances/legacy-1.21.11` (mods, config, saves, options, logs, packs; shared files stayed); 26.2 profile installed with `java-runtime-epsilon` (25), Fabric 0.19.5, Fabric API 0.161.0+26.2, Sodium 0.9.2, Hypixel Mod API 1.0.2, Hollow 2.0.0; legacy profile recognised as installed with `java-runtime-delta` (21) and Hollow 1.1.0
  - [x] First play of a profile copies your Hollow config, `options.txt` and server list from the other profile (only when it has no Hollow config yet)
- [x] **F1–F6. SkyBlock foundation**
  - [x] Data layer (`skyblock/`): game text → `SkyBlockInput` → parsers (`SidebarParser`, `TabListParser`, `ActionBarParser`, `ChatParser`, `ItemParser`) → `SkyBlockState` + typed `SbEvent`s on an `EventBus`; patterns in `assets/hollowclient/skyblock/patterns/*.json` (overridable from `config/hollowclient/skyblock/patterns/`)
  - [x] Game adapter (`skyblock/mc/GameReader`): sidebar, tab list, open containers, item custom data → JSON; chat and action bar/titles via mixins (`ChatComponentAddMixin`, new `HudMessagesMixin`); Hollow's own overlay messages excluded
  - [x] Detection: Hypixel Mod API location (+ party) packets when the mod is installed (compile-only dependency, loaded by reflection), else scoreboard title / tab "Area:" / Rift, dungeon and Kuudra lines; profile name, id and game mode (♲ ☀ Ⓑ); per-profile store
  - [x] Debug recorder (`/hollow sbrec on|off`, JSONL in `hollow-debug/`, other players' chat never recorded, names anonymised), item dump (`/hollow sbdump`), simulator (`/hollow sbsim <scenario>|list|stop`, singleplayer only; scenarios hub, garden, slayer, dungeon, rift, items generated from the test fixtures by `mod/tools/skyblock_scenarios.py`; recordings replay too), `/hollow sbstate`
  - [x] Session totals (`SessionStats`) saved as `session_stats.json` per profile for the launcher's SkyBlock page
  - [x] Data: `Http` disk cache with TTLs and stale-on-outage, `Prices` (bazaar, Elite lowest BIN incl. pets by rarity/level, NPC prices and names), `NeuRepo` (MIT zip, daily commit check, read in place), refreshed in the background only while on SkyBlock
  - [x] Tests: 42 JUnit (parsers with fixtures, input router, recorder privacy, every scenario, prices, cache, NEU repo); gametest 70/70 incl. 9 SkyBlock checks (server action bar and chat read, simulator end to end, items with SkyBlock data in singleplayer)
  - [x] Menu: SkyBlock category (nether star) with area sub-tabs; `SkyBlockModule`/`SkyBlockHudModule` base classes (island gating, feature health, overlaps)
  - [x] SkyBlock Mode module: badge toast on join, SkyBlock HUD layout (shared or per island, swapped back on leaving and at startup), downloads switch; SkyBlock Info HUD and SkyBlock Session HUD
  - [x] Hub (H): session, calendar, held item value, mayor (keyless election resource), latest events, tools; alerts engine (built-ins + your own chat regex alerts, title/sound/toast, cooldowns) + editor; setup checker; feature health; compatibility (Skyblocker/SkyHanni/Firmament); hover-dump key (unbound) and "Save held item"; optional API key stored with Windows DPAPI (JNA ships with Minecraft)
  - [x] Tests: 51 SkyBlock JUnit tests (150 in all); gametest 75/75 incl. screenshots of the join toast, SkyBlock HUD, an alert, Hub, setup checker, feature health, alerts editor, compatibility and the SkyBlock menu category
  - [x] TESTING_SKYBLOCK.md started (setup checker + foundation)
- [ ] **A1–A14. SkyBlock features** (see the plan, section 6)
  - [x] A1 General & HUD: Status Bars (+ action bar trimming), Hide Vanilla Bars, Pet, Next Events, Deployables, Compact Damage, Clutter Hider, Fairy Souls (NEU locations, found per profile, `/hollow sbsouls`); shared SkyBlock waypoint source; `general` scenario (with simulated name tags); SkyBlock HUD elements left out of the layout swap; scenarios stay on screen until `/hollow sbsim stop`; 56 SkyBlock unit tests; gametest 80/80
  - [x] A2 Items, inventory & GUI: Price Tooltips (bazaar, lowest BIN incl. pets, NPC, craft cost from NEU recipes, estimated value with Shift breakdown, museum, age), Slot Info (rarity backgrounds, pet level/stars/book/minion labels), Item Protection (lock key; blocks drop, throw, drop-outside and selling/salvaging/auctioning/trading locked items), Menu Tools (search dimming, quick-nav command buttons), Experiment Helpers (Ultrasequencer, Chronomatron, Superpairs; display only); gametest 84/84 with screenshots of the tooltip, search and Ultrasequencer helper. Left for later: storage previews/overlay, item browser, sack and accessory helpers, reforge/enchanting helpers, harp helper
  - [x] A3 Economy: Bazaar Orders (chat-tracked orders per profile, "filled" alert, 24h sparkline + change and flip margin after tax in tooltips, hourly price history), Auctions (cheap/pricey corners on BIN listings vs lowest BIN, sold auctions + "sold" alert, coins to collect), Coins per Hour (`/hollow sbprofit reset`); `economy` scenario; gametest 86/86. Left: per-activity profit trackers (added per area), minion helper
  - [x] A4 Garden & farming: Garden HUD (level, copper, visitors, pests by plot from chat + tab) with a pests alert, Visitor Offer (items, bazaar cost, coins per copper), Jacob's Contests (Elite API crops), Farming HUD (blocks/s, tool counter per hour, yaw/pitch), Composter (tab widget); garden scenario with pests and a visitor menu; gametest 88/88. Left: plot map/borders, crop milestones, fortune breakdown, repellent timers
  - [x] A5 Dungeons: Dungeon HUD (wiki score formula with S/S+ alerts, secrets/crypts/deaths/puzzles from the tab list, keys, split times, last run), Dungeon Map (the map item as is), Terminal Helper (order, color, starts-with, panes, same color with click counts), Blaze Puzzle order, Spirit Leap Classes, Reward Chest Value; gametest 89/89 with HUD and terminal screenshots. Left: Three Weirdos and other room puzzles (unverified data / room geometry), room detection + secret waypoints, Livid finder, starred mob highlights, party finder overlay, ability cooldowns
  - [x] A6 Slayers: Slayer HUD (quest progress from the scoreboard, your boss's tags from its "Spawned by" tag, fight time, kills/average/per hour, drop value per session and hour), Boss Highlight (depth-tested box, your boss only); simulator summons a frozen mob; SlayerTest + gametest section with a screenshot (97/97 checks). Left: miniboss alerts, Demonlord attunement / Voidgloom hit-phase / beacon / Twinclaws helpers (need recordings)
  - [ ] A7 Mining
  - [ ] A8 Fishing
  - [ ] A9 Foraging, Hunting & newer content
  - [ ] A10 Crimson Isle & Kuudra
  - [ ] A11 The Rift
  - [ ] A12 Events & calendar
  - [ ] A13 Progression & stats
  - [ ] A14 Chat & party
- [ ] **T/Z. Finish**: TESTING_SKYBLOCK.md, USER_GUIDE, README, LICENSE/THIRD_PARTY_NOTICES, final summary

### Plan 3 notes

- L1–L2: the launcher's session-totals file format (`session_stats.json`) is fixed in DECISIONS; the mod writes it in the F/A milestones.
- Reference sources for porting: decompiled 26.2 game code via `gradlew genSources` (Loom cache), Fabric API sources from maven.fabricmc.net, NeoForge primers for 26.1 and 26.2.

## Plan 1 and 2 log (Minecraft 1.21.11, finished)

### Milestones

- [x] **0. Setup**: repo cloned, layout (`mod/`, `launcher/`, `scripts/`), `.gitignore`, doc skeletons
- [x] **1. Mod skeleton**: Gradle project (Loom 1.18.4, MC 1.21.11, Loader 0.19.5, Fabric API 0.141.6), `fabric.mod.json`, entrypoint, JUnit wired into `gradlew build`
- [x] **2. Core systems**: config (`config/hollowclient.json`), module + settings system, registry, keybinds (Controls menu) + tests
- [x] **3. Mod menu**: Right Shift menu, categories, module cards, settings screen (toggle, slider, enum, color picker, keybind picker)
- [x] **4. HUD**: FPS, coordinates, CPS, keystrokes, armor, potions, ping, clock, toggle indicator; HUD editor (drag, scale, snap, reset, resize-safe positions) + tests
- [x] **5. QoL / visual**: zoom, toggle sprint/sneak, fullbright, custom crosshair, low fire, clear water, hit color
- [x] **6. Styles**: texture generator, capes (built-in + PNG import), hat + wings, texture pack import/switch, presets + tests
- [x] **7. Mod polish**: `-PwithSodium` dev run, TESTING.md, mixin review, in-game checks
- [x] **8. Launcher core**: paths, settings, config, Modrinth, installer + pytest; real install into `%APPDATA%\.hollowclient`
- [x] **9. Launcher auth**: Microsoft OAuth (PKCE) with local redirect server, keyring token store, refresh, log out, "not approved yet" message + pytest
- [x] **10. Launcher UI**: sidebar pages, PLAY, progress, account badge, logs, settings, icon
- [x] **11. Packaging**: `launcher/build.bat`, PyInstaller spec, bundled mod jar, frozen `--selftest`
- [x] **12. Tie together**: `scripts/build_all.bat`, README, USER_GUIDE, final summary, push

## Notes / blockers

- In-game checks: I use Fabric's client game-test API (`gradlew runClientGameTest`). It runs the real client, creates a test world,
  simulates keys and saves screenshots to `mod/build/run/clientGameTest/screenshots`, which I review. Behaviour checks
  log `CHECK ...: PASS/FAIL` and fail the run on any failure.
- Milestone 5 run (2026-10-13): 8/8 checks pass (menu key, CPS, zoom, zoom scroll keeps hotbar slot, zoom release,
  toggle sprint on/off); all mixins applied with no errors; screenshots reviewed (fullbright, hit color, low fire,
  clear water, crosshair, menu, color picker, HUD editor).
- Milestone 6 run: 15/15 checks pass (adds built-in cape, PNG cape import, wrong-size cape refused, pack import,
  pack switch, preset save/apply incl. pack, pack removal). Screenshots reviewed: cape/hat/wings in F5 front and back,
  imported cape, magenta test pack on stone, all four Styles tabs. Wings were redone after the first run (stray edge
  lines, droopy pose).
- Milestone 7: the full game test also passes with Sodium 0.8.14 (`-PwithSodium`), 15/15 at that point; fullbright, clear
  water and cosmetics screenshots look the same as without Sodium. Then a hands-on pass driving the real dev client
  (access granted on 2026-10-14): menu, card toggles, settings, keybind picker (bound K, toggles with an action-bar
  message), color picker (swatch + drag), HUD editor (drag with snap guide, scroll scale, right-click reset, Done),
  Styles tiles, capes, cosmetics, the native file picker (opened and cancelled), F5 view. Fixes from that pass: swatch
  outlines, a hint for the Styles tiles, and Right Shift now also works on the title and pause screens (16/16 checks).
- Milestone 8: real install into %APPDATA%\.hollowclient with `python -m hollow_launcher --install-only` worked first try
  (Minecraft 1.21.11, Mojang Java runtime 21.0.7 "java-runtime-delta", Fabric Loader 0.19.5 via the official Fabric
  installer, Fabric API 0.141.6, Sodium 0.8.14 release (not the newer beta), hollowclient-1.0.0.jar; 672 MB). A second
  run (repair) took 9 s and downloaded nothing. `--check-updates` reports both mods up to date. 31 pytest tests pass.
- Milestone 10: launcher window (Home, Styles & Mods, Settings, Logs; account badge; PLAY flow install -> sign in ->
  launch; repair; mod updates with a banner; live game log). Reviewed offscreen screenshots of every page, the
  "not approved yet" state and a small window (`--screenshots`); fixed a mnemonic "&", a disabled-button style and the
  card proportions. The launch command was built (not run) against the real install: Mojang Java runtime, Fabric
  KnotClient, token shown as ******** in logs. 65 pytest tests pass (incl. offscreen UI smoke tests).
- Milestone 11: `launcher\build.bat` (venv, pinned packages, pytest, PyInstaller, then `HollowClient.exe --selftest`)
  ran green: `launcher\dist\HollowClient\HollowClient.exe`, 104 MB folder, mod jar/icon/logo bundled, self-test exit 0.
  Opened the packaged exe for real: dark title bar, PLAY without config.json shows the setup steps, the startup
  Modrinth check reports everything up to date.
- Milestone 12: `scripts\build_all.bat` ran green end to end (mod build + 57 JUnit tests, jar bundled, 65 pytest tests,
  PyInstaller, packaged self-test). README extended (original text kept), USER_GUIDE written, pushed to GitHub.

## Final summary

### What works (verified)
- **Mod** (`mod\build\libs\hollowclient-1.0.0.jar`, Fabric 1.21.11, client-only): Right Shift menu (also on the title
  and pause screens) with categories, search, cards, per-module settings (toggles, sliders, choices, color picker,
  keybind picker), keybinds in Controls; config in `config/hollowclient.json` with corrupt-file fallback; 9 HUD
  elements with text color/background/shadow/scale; HUD editor (drag, scale, snap, reset, resize-safe positions);
  zoom, toggle sprint/sneak, fullbright, custom crosshair, low fire, clear water, hit color; Styles: 5 built-in capes +
  PNG import, hat and wings (3 styles each, tint, flap), texture pack import (zip/folder, validation, wrapped packs) and
  switching, presets. Works with Sodium 0.8.14.
- **Tests**: 57 JUnit tests (`gradlew build`), 16 automated in-game checks with screenshots
  (`gradlew runClientGameTest`, with and without Sodium), plus a hands-on pass driving the dev client.
- **Launcher** (`launcher\dist\HollowClient\HollowClient.exe`): installs 1.21.11 + Mojang Java + Fabric 0.19.5 + Fabric
  API + Sodium + the Hollow jar into `%APPDATA%\.hollowclient` (real install done and repeated); update check and
  update; repair; settings; live logs; Microsoft login (PKCE, local redirect, Credential Manager, refresh, log out) with
  the exact "not approved yet" message; packaged with PyInstaller; 65 pytest tests.

### What to test yourself
- Go through TESTING.md in game (especially the parts automation can't judge: how the menu feels, the look of the
  cosmetics on your own skin, your real texture packs, Ping on a real server, and that other players don't see styles).
- Once Mojang approves the app ID: put it in `config.json` (USER_GUIDE section 3), press PLAY, sign in, and check that
  the game starts and the Logs tab fills. Then try Log out and signing in again.

### Still open / known limits
- **Launching from Hollow's own launcher couldn't be tested end to end**: it needs a real Microsoft sign-in with an
  approved app ID. Everything up to that point is tested (install, the command line it builds, the login chain with
  mocked servers, the local redirect with a fake browser). Until approval, use the official launcher (USER_GUIDE section 6).
- The "close launcher on start" option discards the game's output (by design); turn it off to see logs.
- The menu key doesn't work inside other screens (inventory, chat), the same as every other keybind.
- Hit Color changes the hurt tint for every entity (that's how Minecraft draws it), not only mobs you hit.

---

# Expansion: visuals & features (add-on plan, started 2026-10-14)

- [x] **E1. Identity core**: theme engine (accent/gradient/glow/blur/roundness/animation speed, 6 presets + custom), shared theme file for the launcher, H sigil logo, OFL UI font (Inter) with vanilla toggle, original UI sounds + volume, Performance Mode, FPS-aware Auto Quality, Iris detection
- [x] **E2. Menu remake**: world blur, spring open/close, staggered cards, glass panels, animated gradient borders, hover glow, card tilt, click ripples, live animated search, reactive particle field; toast notifications
- [x] **E3. Custom screens**: splash/loading screen, title screen (nebula shader, parallax, glowing logo, animated buttons, clock + FPS), pause menu, inventory blur, chat restyle (rounded, translucent, slide-in)
- [x] **E4. HUD upgrades**: glass/gradient text/glow/rounded themes, rolling numbers, fade/slide-in, animated hotbar, keystroke glow + ripple
- [x] **E5. Post-processing**: bloom, motion blur, chromatic aberration, vignette, film grain, color grading presets, damage screen shake; Iris auto-off
- [x] **E6. Sky & world look**: sky gradient/colors, star density, shooting stars, aurora, client time and weather changer, block outline customizer, prettier rain/snow
- [x] **E7. Particles & animations**: hit particle multiplier/color, crit sparks, block-break particles, own trails, footsteps, item physics, smoother switching, swing speed, 1.7-style animations, crit flash, kill burst, own nametag style
- [x] **E8. Styles upgrades**: animated capes (frames/GIF, glow, scrolling gradient), halo, horns, aura, pet, emotes + radial wheel (B), wardrobe with drag-to-spin, presets save theme/trails/crosshair/HUD layout
- [x] **E9. Features A**: screenshot manager, chat upgrades (timestamps, stacking, copy, search, keyword highlight), Auto GG + auto-text hotkeys, session stats
- [x] **E10. Features B**: music player + visualizer, singleplayer waypoints, scoreboard/boss bar/title customizer, Discord Rich Presence, profiles, singleplayer cinematic camera
- [x] **E11. Launcher in QML**: frameless window + own title bar + shadow, nebula shader background, glass panels, page transitions, intro, charging PLAY, 3D skin viewer, animated install progress, stats dashboard, theme sync
- [x] **E12. Finish**: all effects vs Performance Mode FPS measurements, TESTING/USER_GUIDE/CREDITS, build_all, push

## Expansion notes / blockers

- E1: theme editor first overflowed at small GUI sizes (854x480, GUI scale 2); reworked to adaptive row heights and a scrolling theme list. Inter's license file had an uppercase name inside `assets/` (invalid resource path, logged an error); moved to `licenses/`. The config debounce unit test was flaky under load; it now waits up to 15 s.
- E12: the first FPS benchmark showed everything after the sixth effect at exactly 30 FPS: Minecraft's own AFK frame limit, not Hollow; the benchmark now sets the inactivity limit to "minimized". While writing the guide I found that Screenshots, Waypoints, Music Player and Camera Paths had no way to reach their settings (right-click opens their own screen), so each screen got a Settings button. Versions bumped to 1.1.0 (mod and launcher); build_all now clears old jars so only the new one is bundled. Final: JUnit 98, gametest 60/60 with and without Sodium, launcher pytest 80, build_all green with the packaged self-test.
- E11: the background nebula first drew nothing while the glass panels worked: a relative shader path in a QML component resolves against the file that *creates* it, so only the instance made in Main.qml looked in the wrong folder (fixed with Qt.resolvedUrl). Icons first collapsed to dots (a Shape sizes itself from its path, which looped with a size taken from width). Found while reviewing: the launcher only compared the Hollow jar's version number, so a rebuilt jar with the same number was never installed; it now compares file hashes, shows "Update available" and refreshes the jar before PLAY. Launcher tests 80 (new: backend, theme file, stats, skin viewer, QML loads without errors); the packaged exe renders the same pages as the source run.
- E10: first screenshots showed waypoint beams as solid columns that swallowed the screen up close and labels piled on top of each other; beams are now softer and fade out as you walk up, and labels stack. Camera paths switched to centripetal Catmull-Rom after a unit test showed the plain version doubling back with unevenly spaced points. Music HUD moved to the top right (it covered chat). One gametest run died in the old E7 item-physics step with Fabric's "network synchronizer" assertion; the identical rerun passed, so it is logged as a harness flake. Unit tests 98, gametest 59/59 (new: waypoints rename/save/death point, camera flight starts and ends, music scan/tags/playback/visualizer/pause/next, Discord waits for an app ID, profiles apply and undo).
- E9: the test harness sends key presses without modifier flags, so Ctrl+F also checks the physical Ctrl key (works the same for real players). Copying a chat line drops Hollow's own timestamp and "(x3)"; the capture toast shows the file name instead of a hint that got cut off. Gametest 43/43 (new: stacking, timestamps, highlight, right-click copy, Ctrl+F search, Auto GG once per game end, chat macro, block and kill counters, F2 capture + toast).
- E8: found a real Windows race (a reader holding the file made the atomic save fail silently) through the flaky config test; added retrying `AtomicFiles`. PNG strips need 3+ frames so the "square image is refused" check still holds. Gametest 32/32 (new: preset restores theme + halo, emote + camera, GIF cape import).
- E7: found and fixed a crash in rolling numbers (switching HUD Style off and on left stale arrays; the HUD editor has no per-element try/catch). Crit sparks first spawned inside mobs' heads (models stick out of hitboxes), so they moved forward. Gametest 28/28 (new: hit burst, crit sparks, footprints).
- E6: works as planned; the aurora test first faced the wrong way (the test's teleport overrode the client rotation), fixed by rotating through the teleport. Gametest 25/25 (new: client time shows 18:30 while the server stays at noon; client weather shows rain).
- E5: bloom was barely visible at first (threshold too high for Minecraft's low-range colors, and too small a blur); now ¼-size with two blur rounds and a lower threshold, measured +10 brightness on glowstone and +6 spill past its edge. Gametest 23/23 (new: post shaders compile and run).
- E4: done as planned; screenshots show glass/outline/classic looks, gradient text and the hotbar mid-glide. Gametest 22/22.
- E3: title-screen column ran into the footer on short windows (button height now adapts); Language/Accessibility became icon buttons with tooltips; nebula clouds brightened; chat panel corners/edge made more visible. Gametest 22/22 (new: Hollow title screen replaces vanilla's).
- E2: particles were too faint at first (made bigger/brighter); the search category label overlapped descriptions (now a tag before the switch). Gametest 21/21 (new: notifications show, menu close is animated).
- E1 gametest: 19/19 checks pass (new: theme card opens the editor, theme saved to the shared file, Performance Mode turns menu blur off).

## Expansion final summary (2026-10-15)

Everything in the expansion plan is done, built, tested and pushed. Versions: mod `hollowclient-1.1.0.jar`, launcher 1.1.0.

### What's new (verified)
- **Identity**: theme engine with 6 presets and your own themes, shared live with the launcher through `theme.json`; the H sigil; Inter UI font; original UI sounds; Performance Mode, Auto Quality, Iris detection.
- **Menu and screens**: spring-animated glass menu (staggered cards, tilt, hover glow, ripples, particles, live search), notifications, Hollow title/loading/pause screens, inventory blur, restyled chat.
- **HUD**: HUD Style looks, rolling numbers, fade-in, animated hotbar, keystroke glow; Session Stats and Music HUD elements.
- **Visuals and effects**: post-processing (bloom, motion blur, chromatic aberration, vignette, film grain, grades, damage shake) that turns itself off under Iris shaders; sky colors, stars, aurora, shooting stars; client time and weather; block outline; hit/crit/kill effects, trails, footprints, item physics, hand animations, own nametag.
- **Styles**: animated capes (Void Flow, GIF, strips), cape glow and shimmer, halo, horns, aura, pet, emotes on a radial wheel (B), wardrobe preview, presets that save the whole look.
- **Features**: screenshot manager, Chat+ (timestamps, stacking, copy, search, highlights), Auto GG, chat macros, singleplayer waypoints with beams and death points, music player with a visualizer, singleplayer camera paths, scoreboard/boss bar/title customizer, Discord Rich Presence (private by default), profiles with undo.
- **Launcher**: rebuilt in Qt Quick on the same backend: frameless window with its own title bar and shadow, nebula shader that follows the mouse, frosted glass panels, springy pages, intro, charging PLAY, 3D skin viewer with your cosmetics, animated install progress, play-time stats dashboard, theme sync, sounds; updates the Hollow jar by content.
- **Fair play**: every feature is visual or quality of life. Nothing sees through walls, no radar or entity markers, no freelook (waypoints and camera paths are singleplayer only and off while open to LAN), no aim or click help, nothing changes the packets sent to servers.

### Tests
- `gradlew build`: 98 JUnit tests. `gradlew runClientGameTest`: 60 in-game checks with screenshots, passing with and without Sodium, plus the FPS benchmark (TESTING.md has the table: every effect alone costs at most +0.12 ms per frame on an RTX 3080 at 720p except the HUD at +0.58 ms; everything at once +1.26 ms).
- Launcher: 80 pytest tests; `--screenshots` reviewed for every page, the intro and the PLAY burst; packaged exe self-test and screenshots.

### Sign-in and launch (2026-10-15)
- Verified end to end with a real Microsoft account on the packaged launcher: PLAY opened the Microsoft sign-in page, the sign-in went through Xbox Live and the Minecraft services, the launcher put the new Hollow jar in place (1.0.0 -> 1.1.0 by content), and the game started signed in with Hollow Client 1.1.0, Fabric API and Sodium, no errors in the log, and quit cleanly. Reopening the launcher signed in again silently from Windows Credential Manager.
- Discord Presence checked with a real Discord application: Discord accepted the ID and the profile showed "Playing Hollow Client".
