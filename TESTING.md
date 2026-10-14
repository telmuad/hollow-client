# Hollow Client — testing

## Automated checks (run these first)

From the `mod` folder:

| Command | What it checks |
|---|---|
| `gradlew.bat build` | Compiles the mod and runs the unit tests (config loading/saving, HUD layout math, click counting, zoom math, colors, PNG/cape sizes, resource-pack import rules). Produces `build\libs\hollowclient-1.0.0.jar`. |
| `gradlew.bat runClientGameTest` | Starts a real game window by itself, creates a test world and runs 16 in-game checks: the menu key (in game and on the title screen), CPS counting, zoom and zoom scrolling, toggle sprint, built-in cape, PNG cape import (and refusing a wrong size), pack import, pack switching, presets and pack removal. It saves a screenshot of every screen and feature to `build\run\clientGameTest\screenshots`. Don't touch the game window while it runs (about a minute). It fails if any check fails. |
| `gradlew.bat runClientGameTest -PwithSodium` | The same run with Sodium 0.8.14 loaded, to check the two work together. |

From the `launcher` folder: `.venv\Scripts\python -m pytest` (after `build.bat` has created the venv).

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

### With Sodium
- [ ] Put Sodium in `mods` (the launcher does this). Everything above still works, especially fullbright, clear water, the crosshair and the cosmetics.

### Fair play
- [ ] Join a server: nothing in Hollow sends anything to the server. Every feature is visual, or reads your own input locally (CPS, keystrokes, toggle sprint behaves exactly like vanilla's "Toggle" option).
