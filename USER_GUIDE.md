# Hollow Client — user guide

This guide covers building Hollow Client, setting up Microsoft sign-in, using the launcher, and everything in the game: the menu, the keys, the HUD, effects, styles, themes and the extra features.

---

## 1. What you need (one time)

| Program | Why | Where |
|---|---|---|
| **Git** | to download and update this project | git-scm.com |
| **Java JDK 25 or newer** | to build the mod (the game itself uses Mojang's own Java, which the launcher downloads) | adoptium.net (Temurin) |
| **Python 3.12 or newer** | to build the launcher | python.org (tick "Add python.exe to PATH" when installing) |

Your PC already has all three (Git 2.55, Temurin 25, Python 3.14).

---

## 2. Build everything

1. Open the `hollow-client` folder.
2. Double-click **`scripts\build_all.bat`** (or run it in a terminal to see everything).
3. Wait. The first time takes a while (it downloads Gradle, Minecraft's code for building, and the Python packages). Later builds take a few minutes.

When it says **All done**, you have:

- the mod: `mod\build\libs\hollowclient-2.0.0.jar`
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

**Two versions (profiles).** The switch at the top right of Home picks what PLAY starts:
- **Hollow 26.2 — SkyBlock ready** (the default): Minecraft 26.2 with Fabric API, Sodium, the Hypixel Mod API and Hollow Client 2.x. This is the one for Hypixel SkyBlock.
- **Hollow 1.21.11 — Legacy**: the older Hollow Client 1.1 on Minecraft 1.21.11, with Fabric API and Sodium. It gets no new features.

Each profile has its own game folder (`%APPDATA%\.hollowclient\instances\<profile>`) with its own mods, settings, worlds, screenshots and texture packs. The first time you play a profile, it starts with your Hollow settings, Minecraft options and server list from the other profile; after that the two are separate. Minecraft itself, its libraries and Mojang's Java (Java 25 for 26.2, Java 21 for 1.21.11) are downloaded once and shared. The theme is kept the same in both.

**Coming from launcher 1.x?** The first time launcher 2 starts, it moves your old game folder's files (mods, config, saves, options, screenshots, texture packs, ...) into `instances\legacy-1.21.11`, so your 1.21.11 setup keeps working as the Legacy profile. Nothing is deleted; `migration.txt` in `%APPDATA%\.hollowclient` lists what moved.

### Home
- **PLAY** does everything that is still missing for the selected profile, in order:
  1. The first time, it installs that profile's Minecraft, Mojang's Java, Fabric, the profile's mods and the Hollow Client mod into `%APPDATA%\.hollowclient` (about 700 MB). This is a separate folder, so your normal Minecraft is not touched. The steps and the progress show under the button.
  2. It signs you in. The first time, your web browser opens Microsoft's sign-in page. Sign in there, then come back to the launcher. After that it remembers you (safely, in Windows Credential Manager). **Cancel sign-in** stops waiting for the browser.
  3. It starts the game. Its output appears on the **Logs** page.
- **Join Hypixel** starts the 26.2 profile (switching to it if needed) and connects straight to `mc.hypixel.net` from the title screen. Only the connection is automatic: once you're there, you play as usual (type `/skyblock` yourself).
- A banner appears when there are updates (**Update now**) or when something needs your attention.
- A yellow note appears when the profile's mods folder has **Skyblocker, SkyHanni or Firmament**. They overlap with Hollow's SkyBlock Mode; you can keep them, and in game Hollow offers to switch off its matching features.
- **Repair** checks every game file and downloads anything missing or broken. **Game folder** opens the selected profile's folder.

### SkyBlock
Live SkyBlock information, read from Hypixel's public data (no API key needed) and updated when you open the page (**Refresh** to update now). Without internet it shows the last saved data and says so.
- **Mayor**: the current mayor and minister with their perks, and when the term ends. **Election**: while the booth is open, the candidates and their share of the votes.
- **Coming up**: running and upcoming SkyBlock events with countdowns in your local time (Spooky Festival, Season of Jerry, Traveling Zoo, Hoppity's Hunt, the Dark Auction, Cult of the Fallen Star, Fishing Festivals when Marina is mayor, ...). The SkyBlock date is at the top.
- **Jacob's contests**: the next three contests and their crops (crops from the Elite API).
- **Bazaar movers**: the biggest price changes since about a day ago (the launcher keeps an hourly snapshot; movers show up once it has seen the bazaar twice, 10+ minutes apart). **Bazaar margins**: the biggest gaps between insta-buy and insta-sell on busy products, before tax.
- **Last session**: totals from your last SkyBlock session with Hollow Client (coins, XP, drops; filled in by the mod).

### Account (bottom left)
Your face from your skin and your name. Click it to **sign in**, **log out** (removes the saved sign-in) or open the `config.json` folder.

### Wardrobe
- Your skin in 3D with the cape, wings and halo you switched on in the game. **Drag** to turn it and tilt it; it keeps turning slowly after a few seconds. (When you aren't signed in it shows a stand-in figure.)
- **Mods**: installed and newest versions of the selected profile's mods (Fabric API, Sodium, the Hypixel Mod API on 26.2) and Hollow Client, **Check** for updates and **Update all**.
- **Your files**: buttons that open the folders for mods, texture packs, capes, music and screenshots.

### Stats
Time played in both profiles (in total, this week, longest session, how many days in a row), a chart of the last 14 days (hover a bar for the exact time), and counts from the selected profile's game folder: screenshots, worlds, waypoints, your own capes, songs and how many Hollow modules are on. Play time is recorded by the launcher while the game runs (not when "close the launcher when the game starts" is on).

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
| **H** (on SkyBlock) | Open the SkyBlock Hub (alerts, setup checker, feature health, compatibility) |

SkyBlock also has unbound keys you can set: **lock / unlock item** (item protection), **share my location in party chat** and **save hovered item (debug)**.

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

### SkyBlock Mode
When you join Hypixel SkyBlock, Hollow switches to **SkyBlock Mode**: a badge toast shows, your HUD swaps to its SkyBlock layout (your normal layout comes back when you leave), and the SkyBlock features for the island you're on turn on. They're in the menu under **SkyBlock**, grouped by area (General, Items, Economy, Garden, Dungeons, Slayers, Mining, Fishing, Hunting, Crimson Isle, Rift, Events, Progression, Chat). SkyBlock HUDs are placed with the normal HUD editor.

**First time:**
1. Press **H** on SkyBlock and open the **Setup checker**. It tells you exactly what's missing, for example the Hypixel Mod API or a tab list widget.
2. Turn on the tab list widgets it lists. On Hypixel: **SkyBlock Menu (`/sbmenu`) → Settings**, then the tab list (player list) widget settings, for each island. The Mining features need the Commissions, Powders, Forges and Crystals widgets; the Garden needs Visitors.
3. Prices (item values, bazaar, auction) load from Hypixel's public data on their own; the setup checker says when they're ready.

**What it can do** (everything is display only):
- **Everywhere:** status bars, pet, upcoming events, item values and tooltips, slot labels, item protection (locked items can't be dropped, sold or salvaged), menu search, alerts (rare drops, slayer bosses, your own chat alerts), session and skill progress, reminders (`/hollow remind 2h30m check the forge`, `/hollow remind list`, `/hollow remind clear`), a Tab Widgets HUD that shows any tab widget you name.
- **Economy:** bazaar orders, auctions, coins per hour, bazaar sparklines in tooltips.
- **Garden:** visitors and their offer cost, pests, Jacob's contests, composter.
- **Dungeons:** score estimate, the map item, terminal and Blaze helpers (outlines only), Spirit Leap classes, reward chest value.
- **Slayers:** quest progress, your boss's health and fight time, bosses per hour, drop value, a soft box around your boss (walls hide it).
- **Mining:** commissions, powder per hour, pickaxe ability cooldown, forge timers that keep counting between sessions, Cold/Heat, Crystal Hollows crystals, and a metal detector helper for the Mines of Divan (walk to four or five spots holding the detector).
- **Fishing:** bobber time and "Fish on!", sea creatures near you, rare catch alerts, trophy fish.
- **Hunting, Crimson Isle, Rift, Events:** Huntraps, Vanquisher alert, Kuudra chest value, Rift time and motes, Diana's burrow chain and Inquisitor alert, the Chocolate Factory's best-value upgrade.
- **Chat:** Chat Cleanup (hides SkyBlock's noisy lines, never players' messages, off by default), Party HUD, share location (one press, one party chat line).

**What it won't do:** Hollow follows Hypixel's rules for SkyBlock. It never clicks, moves or replies for you, solvers only outline slots, nothing shows through walls, and the only things it sends are what you trigger yourself with one key press (share location, chat macros). Auto GG never fires on SkyBlock. The full list of what was left out on purpose is in `DECISIONS.md` under "Skipped for Hypixel rules".

**If a feature shows nothing:** Hypixel's exact messages aren't all documented, so a few formats are best guesses. Open **H → Feature health** to see which features have their data right now. To help fix one, record a few minutes with `/hollow sbrec on` (and `/hollow sbrec off`): the file lands in `hollow-debug` in the game folder. Other players' chat is never saved. `TESTING_SKYBLOCK.md` lists what to check, area by area.

**Other SkyBlock mods:** if Skyblocker, SkyHanni or Firmament is installed too, Hollow's **Compatibility** screen (in the Hub) offers to switch off its overlapping features.

---

## 6. Playing through the official launcher (optional)

You can also play with Hollow Client through the official Minecraft Launcher, for example if you set up a new app ID that Mojang hasn't approved yet. In that case Hollow's launcher shows:

> Your app ID hasn't been approved by Mojang yet. You can still install and update everything, and launch from the official launcher in the meantime.

1. **Install Fabric** with the official Fabric installer from fabricmc.net/use/installer. Choose **Client**, Minecraft **26.2**, Loader **0.19.5** (or newer), and click Install. This adds a "fabric-loader-26.2" installation to the official launcher. Minecraft 26.2 needs Java 25; the official launcher brings its own.
2. **Open your normal mods folder**: press Windows+R, type `%APPDATA%\.minecraft` and press Enter. Make a folder called **`mods`** if there isn't one.
3. **Put these four files into it:**
   - `hollowclient-2.0.0.jar` (from `mod\build\libs`, or from Hollow's launcher folder)
   - **Fabric API** for 26.2 (modrinth.com/mod/fabric-api, pick the newest file for 26.2 and Fabric)
   - **Sodium** for 26.2 (modrinth.com/mod/sodium, same)
   - **Hypixel Mod API** for 26.2 (modrinth.com/mod/hypixel-mod-api); Hollow's SkyBlock Mode uses it to know where you are on Hypixel

   Hollow's launcher downloads exactly these into its own game folder, so once it has installed the 26.2 profile you can also copy them from there.
4. In the official Minecraft Launcher, pick the **fabric-loader-26.2** installation and press **Play**.

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

`<game>` is the profile's game folder: `%APPDATA%\.hollowclient\instances\hollow-26.2` or `...\instances\legacy-1.21.11`.

| What | Where |
|---|---|
| Hollow's folder (shared Minecraft files, launcher files) | `%APPDATA%\.hollowclient` |
| A profile's game folder | `%APPDATA%\.hollowclient\instances\<profile>` |
| Mods | `<game>\mods` |
| Hollow Client settings | `<game>\config\hollowclient.json` |
| Theme (shared with the launcher and the other profile) | `<game>\config\hollowclient\theme.json` |
| Your capes | `<game>\config\hollowclient\capes` |
| Your music | `<game>\config\hollowclient\music` |
| Waypoints and camera paths (per world) | `<game>\config\hollowclient\worlds` |
| Your HUD/module profiles | `<game>\config\hollowclient\profiles` |
| Screenshots (deleted ones in `deleted`) | `<game>\screenshots` |
| Texture packs | `<game>\resourcepacks` |
| Launcher settings (including the selected profile) | `%APPDATA%\.hollowclient\launcher_settings.json` |
| Play-time stats | `%APPDATA%\.hollowclient\launcher_stats.json` |
| SkyBlock page cache | `%APPDATA%\.hollowclient\skyblock_cache` |
| Launcher log | `%APPDATA%\.hollowclient\launcher.log` |
| Your saved sign-in | Windows Credential Manager → Windows Credentials → "HollowClient" |

(When you play through the official launcher, Hollow's settings and files are in `%APPDATA%\.minecraft` instead.)
