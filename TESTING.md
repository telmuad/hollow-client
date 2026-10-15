# Hollow Client — testing

## Automated checks (run these first)

From the `mod` folder:

| Command | What it checks |
|---|---|
| `gradlew.bat build` | Compiles the mod and runs 98 unit tests: config loading and saving (including the Windows "file in use" retry), HUD layout math, click counting, zoom math, colors, themes, springs, PNG/GIF/strip cape rules, resource-pack import rules, the camera path curve, waypoint files, MP3/Ogg tags and lengths, MP3/Ogg decoding (440 Hz and 880 Hz test tones), the FFT, and the Discord frames and privacy rules. Produces `build\libs\hollowclient-1.1.0.jar`. |
| `gradlew.bat runClientGameTest` | Starts a real game window by itself, makes a test world and runs 60 in-game checks with a screenshot of every screen and feature (`build\run\clientGameTest\screenshots`): menus, theme editor and shared theme file, Performance Mode, title/loading/pause screens, HUD and HUD editor, zoom, toggle sprint, fullbright, fire, water, post-processing shaders, sky and client time/weather, block outline, hit bursts, crits, footprints, cosmetics, emotes, animated capes, presets, Chat+, Auto GG, macros, session stats, screenshots, waypoints, camera paths, the music player and visualizer, the scoreboard customizer, Discord Presence and profiles. It ends with the FPS benchmark below and writes `hollow_fps_results.md` in the run folder. Don't touch the window while it runs (about four minutes). |
| `gradlew.bat runClientGameTest -PwithSodium` | The same run with Sodium 0.8.14 loaded. All 60 checks pass with Sodium too. |

From the `launcher` folder: `.venv\Scripts\python -m pytest` runs 80 tests (installer, Modrinth, sign-in error handling, token storage, launch command, settings, the QML backend, the shared theme file, play-time stats, the skin viewer, and that every QML page loads without errors). `.venv\Scripts\python -m hollow_launcher --screenshots <folder>` saves a picture of every launcher page, the intro and the PLAY burst for a visual check.

## What each effect costs (measured)

Measured by the gametest's benchmark: each effect switched on alone at its default strength, in the same night scene on the superflat test world, 1280x720, vsync off, no FPS limit, FPS averaged over about 5 seconds. "Frame time vs baseline" is the extra time per frame; differences under about 0.03 ms are measurement noise (that's why a few are slightly negative).

Test PC: NVIDIA GeForce RTX 3080, AMD Ryzen 5 5600X.

**Without Sodium**

| Effect (alone, default strength) | FPS | Frame time vs baseline |
|---|---|---|
| Baseline: every Hollow effect off | 940 | |
| Bloom | 893 | +0.06 ms |
| Motion blur | 920 | +0.02 ms |
| Chromatic aberration | 962 | -0.02 ms |
| Vignette | 964 | -0.03 ms |
| Film grain | 944 | -0.00 ms |
| Color grade (Cinematic) | 960 | -0.02 ms |
| Sky: more stars, aurora, shooting stars | 948 | -0.01 ms |
| Waypoint beams and labels (3 waypoints) | 895 | +0.05 ms |
| Block outline with fill and pulse | 981 | -0.04 ms |
| HUD Style + 6 HUD elements | 610 | +0.58 ms |
| Third person, no cosmetics | 956 | -0.02 ms |
| Third person with cape, wings, halo, aura, pet | 821 | +0.15 ms |
| **Everything above at once** | 430 | +1.26 ms |
| **Everything + Performance Mode** | 452 | +1.15 ms |

**With Sodium 0.8.14**

| Effect (alone, default strength) | FPS | Frame time vs baseline |
|---|---|---|
| Baseline: every Hollow effect off | 992 | |
| Bloom | 927 | +0.07 ms |
| Motion blur | 993 | -0.00 ms |
| Chromatic aberration | 987 | +0.01 ms |
| Vignette | 987 | +0.01 ms |
| Film grain | 995 | -0.00 ms |
| Color grade (Cinematic) | 1006 | -0.01 ms |
| Sky: more stars, aurora, shooting stars | 1001 | -0.01 ms |
| Waypoint beams and labels (3 waypoints) | 953 | +0.04 ms |
| Block outline with fill and pulse | 1017 | -0.02 ms |
| HUD Style + 6 HUD elements | 638 | +0.56 ms |
| Third person, no cosmetics | 977 | +0.02 ms |
| Third person with cape, wings, halo, aura, pet | 848 | +0.17 ms |
| **Everything above at once** | 442 | +1.25 ms |
| **Everything + Performance Mode** | 481 | +1.07 ms |

What this means:

