# Hollow Client — user guide

This guide covers building Hollow Client, setting up Microsoft sign-in, using the launcher, and everything in the game: the menu, the keys, the HUD, effects, styles, themes and the extra features.

---

## 1. What you need (one time)

| Program | Why | Where |
|---|---|---|
| **Git** | to download and update this project | git-scm.com |
| **Java JDK 21 or newer** | to build the mod (the game itself uses Mojang's own Java, which the launcher downloads) | adoptium.net (Temurin) |
| **Python 3.12 or newer** | to build the launcher | python.org (tick "Add python.exe to PATH" when installing) |

Your PC already has all three (Git 2.55, Temurin 25, Python 3.14).

---

## 2. Build everything

1. Open the `hollow-client` folder.
2. Double-click **`scripts\build_all.bat`** (or run it in a terminal to see everything).
3. Wait. The first time takes a while (it downloads Gradle, Minecraft's code for building, and the Python packages). Later builds take a few minutes.

When it says **All done**, you have:

- the mod: `mod\build\libs\hollowclient-1.1.0.jar`
- the launcher: `launcher\dist\HollowClient\HollowClient.exe`

You can move the whole `launcher\dist\HollowClient` folder anywhere you like (for example to your Desktop) and start `HollowClient.exe` from there. When you rebuild, copy the new folder over the old one: the launcher notices the new mod and puts it in place the next time you press PLAY.

---

## 3. Put your Azure app ID into config.json

The launcher needs your Azure app (client) ID to sign you in with Microsoft. The ID is not secret, but it is kept out of git.

1. In the `launcher` folder, copy **`config.example.json`** and name the copy **`config.json`**.
2. Open `config.json` in Notepad. It looks like this:

   ```json
   {
     "azure_client_id": "00000000-0000-0000-0000-000000000000",
     "redirect_uri": "http://localhost"
   }
   ```

3. Replace the zeros with your **Application (client) ID** from the Azure portal (App registrations → your app → Overview). Keep the quotes.
4. Save the file, then run `launcher\build.bat` (or `scripts\build_all.bat`) again. The build copies `config.json` next to `HollowClient.exe` for you. Or copy it there yourself: it must sit **in the same folder as `HollowClient.exe`**.

**How the Azure app must be set up** (check this if sign-in says the app ID is wrong):

- Supported account types: **personal Microsoft accounts** (either "Personal Microsoft accounts only" or the option that includes personal accounts).
- Authentication → Add a platform → **Mobile and desktop applications** → redirect URI **`http://localhost`**.
- Mojang must allow the app to use the Minecraft API. Yours was approved on 14 October 2026, so sign-in and PLAY work. (If you ever switch to a new app ID, it needs approval again: https://aka.ms/mce-reviewappid. Until then the launcher tells you so and section 6 explains how to play meanwhile.)

---

## 4. The launcher

Start **`HollowClient.exe`**. A short intro plays (click or press any key to skip; Settings can turn it off). The window has its own title bar: drag it to move the window, double-click it to maximize, and drag the edges to resize.

### Home
- **PLAY** does everything that is still missing, in order:
  1. The first time, it installs Minecraft 1.21.11, Mojang's Java, Fabric, Fabric API, Sodium and the Hollow Client mod into `%APPDATA%\.hollowclient` (about 700 MB). This is a separate folder, so your normal Minecraft is not touched. The steps and the progress show under the button.
  2. It signs you in. The first time, your web browser opens Microsoft's sign-in page. Sign in there, then come back to the launcher. After that it remembers you (safely, in Windows Credential Manager). **Cancel sign-in** stops waiting for the browser.
  3. It starts the game. Its output appears on the **Logs** page.
- A banner appears when there are updates (**Update now**) or when something needs your attention.
- **Repair** checks every game file and downloads anything missing or broken. **Game folder** opens `%APPDATA%\.hollowclient`.

### Account (bottom left)
Your face from your skin and your name. Click it to **sign in**, **log out** (removes the saved sign-in) or open the `config.json` folder.

### Wardrobe
- Your skin in 3D with the cape, wings and halo you switched on in the game. **Drag** to turn it and tilt it; it keeps turning slowly after a few seconds. (When you aren't signed in it shows a stand-in figure.)
- **Mods**: installed and newest versions of Fabric API, Sodium and Hollow Client, **Check** for updates and **Update all**.
- **Your files**: buttons that open the folders for mods, texture packs, capes, music and screenshots.

### Stats
Time played (in total, this week, longest session, how many days in a row), a chart of the last 14 days (hover a bar for the exact time), and counts from your game folder: screenshots, worlds, waypoints, your own capes, songs and how many Hollow modules are on. Play time is recorded by the launcher while the game runs (not when "close the launcher when the game starts" is on).

### Settings
- **Game**: memory for Minecraft (default 4 GB), extra Java options, window size, close the launcher when the game starts.
- **Theme**: the same themes as in the game. Picking one here changes the game too, and the other way round.
- **Launcher**: animated background, sounds, intro.
- **Microsoft sign-in**: whether `config.json` was found, and how to set it up.

### Logs
The launcher's messages and the game's live output, colored (errors red, warnings yellow). **Copy**, **Clear**, **Logs folder**, and **Follow new lines**.

---

## 5. In the game

### Keys

| Key | What it does |
|---|---|
| **Right Shift** | Open or close the Hollow menu (in game, on the title screen and on the pause menu) |
| **C** (hold) | Zoom. Scroll while holding to zoom further in or out |
| **B** (hold) | Emote wheel: point at an emote and let go. A quick tap keeps the wheel open for clicking |
| **F2** | Screenshot (Hollow adds a sound and a notification; see Screenshots below) |
| **Ctrl+F** in chat | Search the chat |
| **Right-click** a chat line | Copy it |

Every Hollow feature can also have its own key, and so can these actions (none is set by default): add a waypoint, add a camera point, play/stop the camera path, music play/pause, next and previous song, and five chat macros. Set them in **Options → Controls → Key Binds → Hollow Client**, or with the key button in a feature's settings.

### The menu

- On the left: **HUD**, **Interface**, **Visual**, **Effects**, **Quality of Life**, **Styles**, **Client**.
- **Click a card** to switch that feature on or off. **Right-click a card** (or its slider icon) for its settings: switches, sliders, color pickers, text boxes and a key button.
- Features with a screen of their own (Theme, Profiles, Screenshots, Waypoints, Music Player, Camera Paths) open that screen instead; their settings are behind its **Settings** button.
- Type in the **search box** (or press `/`) to find a feature anywhere.
- Every visual effect has its own switch and a strength slider.
- Settings save automatically (in `config\hollowclient.json` in the game folder).

### HUD and the HUD editor

HUD elements: FPS, coordinates, CPS, keystrokes, armor and held item, potion effects, ping, clock, sprint/sneak status, **Session Stats** (time, kills, deaths, blocks, distance this session) and **Music** (now playing, with a visualizer). **HUD Style** (HUD category) gives them all a look: glass, flat, outline or text only, gradient text, glow, rolling numbers and a fade-in.

Click **Edit HUD** in the menu (or **Move on screen** in an element's settings):

- **Drag** an element to move it; it snaps to edges, the middle and other elements. Hold **Alt** to place it freely.
- **Scroll** over an element, or drag its corner square, to resize it.
- **Right-click** an element (or press **R**) to put it back. **Reset all** (two clicks) puts everything back.
- **Done** saves and closes.

### Interface
Hollow's own title screen, loading screen, pause menu, a blurred background behind the inventory, the chat look, notifications, and **Scoreboard & Titles** (move, resize, restyle or hide the scoreboard, boss bars and big titles).

### Visual and Effects
- Visual: fullbright, custom crosshair, low fire, clear water, hit color, block outline, **Sky** (colors, more stars, shooting stars, aurora), **Time & Weather** (client-side time and weather, only on your screen), **Post-processing** (bloom, motion blur, chromatic aberration, vignette, film grain, color grades) and **Camera Paths** (below).
- Effects: hit effects (bursts, crit sparks, kill burst), particles (block-break sparkles, trails, footprints), item physics, hand animations (smooth switching, swing speed, 1.7 style), your own nametag in third person.

### Quality of life
- **Zoom** and **Toggle Sprint / Sneak** (tap sprint once to keep sprinting, tap again to stop).
- **Chat+**: timestamps, repeated messages stacked as "(x3)", right-click to copy, Ctrl+F to search, and highlights with a sound when your name or one of your words appears.
- **Auto GG** (off by default): says "gg" (or your own text) once when a game ends.
- **Chat Macros**: five texts you can send with a key. A text starting with `/` runs as a command.
- **Screenshots**: open the card to browse your screenshots, view them large (arrow keys for the next one), open them, show the folder, **Copy image** (paste it anywhere with Ctrl+V), or **Delete** (two clicks; it goes to `screenshots\deleted`, so nothing is lost by a misclick).
- **Waypoints** (your own singleplayer worlds only): **+ Add here** saves the spot you stand on. Each waypoint shows a light beam and a label with the distance. Click a name to rename it, the dot to change its color. Where you die, a death point is saved.
- **Music Player**: put MP3 or Ogg files in `config\hollowclient\music` (the **Open folder** button), or drop them on the music screen. Click a song to play; previous / play / next, shuffle, repeat, volume, click the bar to jump. The game's own music pauses while yours plays (you can change that).

### Camera Paths (singleplayer only)
Switch on **Camera Paths** (Visual). Stand where the shot should start and press **+ Add point here** in its screen (or use the "Add camera point" key), move and look somewhere else, add the next point, and so on. **Play** flies the camera smoothly through the points while your player stays put. Moving or opening any menu stops it. Flight time, looping and easing are in its settings.

### Styles (only you can see them)
Open the menu → **Styles**.

- **Capes**: built-in capes, the animated "Void Flow", or your own: **Import...** a PNG (64x32 is the normal size), an animated GIF, or a strip of frames stacked top to bottom; or drop the file on the screen. Cape glow and theme shimmer are in the Cape settings.
- **Wardrobe**: hat, wings, halo, horns, aura and a little pet, each with its own colors. Drag your player in the preview to turn it.
- **Emotes**: hold **B** for the wheel (wave, cheer, clap, dance, facepalm, salute, dab, spin).
- **Texture Packs**: import a .zip or a folder (or drop it on the screen), then **Use** it or **Use none**.
- **Presets**: save your whole look (cape, cosmetics, theme, trail, crosshair, HUD Style and HUD layout, pack) under a name and apply it with one click.

### Client
- **Theme**, **Profiles**, **Performance**, UI font, UI sounds, menu effects and notifications, and **Discord Presence** (below).
- **Profiles**: **PvP**, **Building** and **Cinematic** set up Hollow for that in one click (they only change what they're about). **Save current** stores your own setup under a name. **Back to my settings** undoes profiles.
- **Performance**: **Performance Mode** turns off every heavy effect at once. **Auto Quality** lowers effect quality when FPS drops below your target.

### Making your own theme
1. Menu → **Client** → **Theme**.
2. Start from a preset on the left, then change it on the right: **Accent**, **Gradient start** and **Gradient end** (click a color to pick it), **Glow**, **Background blur**, **Corner roundness**, **Animation speed**, and **Rainbow cycle** if you like.
3. Type a name in the box at the bottom and click **Save**. Your theme appears in the list and is used by the launcher too.

Themes live in `config\hollowclient\theme.json` in the game folder, so you can also edit that file in Notepad. `current` is the theme in use; `custom` holds your saved ones. Colors are written as `#AARRGGBB` (for example `#FF9B6CFF`); `glow` and `blur` go from 0 to 1, `roundness` from 0 to 8, `animationSpeed` from 0.25 to 3. A value that doesn't make sense falls back to the default.

### Discord Presence (optional)
Shows "Playing Hollow Client" on your Discord profile while Discord runs on this PC. It needs a Discord application ID of your own (free, two minutes):

1. Go to discord.com/developers/applications and sign in. Click **New Application** and name it **Hollow Client** (Discord shows this name).
2. Copy the **Application ID** from the General Information page.
3. In the game: menu → Client → **Discord Presence** settings → paste it into **Discord application ID**, then switch the module on.

By default only "Hollow Client" and whether you're in the menus, in singleplayer or in multiplayer are shown. The server address and the world name stay hidden unless you switch them on, and you can hide the activity and the time too.

### Fair play
Everything in Hollow is visual or quality of life. Nothing shows things through walls, there is no radar, no extra camera control on servers (waypoints and camera paths are singleplayer only), and nothing changes what your game sends to a server.

---

## 6. Playing through the official launcher (optional)

You can also play with Hollow Client through the official Minecraft Launcher, for example if you set up a new app ID that Mojang hasn't approved yet. In that case Hollow's launcher shows:

> Your app ID hasn't been approved by Mojang yet. You can still install and update everything, and launch from the official launcher in the meantime.

1. **Install Fabric** with the official Fabric installer from fabricmc.net/use/installer. Choose **Client**, Minecraft **1.21.11**, Loader **0.19.5** (or newer), and click Install. This adds a "fabric-loader-1.21.11" installation to the official launcher.
2. **Open your normal mods folder**: press Windows+R, type `%APPDATA%\.minecraft` and press Enter. Make a folder called **`mods`** if there isn't one.
3. **Copy these three files into it.** Hollow's launcher has already downloaded them into `%APPDATA%\.hollowclient\mods`, so you can copy them from there:
   - `hollowclient-1.1.0.jar`
   - `fabric-api-0.141.6+1.21.11.jar`
   - `sodium-fabric-0.8.14+mc1.21.11.jar`

   (You can also get Fabric API and Sodium from modrinth.com; pick the versions for 1.21.11 and Fabric.)
4. In the official Minecraft Launcher, pick the **fabric-loader-1.21.11** installation and press **Play**.

---

## 7. If something goes wrong

- **"Microsoft login isn't set up yet"**: `config.json` is missing, still has the zeros, or isn't next to `HollowClient.exe`. See section 3.
- **"Microsoft didn't accept the app ID"** or **"The redirect URI doesn't match"**: check the Azure settings in section 3.
- **"This Microsoft account doesn't have an Xbox profile yet"**: sign in once at minecraft.net or xbox.com, then try again.
- **The game doesn't start, or closes right away**: open **Logs** and look at the last lines, or press **Repair**.
- **The install stopped halfway** (for example the internet dropped): press PLAY or Repair again; it carries on where it stopped.
- **Low FPS**: switch on **Performance Mode** (menu → Client → Performance), or turn off Post-processing. TESTING.md lists what each effect costs.
- **The launcher feels slow on a laptop**: Settings → Launcher → turn off **Animated background**.
- **To start over completely**: log out in the launcher, then delete the folder `%APPDATA%\.hollowclient`.

---

## 8. Where things are

| What | Where |
|---|---|
| Hollow's game folder | `%APPDATA%\.hollowclient` |
| Mods | `%APPDATA%\.hollowclient\mods` |
| Hollow Client settings | `%APPDATA%\.hollowclient\config\hollowclient.json` |
| Theme (shared with the launcher) | `%APPDATA%\.hollowclient\config\hollowclient\theme.json` |
| Your capes | `%APPDATA%\.hollowclient\config\hollowclient\capes` |
| Your music | `%APPDATA%\.hollowclient\config\hollowclient\music` |
| Waypoints and camera paths (per world) | `%APPDATA%\.hollowclient\config\hollowclient\worlds` |
| Your profiles | `%APPDATA%\.hollowclient\config\hollowclient\profiles` |
| Screenshots (deleted ones in `deleted`) | `%APPDATA%\.hollowclient\screenshots` |
| Texture packs | `%APPDATA%\.hollowclient\resourcepacks` |
| Launcher settings | `%APPDATA%\.hollowclient\launcher_settings.json` |
| Play-time stats | `%APPDATA%\.hollowclient\launcher_stats.json` |
| Launcher log | `%APPDATA%\.hollowclient\launcher.log` |
| Your saved sign-in | Windows Credential Manager → Windows Credentials → "HollowClient" |

(When you play through the official launcher, Hollow's settings and files are in `%APPDATA%\.minecraft` instead.)
