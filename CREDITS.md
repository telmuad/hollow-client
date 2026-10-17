# Credits

Everything in Hollow Client is original unless it is listed under "Licensed" below.

## Licensed

| What | Where it is used | License |
|---|---|---|
| **Inter** typeface (Medium, SemiBold, Bold), © 2016 The Inter Project Authors, https://github.com/rsms/inter | In-game UI font (`mod/src/main/resources/assets/hollowclient/font/`) and the launcher (`launcher/assets/fonts/`) | SIL Open Font License 1.1. Full text: `mod/src/main/resources/licenses/Inter-OFL.txt` and `launcher/assets/fonts/Inter-OFL.txt` |

| **JavaMP3** 1.0.1, © 2017 delthas, https://github.com/delthas/JavaMP3 | MP3 decoding for the music player (bundled inside the mod jar) | MIT License |

The font files are unmodified copies from the Inter v4.1 release. They are bundled, not sold on their own, as the OFL allows.

## Original (made for Hollow Client)

| What | How it was made |
|---|---|
| The Hollow emblem, wordmark, SkyBlock badge, banner, social preview, mod icon and launcher icon | Original, drawn from geometry by `branding/tools/emblem.py` (Qt). The wordmark uses Inter (see Fonts). The brand cape's pixel emblem is drawn by `mod/tools/texturegen/HollowTextureGen.java`. |
| Capes, hat and wing textures, category art | `mod/tools/texturegen/HollowTextureGen.java` (fixed random seed, so the output is reproducible) |
| UI sounds (click, hover, toggles, whooshes, toast, notify, shutter, charge, pop) | Synthesized from sine waves and filtered noise by `mod/tools/soundgen/make_sounds.py`, encoded to Ogg Vorbis with ffmpeg |
| Theme presets (Void, Inferno, Toxic, Frost, Bloodmoon, Rainbow) | Hollow's own color choices |
| Launcher nebula and glass shader (`launcher/hollow_launcher/qml/shaders/nebula.frag`) | Written for Hollow (value noise with domain warping) |
| Launcher sounds (`launcher/assets/sounds/`) | `launcher/tools/make_launcher_sounds.py`, using the same synthesizer as the game's sounds; the intro swell is new |
| Launcher line icons, the intro animation and the hooded stand-in skin | Drawn in code (`qml/components/Icon.qml`, `qml/components/Intro.qml`, `qmlui/skin_view.py`) |
| Test tones (`mod/src/test/resources/audio/`) | Plain 440 Hz and 880 Hz sine waves generated with ffmpeg, used only by the tests |

## SkyBlock data

Hollow's SkyBlock Mode contains no code from other SkyBlock mods (Skyblocker, SkyHanni, Firmament, Skytils, …); their features were used as ideas only. Data it uses:

| What | How it's used | License / terms |
|---|---|---|
| **Hypixel Mod API** (`net.hypixel:mod-api`), https://github.com/HypixelDev/ModAPI | Compiled against (not bundled); the mod itself is installed by the launcher from Modrinth | MIT License |
| **NotEnoughUpdates-REPO**, https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO | Item names, recipes, fairy souls and constants, downloaded at runtime and cached (not bundled) | MIT License |
| **Hypixel public API** (bazaar, items, election) and the **Elite API** (lowest BIN, Jacob's contests) | Fetched at runtime and cached, without an API key | Their own terms of use |
| **Hypixel SkyBlock Wiki** (hypixelskyblock.minecraft.wiki) | Facts checked there: cooldowns, catch messages, chat lines, formulas, lists of creatures and items; short game messages are quoted in the pattern files | Game text belongs to Hypixel; wiki facts credited here |

## Libraries and tools

These are used to build or run Hollow Client and keep their own licenses: Fabric Loader, Fabric API and Loom (Apache 2.0), Mixin and MixinExtras (MIT), JOrbis (LGPL, shipped with Minecraft itself and used for Ogg playback), minecraft-launcher-lib (BSD-2-Clause), PySide6 / Qt, including Qt Quick (LGPLv3), keyring (MIT), requests (Apache 2.0), PyInstaller (GPL with a bootloader exception), pytest (MIT). Sodium (PolyForm Shield) is downloaded from Modrinth by the launcher, never bundled.

Minecraft is not included in this repository or in any build output. The launcher downloads it from Mojang's official servers.
