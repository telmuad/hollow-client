> **Frozen legacy branch (Minecraft 1.21.11).** This branch keeps Hollow Client 1.1.0 for Minecraft 1.21.11 working and gets no new features. Development continues on `main` (Minecraft 26.2, SkyBlock Mode). The launcher on `main` still offers this version as the "Hollow 1.21.11 — Legacy" profile.

# Hollow Client

A personal, non-commercial launcher and client for Minecraft: Java Edition.

## Hollow Launcher

- Signs in with a Microsoft account using the official Microsoft OAuth flow and verifies game ownership
- Downloads Minecraft and the Fabric mod loader from official sources
- Installs and updates the Hollow client mod plus performance mods (Sodium)
- Lets you set options like allocated RAM, then launches the game

## Hollow Client mod

A client-side Fabric mod for Minecraft 1.21.11 that adds:

- Client-side cosmetics ("styles")
- HUD elements: FPS, coordinates, CPS, keystrokes, armor durability
- Quality-of-life features: zoom, toggle sprint, fullbright
- A mod menu to toggle features and move HUD elements

All features are cosmetic or quality-of-life. Hollow Client does not include any features that give an unfair advantage.

## Notes

- Hollow Client does not bypass authentication, license checks, or any safety features.
- It does not redistribute Minecraft game files. Everything is downloaded from official sources at install time.
- This is a personal hobby project for my own use and is not publicly distributed.

Not affiliated with or endorsed by Mojang or Microsoft.

## What's in this repository

```
hollow-client/
├── mod/        the Hollow Client Fabric mod (Gradle project, Java 21, Minecraft 1.21.11)
├── launcher/   the Hollow Client launcher (Python 3.12+, PySide6 / Qt Quick), packaged as HollowClient.exe
├── scripts/    build_all.bat (builds everything), run_launcher_dev.bat
├── USER_GUIDE.md   how to build, set up login, play, and use every feature (start here)
├── TESTING.md      automated checks, measured FPS cost of each effect, and an in-game checklist
├── CREDITS.md      licensed fonts and libraries, and how the original art and sounds were made
├── DECISIONS.md    why things are built the way they are
└── PROGRESS.md     build log and current status
```

### The mod in more detail

- **Menu** (Right Shift): animated glass menu with categories for HUD, Interface, Visual, Effects, Quality of Life, Styles and Client; a card per feature with an on/off switch and a settings screen (toggles, sliders, color picker, text fields, keybind picker). Every visual effect has its own switch and strength slider.
- **Identity**: a theme engine (accent, gradient, glow, blur, roundness, animation speed; six presets plus your own) shared with the launcher, the H sigil, the Inter UI font and original UI sounds.
- **Screens**: Hollow's own title, loading and pause screens, inventory blur, restyled chat and notifications.
- **HUD**: FPS, coordinates, CPS, keystrokes, armor, potion effects, ping, clock, sprint/sneak status, session stats and now-playing music, with HUD Style looks (glass, gradient text, glow, rolling numbers) and a drag-and-snap HUD editor.
- **Visual and effects**: post-processing (bloom, motion blur, chromatic aberration, vignette, film grain, color grades), sky (colors, stars, aurora, shooting stars), client-side time and weather, block outline, crosshair, fullbright, low fire, clear water, hit color; hit and kill bursts, crit sparks, trails, footprints, item physics, hand animations.
- **Quality of life**: zoom (hold C), toggle sprint/sneak, Chat+ (timestamps, stacking, copy, search, highlights), Auto GG, chat macros, a screenshot manager, singleplayer waypoints, a music player with a visualizer, singleplayer camera paths, scoreboard/boss bar/title customizer, Discord Rich Presence, and profiles (PvP, Building, Cinematic, your own).
- **Styles** (only you see them): built-in, imported and animated capes; hat, wings, halo, horns, aura and a pet; emotes on a radial wheel (hold B); texture pack import and switching; presets that save the whole look.
- **Performance Mode** turns every heavy effect off at once, Auto Quality lowers effects when FPS drops, and post-processing switches itself off when an Iris shader pack is active. Works alongside Sodium.

### The launcher in more detail

- A frameless Qt Quick window: an animated nebula background (a shader that follows the mouse), frosted glass panels, springy page transitions, an intro, a PLAY button that charges and bursts, a 3D skin viewer with your cosmetics, animated install progress and a play-time dashboard. Its theme is the game's theme.
- Uses its own game folder, `%APPDATA%\.hollowclient`, so it never touches the official launcher's files.
- Installs Minecraft 1.21.11 (and Mojang's Java runtime) from Mojang, Fabric Loader with the official Fabric installer, and Fabric API and Sodium from Modrinth, then adds the Hollow Client mod. It checks for mod updates on startup and updates the Hollow mod whenever the launcher carries a newer build.
- Microsoft login uses the official OAuth flow (authorization code with PKCE) in your browser. The sign-in is kept in Windows Credential Manager. There is no offline mode.
- The Azure app ID goes in `launcher/config.json`, which is not committed (copy `config.example.json`). If an app ID isn't approved by Mojang yet, the launcher says so plainly and still installs and updates everything (see USER_GUIDE.md).

## Building

You need Windows 10/11, Git, a Java 21+ JDK and Python 3.12+ (details in USER_GUIDE.md). Then run:

```
scripts\build_all.bat
```

It builds and tests the mod, bundles it into the launcher, tests and packages the launcher, and checks the packaged app. Results:

- `mod\build\libs\hollowclient-1.1.0.jar`
- `launcher\dist\HollowClient\HollowClient.exe`

Other useful commands (from `mod\`): `gradlew.bat runClient` (dev game), `gradlew.bat runClientGameTest` (automated in-game checks with screenshots and the FPS benchmark). See TESTING.md.
