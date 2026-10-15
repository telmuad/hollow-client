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

## Styles

- **Capes**: 5 built-in capes (Hollow, Nightfall, Ember, Glacier, Verdant), all generated by `mod/tools/texturegen/HollowTextureGen.java`. Imported capes must be PNGs of 64x32 (or any 2:1 size that is a multiple of 64, up to 2048x1024) or the old 22x17 layout (padded onto a 64x32 canvas). They are copied to `config/hollowclient/capes/`. The cape replaces your skin's cape for your own player only (other players are untouched) and, if "On elytra" is on, your elytra texture too. Hollow turns the cape layer on for your player even if the skin's Cape part is switched off in Skin Customization.
- **Cosmetics**: hat (top hat, wizard hat, beanie) and wings (shadow, angel, ember), built in code as model parts and drawn by a render layer only on the local player's render state. Both can be tinted (white = original colors). Wings flap gently, faster while falling or gliding, and hide while you wear an elytra (option). They show in F5 and in your inventory preview, like vanilla armor.
- **Each style is a module** (`cape`, `hat`, `wings`) so it has an enabled flag, settings and a keybind like everything else. Clicking a cape or hat style in the Styles screen also switches that piece on.
- **Texture packs** go into the game's normal `resourcepacks` folder (copied, never moved). A plain zip is copied as is. A zip or folder that wraps the real pack in one extra folder is unwrapped (extracted with size limits and a guard against paths escaping the folder). Missing or broken `pack.mcmeta`, several packs in one zip, a `pack.mcmeta` buried deeper, or a file that isn't a zip each give a plain-English error. Hollow remembers the packs it imported; "Use" swaps only Hollow's packs and keeps everything else you selected. Switching reloads resources like the vanilla pack screen. "Remove" asks twice, then deletes only Hollow's copy (never the original you picked, never a symlink, only a direct child of `resourcepacks`).
- **Presets** store the full state of the three style modules plus the pack name. Applying a preset whose pack was removed applies the rest and says which pack was skipped.
- **Native file picker** (tinyfd, bundled with Minecraft) for "Import PNG/.zip/folder", run off the render thread. Drag-and-drop onto the Styles screen works too.
- **Styles UI**: four tabs (Capes, Cosmetics, Texture Packs, Presets) with a live, slowly turning model of your player on the right when you're in a world and the window is wide enough.

## Launcher core

