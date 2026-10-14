# Decisions

Choices made while building Hollow Client, with the reason for each. Newest sections are added at the bottom.

## Tooling and versions (checked 2026-10-13, not guessed)

| Item | Choice | Why |
|---|---|---|
| Minecraft | 1.21.11 | requested |
| Fabric Loader | 0.19.5 | latest stable on meta.fabricmc.net |
| Fabric API | 0.141.6+1.21.11 | newest 1.21.11 build on maven.fabricmc.net and Modrinth |
| Loom | `net.fabricmc.fabric-loom-remap` 1.18.4 | newest stable 1.18.x; the official 1.21.11 template uses the remap plugin |
| Mappings | Mojang official (`loom.officialMojangMappings()`) | what the official Fabric 1.21.11 template uses (fabricmc.net/develop) |
| Gradle | 9.7.1 wrapper | same as the official template |
| Java | compiled with `options.release = 21` | Minecraft 1.21.11 needs Java 21. The PC has Temurin 25 installed; `--release 21` guarantees the output runs on Java 21, so no separate JDK 21 install is needed. |
| Python | 3.14 (any 3.12+ works) | installed on the PC; every dependency ships 3.14 wheels |
| minecraft-launcher-lib | 8.0 | latest |
| PySide6 | `PySide6-Essentials` 6.11.2 | Essentials is the smaller package (Core/Gui/Widgets/Network), which keeps the exe smaller. 6.12.0 came out 5 days before this build, so the previous release is pinned. |
| PyInstaller | 6.22.3 | latest, supports 3.14 |
| keyring / requests / pytest | 25.7.0 / 2.34.2 / 9.1.1 | latest |

## Mod build layout

- **No split source sets.** Facet used `splitEnvironmentSourceSets()`. Hollow is client-only (`"environment": "client"`), so everything lives in `src/main`. Plain `src/test` JUnit tests can then see every class without extra Gradle wiring.
- **JUnit 6.0.3** runs as part of `gradlew build` (the `check` task). Tests cover pure logic only (config, HUD layout math, click counting, zoom math, pack/cape validation, colors), so they don't need a running game.
- **Sodium is opt-in for dev runs** (`gradlew runClient -PwithSodium`) through Modrinth's maven. It is never bundled in the jar.

## Core systems

- **Config file** is `config/hollowclient.json` (as asked). Files Hollow keeps besides that (imported capes) go in `config/hollowclient/`.
- **Settings and modules are plain Java** (`setting/`, `module/`, `config/`, `hud/HudLayout`): no Minecraft classes, so config handling and layout math are unit tested.
- **Loading never fails**: unknown keys are ignored, wrong types fall back to the default, numbers are clamped and snapped to their step, and a file that isn't valid JSON is renamed to `hollowclient.json.corrupt-<time>` before defaults load. Enabled flags are restored silently at startup (no enable/disable callbacks before the game is up).
- **Saves** are debounced (500 ms) and atomic (temp file + move), plus a final save on shutdown. This is the pattern proven in Facet.
- **Every module gets a keybind** in Options → Controls → "Hollow Client" (unbound by default except Zoom = C and the menu = Right Shift). Pressing a module's key toggles it and shows "Name: ON/OFF" in the action bar.

## Menu

- **Own drawing, no sprite sheets.** Rounded panels, cards, switches and sliders are drawn from fills (the approach proven in Facet), so the look is crisp at every GUI scale and needs no textures.
- **Look**: near-black translucent panel, violet accent (#8B6CFF), vanilla item icons on the category rail. The background is dimmed, not blurred, so you can see your HUD while changing settings. The menu doesn't pause singleplayer.
- **Cards**: click a card to toggle the module; the gear (or right-click) opens its settings. The search field (or `/`, Ctrl+F) searches every category.
- **Styles category** shows four big tiles (Capes, Cosmetics, Texture Packs, Presets) instead of module cards, because those are pickers rather than on/off features.
- **Settings screen**: the keybind row comes first (click, then press a key or mouse button; Backspace unbinds; Esc cancels), then one row per setting. "Reset to defaults" asks for a second click.
- **Color picker**: saturation/value square, hue bar, alpha bar (when the color supports it), hex field (type 6 or 8 hex digits) and 12 swatches.

## HUD

- **Drawn through Fabric's `HudElementRegistry`**, so there is no HUD mixin. Elements are hidden with F1, while F3 is open, and while the HUD editor is open (the editor draws them itself, with sample data where there is none, e.g. Ping in singleplayer).
- **Common settings on every element**: text color, background on/off, background color, text shadow; scale is set in the HUD editor (0.5x–3x).
- **Positions** store an anchor per axis (start/center/end third of the screen) plus an offset as a fraction of the screen. Right- or bottom-flush elements stay flush when the window or GUI scale changes. Text lines up on the right for elements on the right side.
- **Defaults on**: FPS, Coordinates, Armor & Held Item, Potion Effects (plus the sprint/sneak indicator later). Others start off.
- **Ping** is the latency the server reports in the player list (the same number as the Tab list); it is hidden in singleplayer.
- **CPS** counts real mouse presses in a 1-second window (only while no screen is open). It only reads input.
- **Potion Effects** can hide vanilla's top-right effect icons (off by default), via `replaceElement(STATUS_EFFECTS)`.

## Quality of life and visual modules

- **Zoom** (hold C): divides the world FOV (not the hand), smooth by default (eased in log space so in and out feel the same), scroll while holding changes the zoom (1.15x per notch, 1.5x–50x) instead of the hotbar slot, mouse turning slows down with the zoom, optional cinematic camera. C is also vanilla's "Save Toolbar Activator" (creative only); both still work because Minecraft lets two bindings share a key, but Controls shows it in red. Rebind either if that bothers you.
- **Toggle Sprint / Sneak**: one module with "toggle sprint" (on), "toggle sneak" (off) and "always sprint". It ORs the toggled state into the input record vanilla already builds from your keys, exactly what vanilla's own "Sprint: Toggle" option does. Toggled sneak is not applied while flying or riding (you would sink or dismount). Keep vanilla's Sprint/Sneak options on "Hold" to avoid double toggling. A "Sprint/Sneak Status" HUD element shows the state.
- **Fullbright** raises the brightness value the lightmap shader reads (strength 1x–15x, default 10x). The saved Brightness option is never changed, so turning it off is instant and clean.
- **Custom crosshair** replaces the vanilla crosshair element through Fabric's HUD registry (no mixin): cross, dot, circle, X, box, T. Vanilla's attack indicator is redrawn under it. In spectator mode and with the F3 3D crosshair, vanilla's crosshair is used.
- **Low Fire** moves the first-person fire overlay down (0–100%, default 40%). **Clear Water** cancels the underwater screen overlay and pushes the underwater fog end out (default 96 blocks).
- **Hit Color** rewrites the 8 "damage" rows of vanilla's 16x16 overlay texture and re-uploads it. That tint is used for every entity that gets hurt (including you in F5), not only for mobs you hit. Off = vanilla red restored.
- **Mixins** use MixinExtras (`@ModifyReturnValue`, `@WrapOperation`, `@ModifyExpressionValue`) and plain `@Inject`; none use `@Overwrite` or `@Redirect`, and none touch the classes Sodium replaces (chunk rendering).
- **Automated in-game test**: `gradlew runClientGameTest` (Fabric's client game-test API) runs the real client, creates a world, checks the menu key, CPS, zoom and toggle sprint, and screenshots every screen and visual module into `mod/build/run/clientGameTest/screenshots`.
