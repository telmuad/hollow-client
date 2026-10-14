# Hollow Client — progress

This file is the source of truth for where the build is. A new session should read it first and pick up at the first unticked item.

## Milestones

- [x] **0. Setup**: repo cloned, layout (`mod/`, `launcher/`, `scripts/`), `.gitignore`, doc skeletons
- [x] **1. Mod skeleton**: Gradle project (Loom 1.18.4, MC 1.21.11, Loader 0.19.5, Fabric API 0.141.6), `fabric.mod.json`, entrypoint, JUnit wired into `gradlew build`
- [x] **2. Core systems**: config (`config/hollowclient.json`), module + settings system, registry, keybinds (Controls menu) + tests
- [x] **3. Mod menu**: Right Shift menu, categories, module cards, settings screen (toggle, slider, enum, color picker, keybind picker)
- [ ] **4. HUD**: FPS, coordinates, CPS, keystrokes, armor, potions, ping, clock, toggle indicator; HUD editor (drag, scale, snap, reset, resize-safe positions) + tests
- [ ] **5. QoL / visual**: zoom, toggle sprint/sneak, fullbright, custom crosshair, low fire, clear water, hit color
- [ ] **6. Styles**: texture generator, capes (built-in + PNG import), hat + wings, texture pack import/switch, presets + tests
- [ ] **7. Mod polish**: `-PwithSodium` dev run, TESTING.md, mixin review, in-game checks
- [ ] **8. Launcher core**: paths, settings, config, Modrinth, installer + pytest; real install into `%APPDATA%\.hollowclient`
- [ ] **9. Launcher auth**: Microsoft OAuth (PKCE) with local redirect server, keyring token store, refresh, log out, "not approved yet" message + pytest
- [ ] **10. Launcher UI**: sidebar pages, PLAY, progress, account badge, logs, settings, icon
- [ ] **11. Packaging**: `launcher/build.bat`, PyInstaller spec, bundled mod jar, frozen `--selftest`
- [ ] **12. Tie together**: `scripts/build_all.bat`, README, USER_GUIDE, final summary, push

## Notes / blockers

(none yet)