- **Own game folder** `%APPDATA%\.hollowclient`, never `.minecraft`, so the official launcher's files are untouched.
- **Install order**: vanilla 1.21.11 through minecraft-launcher-lib (official Mojang servers; this also installs Mojang's Java 21 runtime, `java-runtime-delta`) → newest *stable* Fabric Loader that is at least 0.19.5 (the version the mod is built with), installed by the official Fabric installer run with Mojang's Java → Fabric API and Sodium from the Modrinth API → the Hollow jar.
- **Repair** simply runs the whole install again: minecraft-launcher-lib re-checks every file's hash and only downloads what is missing or broken; mods are only downloaded when the version or the sha512 differs.
- **Modrinth choice**: builds for game version 1.21.11 and the fabric loader; releases first, then betas, then alphas; newest by publish date within that. On 2026-10-13 this picks Sodium 0.8.14 (release) over 0.8.15-beta.1. Required dependencies are followed. Every download is checked against Modrinth's sha512 before it replaces anything. A small manifest (`mods\.hollow-mods.json`) records what Hollow installed, so updates replace the old file and never touch other mods you add yourself.
- **Mod jar lookup**: the copy bundled with the exe first, then `mod\build\libs` (newest `hollowclient-<version>.jar`, never `-sources`).
- **Settings** (`launcher_settings.json`): RAM 4 GB by default (slider from 1 GB to your RAM minus 2 GB, 512 MB steps), extra JVM arguments, window size (or Minecraft's default), close launcher on start. A broken file is renamed `.corrupt-<time>` and defaults are used.
- **Headless modes** for testing and power users: `--install-only`, `--check-updates`, `--selftest`, `--screenshots DIR`.

## Microsoft login

- **Official flow only**: OAuth 2.0 authorization code with PKCE (S256) and a random state, against `login.microsoftonline.com/consumers`, then Xbox Live → XSTS → `api.minecraftservices.com`. It uses minecraft-launcher-lib's step functions (`get_secure_login_data`, `get_authorization_token`, `refresh_authorization_token`, `authenticate_with_xbl/xsts/minecraft`, `get_profile`), chained by Hollow so each failure can be explained. There is **no offline mode** anywhere.
- **Redirect**: a tiny local web server on `127.0.0.1` (and `::1`) on a free port; the redirect URI is `http://localhost:<port>`. Microsoft accepts any port on loopback for "Mobile and desktop" redirect URIs, so registering plain `http://localhost` in Azure is enough. A fixed port can be set in `config.json` (`"redirect_uri": "http://localhost:25585"`). The browser tab shows a small "You're signed in" page. The wait times out after 5 minutes and can be cancelled.
- **Not approved yet**: when `login_with_xbox` returns no Minecraft token (HTTP 403 "Invalid app registration"), the launcher shows exactly: "Your app ID hasn't been approved by Mojang yet. You can still install and update everything, and launch from the official launcher in the meantime." Install, Repair and Update keep working. Other cases (no Xbox profile, child account, account without Minecraft, wrong app ID or redirect URI in Azure, network down, cancelled) each get their own plain message.
- **Tokens**: only the Microsoft refresh token is stored, in Windows Credential Manager via `keyring` (service "HollowClient"), split into numbered chunks because one credential is limited to 2560 bytes. The Minecraft access token lives in memory only. The account name, UUID and skin URL (not secret) are kept in `account.json` so your name shows straight away. The launcher refreshes silently at startup and again right before launching. Log out deletes the stored token. Tokens are never written to logs; the launch command shown in Logs has the token replaced with `********`.

## Launcher window

- **Look**: dark, Lunar-style layout with a sidebar (Home, Styles & Mods, Settings, Logs), a painted hero area (original art: violet gradient, star dots, the Hollow ring), a big PLAY button, and the account badge in the top-right corner (your skin's face once signed in, fetched from Mojang's texture server). Windows 11 gets a dark title bar. The icon is drawn in code by `launcher/tools/make_icon.py` and matches the mod's icon.
- **PLAY** does whatever is missing, in order: install (if needed), sign in (silent refresh, or the browser sign-in), then start the game. While the game runs, its output streams into Logs and PLAY shows RUNNING. With "Close the launcher when the game starts", the output is discarded and the launcher quits a few seconds after the game started.
- **Updates**: Modrinth is checked in the background at startup; a banner on Home offers "Update now", and Styles & Mods shows installed vs newest for Fabric API, Sodium and Hollow Client.
- **Styles are in-game.** The launcher's Styles & Mods page explains that and opens the resourcepacks and imported-capes folders; it doesn't duplicate the in-game Styles screen.
- **Logging**: `%APPDATA%\.hollowclient\launcher.log` (rotating, 1 MB x 3) plus the Logs tab. Tokens never appear in either.

## Packaging

- **PyInstaller one-folder build** (`dist\HollowClient\HollowClient.exe` + `_internal`), windowed, with the generated icon. One-folder starts faster than one-file and doesn't unpack to a temp folder on every start.
- `launcher\build.bat` creates `.venv` on first run, installs the pinned packages, runs pytest, packages, copies `config.example.json` (and your `config.json`, if you made one) next to the exe, then runs the exe with `--selftest` (window builds, icon loads, mod jar is bundled, keyring backend works) and fails the build if that fails.
- `keyring`'s Windows backend is chosen explicitly and `win32ctypes` is bundled, because backend auto-discovery uses package metadata that can go missing in a frozen app. minecraft-launcher-lib's `version.txt` data file is bundled too.
- The mod jar comes from `launcher\bundled\` (filled by `scripts\build_all.bat`); it is not committed to git.

# Expansion: visuals & features

## E1 Identity core

- **Theme engine** (`theme/HollowTheme`, `ThemeStore`, `Themes`): a theme is accent + two-color gradient + glow, blur, corner roundness, animation speed and an optional rainbow cycle. Values are clamped on load so a hand-edited file can't break the UI. Six presets (Void, Inferno, Toxic, Frost, Bloodmoon, Rainbow) plus up to 30 custom themes. Saving under a preset's name adds " (custom)" so presets can't be overwritten.
- **Shared with the launcher** through `config/hollowclient/theme.json` inside the game folder (`%APPDATA%\.hollowclient\config\hollowclient\theme.json` for a launcher install). The mod checks the file's timestamp and size once a second and reloads when the launcher changed it. Saves are atomic (temp file + move). While a slider is dragged the change stays in memory and is written when you let go, so dragging doesn't hammer the disk.
- **Theme editor** opens from the new **Client** category (Theme card), or from a keybind. The Client category also holds Performance, UI Font and UI Sounds. Theme and Performance are "not toggleable" modules: their cards show "Open >" instead of a switch.
- **Blur strength comes from the theme.** Hollow screens call `GuiGraphics.blurBeforeThisStratum()` (the 1.21.11 menu blur) and a small `Options` mixin returns the theme's blur (0–10) as the menu blur radius *only while a Hollow screen is open*. The saved Accessibility option is never changed. When no world is loaded, the vanilla panorama is drawn behind the blur.
- **Logo: the H sigil.** A pointy-top hexagon ring with a sharp, slanted H and two orbit dots. It's original, distinct from Lunar/Badlion/Feather marks, and the same geometry is drawn by the Java texture generator (white masks tinted at runtime, so the logo follows the theme) and by `logo.py` in the launcher.
- **Font: Inter** (SIL OFL 1.1), Medium for text and SemiBold for titles, loaded as TTF font providers with vanilla's default and unifont as fallbacks for missing glyphs. `UI Font` module turns it off (vanilla font everywhere); a sub-setting keeps HUD elements on the vanilla font if you prefer pixel text there. All Hollow text goes through `gui/Ui`, so the switch is one place. The license sits in `licenses/` at the jar root because resource-pack paths must be lowercase.
- **UI sounds** are synthesized by a Python script (sine tones + filtered noise → ffmpeg/Vorbis), so they are original and reproducible. They're client-only sound events (sounds.json, `SoundEvent.createVariableRangeEvent`), not registered, so servers never see them. Volume 0–100 and an option for hover sounds; hover sounds are rate-limited to one per 60 ms. With UI Sounds off, buttons fall back to the vanilla click.
- **Performance Mode** (one switch, keybind available) disables every heavy effect: `Quality.heavyAllowed()` is the single check every effect asks. **Auto Quality** samples FPS once a second (in a world, not paused): 3 s below the target FPS steps effects down a level (High → Medium → Low = 1 / 0.65 / 0.35 strength), 10 s at target + 15 FPS steps back up. The gap between the two thresholds stops it from flickering.
- **Iris**: detected with `FabricLoader.isModLoaded("iris")`; whether a shader pack is active is asked through Iris' public API (`IrisApi.getInstance().isShaderPackInUse()`) by reflection, cached for 500 ms. If that call fails, Hollow assumes a pack is on, so it errs on the side of not stacking post-processing on shaders.

## E2 Menu remake + notifications

- **Menu Effects module** (Client category): each effect has its own switch and strength slider — void particles, entrance animations, card tilt, hover glow, click ripples, animated borders. The module switch turns them all off. Effects that move are also off with "Reduce UI motion"; heavy ones (particles) are off in Performance Mode, and Auto Quality thins the particle count.
- **Springs, not fixed easing**: `gui/Spring` is a damped spring stepped in fixed 1/240 s slices, so the open "pop", the sliding category marker and notification cards move the same at any frame rate (unit tested). Durations scale with the theme's animation speed.
- **Open/close**: screens spring open from 90 % scale with a fade and a soft whoosh, and fade/shrink away on close before handing back to the game (the hand-off runs on the next frame through `minecraft.execute`, with a tick-based fallback if frames stop). Moving between Hollow screens is instant, with no whoosh.
- **Glass panels**: shadow + theme glow + translucent body over the blurred world + a top sheen + a highlight line + a border that flows through the theme gradient. CPU-drawn from fills and two small runtime textures (soft glow, soft dot/disc), so no custom shaders are needed for the UI and nothing can conflict with Sodium/Iris.
- **"3D" card tilt**: since 1.21.6 the GUI transform is a 2D affine matrix (`Matrix3x2fStack`), so true perspective isn't possible without a custom pipeline. Hollow fakes it with a small shear toward the mouse plus a slight scale and a moving shine spot; at these angles it reads as a tilt. Strength slider 0–100 %.
- **Live search**: cards keep their animation state by module, so while you type, matches glide to their new places, new ones fade in, and the matching letters are highlighted. Results show a category tag.
- **Notifications** (`gui/Toasts`, Notifications module): Hollow's own glass cards with a draining gradient line, drawn right after vanilla's toasts via a `ToastManager.render` TAIL mixin, so they appear above menus and in game. Position (any corner), duration, opacity and sound are settings. Off = vanilla's plain `SystemToast`. Used for theme saved, cape/pack imported or failed, preset applied (or applied with a warning).

## E3 Custom screens

- **New "Interface" category** for modules that restyle vanilla screens: Title Screen, Loading Screen, Pause Menu, Inventory Blur, Chat Style. Each is a normal module (off = vanilla) with its own effect switches and strength sliders.
- **Title screen**: when the vanilla `TitleScreen` is about to be shown, a `Minecraft.setScreen` mixin hands over `HollowTitleScreen` instead. It *extends* `TitleScreen`, so anything that waits for "the title screen" (Fabric's test harness, other mods) still finds it. Only the exact vanilla class is swapped, never the demo's. Buttons open the same vanilla screens (worlds, multiplayer with the safety screen, Realms, options, language, accessibility, credits) and use vanilla's translations. The version line still says "(Modded)" and the copyright link stays.
- **Nebula shader**: an original GLSL fragment shader (domain-warped noise clouds in the theme gradient, two parallax star layers, mouse light, vignette, dither) drawn as one GUI quad with a custom `RenderPipeline`. Rather than adding uniforms, its inputs ride on a normal blit: the texture coordinate carries time + parallax, the vertex color carries mouse position, quality and brightness, the bound "texture" is a 4-pixel palette of theme colors, and the screen size comes from vanilla's `Globals` block. Noise octaves follow Auto Quality. If the shader fails to compile, or in Performance Mode, the vanilla panorama is drawn instead. Hollow menus opened from the title screen use the same background.
- **Loading screen**: only the drawing is replaced (background color, logo, progress bar) through three small injections in `LoadingOverlay`; vanilla still runs the reload, the fades and closing the overlay. Because the first load happens before any resources exist, the sigil and wordmark are read straight from the jar into dynamic textures (`JarTexture`, which also builds half-size copies with smooth filtering so big art stays clean when small). No text is drawn there, since fonts aren't loaded yet.
- **Wordmark**: "HOLLOW" / "CLIENT" are rendered from the bundled Inter font by the texture generator into white masks, then tinted with the theme gradient in slices at draw time. That keeps large titles crisp instead of scaling the 9 px UI font.
- **Pause menu**: vanilla's `PauseScreen` keeps all its buttons and logic (so server links, feedback dialogs and "Disconnect" stay correct); Hollow replaces its background (theme blur, particles, glass panel sized to the widgets) and, through an `AbstractButton.renderDefaultSprite` hook active only on the pause screen, draws the buttons in Hollow's style. Labels are still vanilla's.
- **Inventory blur**: container screens normally only dim the world; a `Screen.renderTransparentBackground` hook adds the menu blur for them, with its own strength. Every Hollow blur goes through `ScreenStyles.tryBlur`, which ignores the "only one blur per frame" error if something else already blurred.
- **Chat**: vanilla paints a black bar per line in a first pass, then text in a second. Hollow wraps the first pass, collects the visible lines, and paints one rounded, theme-tinted panel with a gradient edge instead. Text and click handling are untouched. New lines slide in and fade in, by wrapping the per-line text call in vanilla's inner line class (`ChatComponent$1`). Timing uses the line's own GUI-tick timestamp, so nothing is stored per message. The typing bar becomes a glass bar. Two package-private chat interfaces are opened with an access widener.

## E4 HUD upgrades

- **HUD Style module** (HUD category) decides how every element looks, so a new HUD element gets the look for free: panel style (Glass, Flat, Outline, Text only; corner radius from the theme), gradient text, glow, gradient border, rolling numbers, fade/slide-in and the animated hotbar, each with a switch and strength. Off = the classic flat boxes from the base build. Each element's own text/background colors still apply.
- All HUD text now goes through the HUD font (Inter or vanilla per the UI Font module's "HUD elements too" option), including width measurement, so layouts stay right in either font.
- **Rolling numbers**: per text line, Hollow remembers the last string; when only digits changed (same length and same non-digit characters), each changed digit rolls up (old one out the top, new one in from below, clipped to the line). If the text's shape changes, it simply redraws. Used by every text element and by potion timers.
- **Fade/slide-in**: elements fade in and slide in from their screen edge when they appear (world join, switching one on, leaving F1 or F3), staggered one after another. Item icons can't be faded by the GUI renderer, so they only slide.
- **Animated hotbar**: two small wraps in `Gui.renderItemHotbar`. The selection frame is drawn at an animated x (it glides) with a theme glow behind it, and the selected item is drawn a touch larger, with a short pop when you switch. Vanilla still draws the hotbar, so resource packs' hotbar art keeps working.
- **Keystrokes**: pressed keys glow in the theme accent and a ripple spreads across the key on each press (own switches and strengths); keys get rounded corners when HUD Style is on.

## E5 Post-processing

- **One Post Processing module** (Visual): bloom (+ threshold), motion blur, chromatic aberration, vignette, film grain, color grading (Cinematic, Vibrant, Noir, Dreamy, with a strength slider) and damage screen shake. Every effect has a switch and a strength. Defaults are modest: bloom and vignette on, the rest off.
- **Own passes instead of vanilla post-chain JSON.** Vanilla's post chains bake uniform values in at load time and only size targets in absolute pixels. Hollow draws its passes directly: full-screen triangles with vanilla's `screenquad` vertex shader and Hollow's fragment shaders (`shaders/post/*.fsh`, shared uniform block in `shaders/include/hollow_post.glsl`), its own render targets, and small uniform buffers written every frame. That gives live sliders and a downscaled bloom.
- **Order of work**: copy the frame → bright-pass into a ¼-size texture (⅕, ⅙ when Auto Quality steps down) → two rounds of horizontal + vertical gaussian blur (5 bilinear taps each) → one composite pass back into the frame that adds bloom and applies chromatic aberration, grading, vignette, grain and motion blur. It runs right after the world and vanilla's own effects (spectator shaders) and before the HUD, menus and Hollow's UI, so text and menus stay sharp and ungraded.
- **Motion blur** blends with the previous finished frame, kept in a persistent texture. History is dropped on resize or when the effect turns on, so there's no flash from stale pixels.
- **Bloom threshold**: Minecraft's colors are low-range (no HDR), so the threshold maps to 0.15–0.9 of brightness with a soft knee. The default (55 %) catches glowstone, torches and the sun without washing out daylight.
- **Off automatically** in Performance Mode and whenever Iris reports an active shader pack. If the shaders fail to compile or a pass throws, the effects switch off for the session with one log line; the game keeps running.
- **Damage shake** is a camera effect, added after vanilla's damage tilt (`bobHurt`). It's a small, quickly fading rotation scaled by the hurt timer. It's off with "Reduce UI motion" and doesn't touch what is sent to servers.

## E6 Sky and world look

- **Sky module**: sky colors (top tint plus horizon/fog tint, from the theme or two custom colors), more stars (up to 4x), brighter stars, shooting stars and an aurora. Tints keep vanilla's brightness (night stays dark, dusk stays dusky) by scaling the target color to the vanilla color's luminance before mixing.
- **Stars**: vanilla builds 1500 stars once per resource reload. Hollow multiplies that count, and rebuilds the star buffer right away when the setting changes, so there's no reload.
- **Aurora and shooting stars** are drawn in the sky pass right after the stars, as additive, see-through quads that don't write depth (one small custom pipeline using vanilla's `position_color` shaders), so terrain draws over them like the rest of the sky. The aurora is two wavy curtains fixed toward the north (they don't turn with the stars), brightest at the bottom edge, in theme colors with a touch of green. It fades in with vanilla's star brightness and fades out with rain. Shooting stars appear at random at a rate set by the strength slider.
- **Time & Weather** (client only): the client's own copy of the world reports the chosen time (keeping the day count, so the moon phase still follows the server) and weather (clear, rain or thunder). That moves the sun, sky, light and rain on your screen. The server's world is a separate object and is never touched, and nothing is sent anywhere. Also prettier rain (softer, faintly tinted) and snow (brighter, cooler). The module is on by default, but its defaults change nothing except the prettier rain.
- **Block Outline**: color (flowing theme gradient, custom with alpha, or rainbow), thickness (multiplies vanilla's width), see-through fill and pulse. The fill is drawn before vanilla takes its line buffer, so the two batches don't interfere. The high-contrast outline (accessibility option) is never changed.

## E7 Particles and animations

- **New "Effects" category** (Hit Effects, Particles, Item Physics, Hand Animations, Own Nametag). The menu's category rail now shrinks its row height on short windows so all seven categories fit.
- **Own particle class, no registry changes.** `HollowParticle` (soft dot, four-point sparkle, flat footprint; tinted, optionally full-bright, shrinking and fading) is created directly and handed to the particle engine, so no particle type is registered and nothing exists outside your client. Its sprites live in `textures/particle/`, which vanilla's particle atlas picks up from every namespace. Trails can also use vanilla particles (flames, hearts, notes, clouds), spawned client-side only.
- **Only reacting, never acting.** Hit Effects observe things the client already gets: your own attack (a hook at the start of `MultiPlayerGameMode.attack` that doesn't change the attack), the server's crit animation packets, entity death events, and block-break effects. A crit only counts as yours if you attacked that entity in the last second; kill bursts need a hit from you in the last 5 seconds. Effects appear in front of the target at 70 % of its height, because models such as heads and snouts stick out of their hitboxes.
- **Hand animations are drawing-only**: eased switching (smoothstep on the interpolated hand height), swing speed (changes how long *your own* arm-swing animation lasts on your screen, never the attack cooldown), 1.7 options (no item dip while the cooldown recharges, the cooldown itself unchanged; classic held-item position; instant sneak camera). "Swing while using an item" was left out on purpose: a client-side swing is sent to the server as a packet, which the rules forbid.
- **Item physics**: two small wraps around vanilla's float and spin in `ItemEntityRenderer.submit`. Sprites lie flat and blocks sit still at a fixed random angle on the ground, and items tumble in the air. Whether an item is on the ground is carried on the render state with a Fabric `RenderStateDataKey`.
- **Own nametag**: vanilla never shows your own name; in third person Hollow lets it show (to you only) and recolors it in the theme gradient with an optional star and bold.
- Footprints are placed every ~0.65 blocks, alternating feet, as dark see-through prints that fade over a few seconds.

## E8 Styles upgrades

- **New cosmetics** (Styles → Wardrobe): halo (ring, double, spiked; theme or custom color; glow with a gentle pulse, spin), horns (curved, devil, ram), aura (void motes, sparkles, orbit; particles around you, seen in third person) and a pet (a glowing wisp with orbiting motes, or a little cube with a face) floating above your shoulder. Models are built in code from small boxes like the hats; textures come from the texture generator. Everything is drawn only on your own player.
- **Emotes** (wave, cheer, clap, dance, facepalm, salute, dab, spin): hold **B** for a radial wheel, point, let go; a quick tap keeps the wheel open for clicking. An emote only re-poses *your* model on *your* screen. It travels on the render state and is applied after vanilla's animation in `PlayerModel.setupAnim`, with a short blend in and out. Nothing is sent to the server. By default the camera switches to third person (front) for the emote and back after, and walking away stops it.
- **Animated capes**: a built-in "Void Flow" cape drawn from the theme colors about 20 times a second; imported animated **GIFs** (frames composited with their offsets and "restore to background" handling) and **PNG strips** (frames of 2:1 stacked top to bottom, at least 3 so a square skin isn't mistaken for a strip). Each animated cape is one dynamic texture whose pixels are swapped when the frame changes, so the vanilla cape model and the elytra just work.
- **Cape glow and theme shimmer**: glow raises the cape's light level; shimmer draws the same cape model a second time, see-through and full-bright, with a moving theme-colored sheen. Both happen by wrapping the vanilla cape layer's single draw call, and only when the render state carries your cape effects.
- **Wardrobe preview**: drag to turn your player (capes start from the back, everything else from the front); after a few idle seconds it keeps turning slowly. Emotes play in the preview too.
- **Presets now save the whole look**: cape, hat, wings, halo, horns, aura, pet, theme, trail, crosshair, HUD Style, every HUD element with its position, and the pack. Applying a preset only changes what it contains, so presets saved before still work.
- **Safer file saves**: on Windows, swapping the new config/theme file in fails with "access denied" if another program has it open at that instant (for example the launcher reading `theme.json`). Saves now retry for a moment (`AtomicFiles`, unit tested with a busy reader). This also fixed an occasionally failing config unit test.

## E9 Features A

- **Chat+** (on by default): timestamps (24 h or 12 h, seconds optional), repeated lines stacked into one with "(x3)" (same text within a minute), right-click a line to copy it (Hollow's own timestamp and "(xN)" are left out), Ctrl+F opens a search over the last 1000 lines (click a result to copy), and keyword highlights: your words plus your own name (not in your own messages) get an accent underline, the accent marker on the left and a soft sound. It works by wrapping the one `ChatComponent.addMessage` method every chat line goes through; if Hollow throws, the original line is shown unchanged.
- **Auto GG** (off by default): when a chat line contains an end-of-game phrase (editable list), it sends your message once after a short delay, then waits out a cooldown (15 s by default) so several "game over" lines only give one gg. It never reacts to its own message. It sends an ordinary chat message, the same as typing it, so it isn't a modified packet; servers that dislike auto messages can simply be avoided by leaving it off.
- **Chat macros** (auto-text hotkeys): five slots, each with an unbound key in Controls. A "/" text runs as a command, anything else is sent as chat. At most one macro per second, and only when no screen is open. An empty slot shows a notification instead of sending nothing silently.
- **Session Stats** HUD (off by default): time since you joined the world, kills (mobs or players that died within 5 s of your hit, the same rule the kill burst uses), deaths, blocks you broke (counted when the client's own break succeeds) and distance walked (riding, flying and teleports don't count). Resets when you change worlds.
- **Screenshot manager** (QOL card or its key): a grid of your screenshots, newest first, with thumbnails scaled down on two background threads and uploaded on the render thread; a viewer with previous/next (buttons, arrow keys, scroll wheel), open, open folder and **copy image** (on Windows through PowerShell's `Clipboard.SetImage`, since Java's AWT clipboard can't be used inside the game; elsewhere it copies the path). **Delete** asks for a second click and moves the file to `screenshots/deleted`, so a misclick never loses a picture. Taking a screenshot plays a shutter sound and shows a notification with the file name (both can be switched off).
- **Text settings**: settings screens now have text fields for text settings (keywords, Auto GG phrases, macro texts).