- On this PC every effect costs a fraction of a millisecond. At 144 FPS a frame takes 6.9 ms, so even everything at once (+1.25 ms) leaves plenty of room.
- The biggest single cost is the HUD (+0.58 ms for six elements with HUD Style), which is CPU-side drawing, not the GPU. HUD elements you don't need are the first thing to switch off on a slow PC.
- Bloom, motion blur and film grain work on every pixel, so on a weaker graphics card or at 1440p/4K they cost more than here. Those are exactly what **Performance Mode** switches off (it saved about 0.1-0.2 ms here; on a laptop GPU the saving is larger). **Auto Quality** lowers effect quality by itself when FPS drops below your target.
- Hollow doesn't change chunk rendering, so Sodium's speed-up is kept (about 5% more FPS here even in a tiny superflat world).

## In-game checklist

Start the dev client with `gradlew.bat runClient` (or use the real launcher / the official launcher with the jar in `mods`). Make a Creative superflat world on Peaceful.

### Menu
- [ ] **Right Shift** opens the Hollow menu in game, on the title screen and on the pause (Esc) menu. Right Shift or Esc closes it.
- [ ] The four categories on the left (HUD, Visual, Quality of Life, Styles) switch the page. Hovering shows the name if the window is narrow.
- [ ] Clicking a card turns the module on and off (the switch slides, "x of y on" updates).
- [ ] Right-clicking a card, or clicking its slider icon, opens its settings.
- [ ] Typing in the search box (or pressing `/`) filters across all categories.
- [ ] Settings screen: toggles, sliders (drag), choice buttons (`<` / `>`), color picker (square, hue bar, alpha bar, hex box, swatches) and the keybind button ("press a key", Backspace clears, Esc cancels) all work. "Reset to defaults" needs two clicks.
- [ ] A key set in the settings screen also shows in Options → Controls → Key Binds → "Hollow Client", and the other way round.
- [ ] Quit and restart: everything you changed is still the same (saved in `config\hollowclient.json`).

### HUD
- [ ] Defaults: FPS (top left), Coordinates with facing and biome, Armor & Held Item (bottom right, only when you wear or hold something), Potion Effects (right, only with an effect: try `/effect give @s speed 60 1`).
- [ ] Turn on CPS (top center), Keystrokes (left), Ping (only on a server), Clock (top center), Sprint/Sneak Status.
- [ ] Each element's text color, background on/off, background color and text shadow work.
- [ ] Keystrokes light up for W A S D, both mouse buttons and Space, with CPS under the mouse buttons.
- [ ] Armor durability turns from green to red as items wear down. "Durability as" switches number/percent/both/icons only.
- [ ] Potion Effects: "Hide vanilla effect icons" hides Minecraft's top-right icons.
- [ ] F1 hides everything; F3 hides Hollow's HUD while the debug screen is open.

### HUD editor (menu → Edit HUD, or "Move on screen" in a HUD module's settings)
- [ ] Drag elements anywhere. Pink guide lines show when they snap to an edge, the center or another element. Hold Alt to place freely.
- [ ] Scroll over an element (or drag its corner square) to scale it from 50% to 300%.
- [ ] Arrow keys nudge the selected element (Shift = 10 pixels). R or right-click resets one element; "Reset all" (two clicks) resets all.
- [ ] "Show disabled" lets you place elements that are off.
- [ ] Resize the window or change GUI scale: elements stay in the same corner/edge and stay on screen.

