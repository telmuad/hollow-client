# Hollow Client — user guide

This guide walks you through building Hollow Client, setting up Microsoft login, playing, and using the in-game menu.

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

- the mod: `mod\build\libs\hollowclient-1.0.0.jar`
- the launcher: `launcher\dist\HollowClient\HollowClient.exe`

You can move the whole `launcher\dist\HollowClient` folder anywhere you like (for example to your Desktop) and start `HollowClient.exe` from there. Make a shortcut to it if you want.

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

**How the Azure app must be set up** (check this if login says the app ID is wrong):

- Supported account types: **personal Microsoft accounts** (either "Personal Microsoft accounts only" or the option that includes personal accounts).
- Authentication → Add a platform → **Mobile and desktop applications** → redirect URI **`http://localhost`**.
- Mojang must approve the app for the Minecraft API (you've already applied: https://aka.ms/mce-reviewappid). Until they do, see section 6.

---

## 4. Using the launcher

Start **`HollowClient.exe`**.

- **PLAY** does everything that is still missing, in order:
  1. The first time, it installs Minecraft 1.21.11, Mojang's Java, Fabric, Fabric API, Sodium and the Hollow Client mod into `%APPDATA%\.hollowclient` (about 700 MB). This is a separate folder, so your normal Minecraft is not touched.
  2. It signs you in. The first time, your web browser opens Microsoft's sign-in page. Sign in there, then come back to the launcher. After that it remembers you (safely, in Windows Credential Manager).
  3. It starts the game.
- **Account button** (top right): log in, or log out (log out removes the saved sign-in).
- **Repair installation** (Home): checks every game file and downloads anything missing or broken.
- **Styles & Mods**: shows installed and newest versions of Fabric API, Sodium and Hollow Client, with **Update all**. The launcher also checks for updates when it starts and shows a banner if there are some.
- **Settings**: how much memory Minecraft may use (default 4 GB), extra Java arguments, window size, "close the launcher when the game starts", and buttons to open the game folder and the `config.json` folder.
- **Logs**: the launcher's messages and the game's live output. **Copy logs** copies everything, which is handy if something goes wrong.

---

## 5. Using Hollow Client in game

### The menu: Right Shift

Press **Right Shift** in game (or on the title screen or the pause menu).

- On the left: **HUD**, **Visual**, **Quality of Life**, **Styles**.
- **Click a card** to switch that feature on or off.
- **Right-click a card** (or click its small slider icon) to open its settings: switches, sliders, color pickers and a keybind button.
- Type in the **search box** (or press `/`) to find a feature.
- Every feature also has a key you can set in **Options → Controls → Key Binds → Hollow Client**.
- Your settings are saved automatically (in `config\hollowclient.json` inside the game folder).

### HUD editor

Click **Edit HUD** in the menu (or **Move on screen** in a HUD element's settings).

- **Drag** an element to move it. It snaps to the screen edges, the middle, and other elements (pink lines show it). Hold **Alt** to place it freely.
- **Scroll** over an element, or drag its little corner square, to make it bigger or smaller.
- **Right-click** an element (or press **R**) to put it back where it started. **Reset all** puts everything back (click twice).
- **Show disabled** lets you place elements that are switched off.
- **Done** saves and closes.

### Handy features

- **Zoom**: hold **C**. Scroll while holding to zoom further in or out.
- **Toggle Sprint / Sneak**: switch it on, then tap your sprint key once to keep sprinting (tap again to stop). Sneak works the same way if you turn on "Toggle sneak".
- **Fullbright**, **Custom Crosshair**, **Low Fire**, **Clear Water** and **Hit Color** are in the Visual category.

### Styles (only you can see them)

Open the menu → **Styles**.

- **Capes**: click a cape to wear it. To use your own, click **Import PNG...** and pick a cape image (64x32 is the normal size), or drag the PNG onto the screen. Press **F5** to look at your back. "On elytra" uses the cape on an elytra too.
- **Cosmetics**: pick a hat and wings, change their color (tint) and how fast the wings flap. They show in F5 and in your inventory.
- **Texture Packs**: click **Import .zip...** or **Import folder...** (or drop a pack on the screen). Hollow copies the pack into your resourcepacks folder and lists it. Click **Use** to switch to it (Minecraft reloads its textures for a few seconds), or **Use none** to go back. If something is wrong with the pack, you get a message explaining what (for example "there's no pack.mcmeta at the top level").
- **Presets**: type a name and click **Save current** to save your cape, hat, wings and pack together. Click **Apply** to switch to that look with one click.

---

## 6. Until Mojang approves your app ID: play through the official launcher

Until approval, signing in from Hollow Client shows:

> Your app ID hasn't been approved by Mojang yet. You can still install and update everything, and launch from the official launcher in the meantime.

Here's how to play with Hollow Client through the official Minecraft Launcher:

1. **Install Fabric** with the official Fabric installer from fabricmc.net/use/installer. Choose **Client**, Minecraft **1.21.11**, Loader **0.19.5** (or newer), and click Install. This adds a "fabric-loader-1.21.11" installation to the official launcher.
2. **Open your normal mods folder**: press Windows+R, type `%APPDATA%\.minecraft` and press Enter. Make a folder called **`mods`** if there isn't one.
3. **Copy these three files into it.** Hollow's launcher has already downloaded them for you into `%APPDATA%\.hollowclient\mods`, so you can copy them from there:
   - `hollowclient-1.0.0.jar`
   - `fabric-api-0.141.6+1.21.11.jar`
   - `sodium-fabric-0.8.14+mc1.21.11.jar`

   (You can also get Fabric API and Sodium from modrinth.com; pick the versions for 1.21.11 and Fabric.)
4. In the official Minecraft Launcher, pick the **fabric-loader-1.21.11** installation and press **Play**.

Once Mojang approves the app ID, just press PLAY in Hollow Client instead.

---

## 7. If something goes wrong

- **"Microsoft login isn't set up yet"**: `config.json` is missing, still has the zeros, or isn't next to `HollowClient.exe`. See section 3.
- **"Microsoft didn't accept the app ID"** or **"The redirect URI doesn't match"**: check the Azure settings in section 3.
- **"This Microsoft account doesn't have an Xbox profile yet"**: sign in once at minecraft.net or xbox.com, then try again.
- **The game doesn't start, or closes right away**: open **Logs** and look at the last lines, or press **Repair installation**.
- **The install stopped halfway** (for example the internet dropped): press PLAY or Repair again; it carries on where it stopped.
- **To start over completely**: log out in the launcher, then delete the folder `%APPDATA%\.hollowclient`.

---

## 8. Where things are

| What | Where |
|---|---|
| Hollow's game folder | `%APPDATA%\.hollowclient` |
| Mods | `%APPDATA%\.hollowclient\mods` |
| Hollow Client settings | `%APPDATA%\.hollowclient\config\hollowclient.json` |
| Imported capes | `%APPDATA%\.hollowclient\config\hollowclient\capes` |
| Texture packs | `%APPDATA%\.hollowclient\resourcepacks` |
| Launcher settings | `%APPDATA%\.hollowclient\launcher_settings.json` |
| Launcher log | `%APPDATA%\.hollowclient\launcher.log` |
| Your saved sign-in | Windows Credential Manager → Windows Credentials → "HollowClient" |

(When you play through the official launcher, the Hollow Client settings, capes and packs are in `%APPDATA%\.minecraft` instead.)
