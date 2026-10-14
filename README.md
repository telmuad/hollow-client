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
├── launcher/   the Hollow Client launcher (Python 3.12+, PySide6), packaged as HollowClient.exe
├── scripts/    build_all.bat (builds everything), run_launcher_dev.bat
├── USER_GUIDE.md   how to build, set up login, play, and use the menu and styles (start here)
├── TESTING.md      automated checks and an in-game checklist
├── DECISIONS.md    why things are built the way they are
└── PROGRESS.md     build log and current status
```

### The mod in more detail

- **Menu** (Right Shift): categories for HUD, Visual, Quality of Life and Styles; a card per feature with an on/off switch and a settings screen (toggles, sliders, color picker, keybind picker). Every feature also has a key in Options → Controls.
- **HUD**: FPS, coordinates (with facing and biome), CPS, keystrokes, armor and held-item durability, potion effects, ping (on servers), a clock and a sprint/sneak indicator, each with text color, background and scale. A **HUD editor** lets you drag, scale and snap elements; positions are stored relative to the screen.
- **Quality of life / visual**: zoom (hold C, scroll to adjust, smooth zoom), toggle sprint / sneak, fullbright, custom crosshair, low fire, clear water, hit color.
- **Styles** (only you see them): built-in capes or your own PNG cape, a hat and wings on your player, texture pack import and switching, and presets that save the whole look.
- Works alongside Sodium. Settings are saved to `config/hollowclient.json`.

### The launcher in more detail

- Uses its own game folder, `%APPDATA%\.hollowclient`, so it never touches the official launcher's files.
- Installs Minecraft 1.21.11 (and Mojang's Java runtime) from Mojang, Fabric Loader with the official Fabric installer, and Fabric API and Sodium from Modrinth, then adds the Hollow Client mod. It checks for mod updates on startup.
- Microsoft login uses the official OAuth flow (authorization code with PKCE) in your browser. The sign-in is kept in Windows Credential Manager. There is no offline mode.
- The Azure app ID goes in `launcher/config.json`, which is not committed (copy `config.example.json`). Until Mojang approves the app ID, the launcher says so plainly and still installs and updates everything; you can play through the official launcher in the meantime (see USER_GUIDE.md).

## Building

You need Windows 10/11, Git, a Java 21+ JDK and Python 3.12+ (details in USER_GUIDE.md). Then run:

```
scripts\build_all.bat
```

It builds and tests the mod, bundles it into the launcher, tests and packages the launcher, and checks the packaged app. Results:

- `mod\build\libs\hollowclient-1.0.0.jar`
- `launcher\dist\HollowClient\HollowClient.exe`

Other useful commands (from `mod\`): `gradlew.bat runClient` (dev game), `gradlew.bat runClientGameTest` (automated in-game checks with screenshots). See TESTING.md.