### Quality of life
- [ ] **Zoom**: hold C, scroll to zoom further in or out (the hotbar slot doesn't change), let go to return. Try "Smooth zoom" off and "Cinematic camera" on.
- [ ] **Toggle Sprint / Sneak**: turn it on, tap Ctrl (sprint) once and walk forward, and you keep sprinting; tap again to stop. With "Toggle sneak" on, tap Shift once to keep sneaking. The status element shows "[Sprinting (Toggled)]". (Leave Minecraft's own Sprint/Sneak options on "Hold".)

### Visual
- [ ] **Fullbright**: at night in a cave, everything is bright. Your Brightness option is unchanged when you turn it off.
- [ ] **Custom Crosshair**: every shape, color, size, thickness, gap, outline and center dot; the attack indicator still shows under it (Options → Attack Indicator: Crosshair).
- [ ] **Low Fire**: `/effect give @s fire_resistance 60`, stand in fire; the flames on screen sit lower.
- [ ] **Clear Water**: swim; no blue overlay and you see further.
- [ ] **Hit Color**: hit a mob; the flash uses your color.

### Styles (menu → Styles)
- [ ] **Capes**: click a built-in cape, then press F5 and look at your back. "On elytra" uses the cape on an elytra too.
- [ ] Import a PNG cape with "Import PNG..." (a 64x32 cape image) or by dropping the PNG on the screen. A wrong size (e.g. 64x64) shows a friendly message. The small x (two clicks) deletes an imported cape.
- [ ] **Cosmetics**: pick each hat and each wing style, change the tint and the flap speed; check in F5 and in your inventory. Put on an elytra: the wings hide (option).
- [ ] Other players (open to LAN with a second account, or a server) never see your cape, hat or wings.
- [ ] **Texture Packs**: "Import .zip..." with a real resource pack, "Import folder..." with a pack folder, and drop a zip on the screen. Each appears in the list with its icon. "Use" switches to it (short reload), "Use none" switches back. A zip without `pack.mcmeta`, a renamed text file, and a zip with the pack inside an extra folder each behave as described in DECISIONS.md. "Remove" asks twice and deletes only Hollow's copy in `resourcepacks`.
- [ ] **Presets**: type a name, "Save current", change your cape/hat/wings/pack, "Apply" brings them all back. "Rename" uses the text in the box. "Delete" asks twice.

### Expansion features

**Look and feel**
- [ ] Menu → Client → **Theme**: each preset changes the menu, HUD and chat colors right away. Make and save your own theme; it appears in the launcher's Settings → Theme too, and picking a theme in the launcher changes the game within a second.
- [ ] The menu opens with a spring, cards slide in one after another, hovering tilts and glows a card, clicks ripple, and particles drift behind it. Menu Effects can switch each of these off.
- [ ] Title screen (nebula, glowing logo), loading screen, pause menu and inventory blur all use the theme.
- [ ] **Performance Mode** (Client → Performance) turns off the menu blur, post-processing and other heavy effects at once; with an Iris shader pack active, post-processing switches itself off.

**HUD and effects**
- [ ] HUD Style: glass / flat / outline / text only, gradient text, glow, rolling numbers, the fade-in, the hotbar glide, keystroke glow.
- [ ] Post-processing: each effect alone and together; every color grade. Bloom makes torches and the sky glow.
- [ ] Sky at night: more stars, aurora to the north, shooting stars. Time & Weather shows your chosen time/weather while a server's stays the same.
- [ ] Hit a mob: hit burst, crit sparks on a jump-crit, a kill burst. Trails and footprints in third person. Item physics on dropped items. Hand animations (switching, swing speed, 1.7 style).

**Styles**
- [ ] Animated capes (Void Flow, an imported GIF, a frame strip), cape glow and shimmer.
- [ ] Halo, horns, aura, pet; each style's colors; the Wardrobe preview turns when dragged.
- [ ] Hold **B**: the emote wheel; each emote plays in third person and walking away stops it.
- [ ] Presets save and restore the theme, cosmetics, trail, crosshair and HUD layout.

**Quality of life**
- [ ] Chat+: timestamps, "(x3)" stacking, right-click copy (without Hollow's timestamp), Ctrl+F search, a highlight with a sound for your name.
- [ ] Auto GG says gg once per game end (try "won the game" in chat in singleplayer), never again within the cooldown.
- [ ] Chat macros: set a text and a key; "/" texts run as commands.
- [ ] F2: shutter sound and a notification. Screenshots screen: thumbnails, the viewer with arrow keys, Copy image (paste into Paint), Delete (goes to `screenshots\deleted`), Settings button.
- [ ] Session Stats counts time, kills, deaths, blocks and distance, and resets in another world.
- [ ] Waypoints (singleplayer): add, rename, recolor, hide, delete; beams and labels; a death point after dying. Open the world to LAN or join a server: no waypoints show.
- [ ] Music Player: drop an MP3 and an Ogg on its screen; play, pause, next, previous, seek, shuffle, repeat, volume; the Music HUD shows the song and the visualizer moves with it; the game's music stays quiet.
- [ ] Camera Paths (singleplayer): add three points, Play; the camera flies smoothly and comes back; pressing W stops it. On a server the screen says it's singleplayer only.
- [ ] Scoreboard & Titles on a server with a scoreboard: glass look, left side, size, numbers hidden; boss bars and titles resized or hidden.
- [ ] Discord Presence with your own application ID: Discord shows "Playing Hollow Client"; the server address only appears after switching it on.
- [ ] Profiles: PvP, Building and Cinematic each change things as described; "Back to my settings" brings everything back; save and apply your own profile.

**Launcher**
- [ ] The intro plays (click skips it); the window moves by its title bar, snaps to screen edges, maximizes on double-click and resizes from the edges.
- [ ] The nebula follows the mouse; glass panels show it softly; page changes spring; buttons ripple and click.
- [ ] PLAY charges and bursts, then installs / signs in / launches as before; the install steps and the bar move during an install.
- [ ] Wardrobe shows your skin with your cape, wings and halo, and turns when dragged.
- [ ] Stats fills in after you've played; the chart's bars show the exact time on hover.
- [ ] Settings: memory, Java options, window size, theme, animated background, sounds, intro.
- [ ] Rebuild the mod and copy the new launcher: the Mods card shows Hollow Client as "Update available" and PLAY puts the new jar in place.

### With Sodium
- [ ] Put Sodium in `mods` (the launcher does this). Everything above still works, especially fullbright, clear water, the crosshair and the cosmetics.

### Fair play
- [ ] Join a server: nothing in Hollow sends anything to the server. Every feature is visual, or reads your own input locally (CPS, keystrokes, toggle sprint behaves exactly like vanilla's "Toggle" option).
