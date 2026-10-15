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
| The H sigil logo, mod icon, launcher icon | Drawn in code: `mod/tools/texturegen/HollowTextureGen.java` (game textures) and `launcher/hollow_launcher/ui/logo.py` (launcher). Same shape in both. |
| Capes, hat and wing textures, category art | `mod/tools/texturegen/HollowTextureGen.java` (fixed random seed, so the output is reproducible) |
| UI sounds (click, hover, toggles, whooshes, toast, notify, shutter, charge, pop) | Synthesized from sine waves and filtered noise by `mod/tools/soundgen/make_sounds.py`, encoded to Ogg Vorbis with ffmpeg |
| Theme presets (Void, Inferno, Toxic, Frost, Bloodmoon, Rainbow) | Hollow's own color choices |
| Test tones (`mod/src/test/resources/audio/`) | Plain 440 Hz and 880 Hz sine waves generated with ffmpeg, used only by the tests |

## Libraries and tools

These are used to build or run Hollow Client and keep their own licenses: Fabric Loader, Fabric API and Loom (Apache 2.0), Mixin and MixinExtras (MIT), JOrbis (LGPL, shipped with Minecraft itself and used for Ogg playback), minecraft-launcher-lib (BSD-2-Clause), PySide6 / Qt (LGPLv3), keyring (MIT), requests (Apache 2.0), PyInstaller (GPL with a bootloader exception), pytest (MIT). Sodium (PolyForm Shield) is downloaded from Modrinth by the launcher, never bundled.

Minecraft is not included in this repository or in any build output. The launcher downloads it from Mojang's official servers.
