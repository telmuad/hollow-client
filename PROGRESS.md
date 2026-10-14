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
- [ ] **12. Tie together**: `scripts/build_all.bat`, README, USER_GUIDE, final summary, push

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
