# Hollow Client — progress

This file is the source of truth for where the build is. A new session should read it first and pick up at the first unticked item.

## Milestones

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
- [ ] **E10. Features B**: music player + visualizer, singleplayer waypoints, scoreboard/boss bar/title customizer, Discord Rich Presence, profiles, singleplayer cinematic camera
- [ ] **E11. Launcher in QML**: frameless window + own title bar + shadow, nebula shader background, glass panels, page transitions, intro, charging PLAY, 3D skin viewer, animated install progress, stats dashboard, theme sync
- [ ] **E12. Finish**: all effects vs Performance Mode FPS measurements, TESTING/USER_GUIDE/CREDITS, build_all, push

## Expansion notes / blockers

- E1: theme editor first overflowed at small GUI sizes (854x480, GUI scale 2); reworked to adaptive row heights and a scrolling theme list. Inter's license file had an uppercase name inside `assets/` (invalid resource path, logged an error); moved to `licenses/`. The config debounce unit test was flaky under load; it now waits up to 15 s.
- E9: the test harness sends key presses without modifier flags, so Ctrl+F also checks the physical Ctrl key (works the same for real players). Copying a chat line drops Hollow's own timestamp and "(x3)"; the capture toast shows the file name instead of a hint that got cut off. Gametest 43/43 (new: stacking, timestamps, highlight, right-click copy, Ctrl+F search, Auto GG once per game end, chat macro, block and kill counters, F2 capture + toast).
- E8: found a real Windows race (a reader holding the file made the atomic save fail silently) through the flaky config test; added retrying `AtomicFiles`. PNG strips need 3+ frames so the "square image is refused" check still holds. Gametest 32/32 (new: preset restores theme + halo, emote + camera, GIF cape import).
- E7: found and fixed a crash in rolling numbers (switching HUD Style off and on left stale arrays; the HUD editor has no per-element try/catch). Crit sparks first spawned inside mobs' heads (models stick out of hitboxes), so they moved forward. Gametest 28/28 (new: hit burst, crit sparks, footprints).
- E6: works as planned; the aurora test first faced the wrong way (the test's teleport overrode the client rotation), fixed by rotating through the teleport. Gametest 25/25 (new: client time shows 18:30 while the server stays at noon; client weather shows rain).
- E5: bloom was barely visible at first (threshold too high for Minecraft's low-range colors, and too small a blur); now ¼-size with two blur rounds and a lower threshold, measured +10 brightness on glowstone and +6 spill past its edge. Gametest 23/23 (new: post shaders compile and run).
- E4: done as planned; screenshots show glass/outline/classic looks, gradient text and the hotbar mid-glide. Gametest 22/22.
- E3: title-screen column ran into the footer on short windows (button height now adapts); Language/Accessibility became icon buttons with tooltips; nebula clouds brightened; chat panel corners/edge made more visible. Gametest 22/22 (new: Hollow title screen replaces vanilla's).
- E2: particles were too faint at first (made bigger/brighter); the search category label overlapped descriptions (now a tag before the switch). Gametest 21/21 (new: notifications show, menu close is animated).
- E1 gametest: 19/19 checks pass (new: theme card opens the editor, theme saved to the shared file, Performance Mode turns menu blur off).
