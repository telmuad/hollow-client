# Moving Hollow Client to a new Minecraft version

A checklist for the next bump (for example 26.2 → 26.3). Version-specific values live in two places only:

- **Mod**: `mod/gradle.properties` (Minecraft, Fabric Loader, Loom, Fabric API, Sodium for the dev run, the mod's own version).
- **Launcher**: `launcher/hollow_launcher/profiles.json` (each profile's Minecraft version, minimum Fabric Loader and the Modrinth projects to install).

Everything else (Java version, Mojang's Java runtime, library files) follows from those.

## 0. Is it time?

1. Check what Hypixel recommends for SkyBlock (the latest SkyBlock patch notes on the Hypixel forums). Hollow follows that, not simply the newest Minecraft release.
2. Check that the dependencies exist for the new version on Modrinth: Fabric API (`P7dR8mSH`), Sodium (`AANobbMI`) and the Hypixel Mod API (`1A2mKfBx`). Without all three, wait.
3. Check `https://meta.fabricmc.net/v2/versions/loader/<version>` for a stable Fabric Loader, and the Fabric example mod's branch for that version (`github.com/FabricMC/fabric-example-mod/tree/<version>`) for the Loom and Gradle versions it uses.

## 1. Keep the old version

1. Tag the current state: `git tag legacy-<old version>`, create `legacy/<old version>` and push both.
2. Decide whether the launcher keeps a profile for the old version (it can keep any number; see step 5).

## 2. Mod build

1. `mod/gradle.properties`: set `minecraft_version`, `loader_version`, `loom_version`, `fabric_api_version`, `sodium_version`, and raise `version` (major bump for a new Minecraft generation).
2. If the new Loom needs it, update Gradle: `gradlew.bat wrapper --gradle-version <x>` (Loom tells you the minimum when it fails).
3. `mod/src/main/resources/fabric.mod.json`: the `minecraft` range (`~<version>`) and `java` if Mojang raised it.
4. If Java changed: `release` and `sourceCompatibility` in `mod/build.gradle`.

## 3. Port the code

1. `gradlew.bat genSources` (decompiled Minecraft sources for reference), then `gradlew.bat compileJava`.
2. Read the Fabric blog post for the version (`fabricmc.net`, "Fabric for Minecraft <version>") and NeoForge's primer (`docs.neoforged.net/primer/docs/<version>/`); both list renamed and removed classes and methods.
3. Fix compile errors file by file. For new Fabric API signatures, the module sources are on `maven.fabricmc.net`.
4. Check every mixin target, since the compiler can't: `python tools/mixincheck.py` (from `mod/`). It must report `problems: 0`.
5. `gradlew.bat build`: all unit tests must pass, including `NoRawOpenGlTest` (all drawing must go through Blaze3D, never raw OpenGL).

## 4. In-game checks

Run all three and review the screenshots in `mod/build/run/clientGameTest/screenshots`:

1. `gradlew.bat runClientGameTest` (OpenGL)
2. `gradlew.bat runClientGameTest -PwithSodium`
3. `gradlew.bat runClientGameTest -Pvulkan` (Minecraft's Vulkan backend)

Each run ends with the FPS benchmark (`mod/build/run/clientGameTest/hollow_fps_results.md`); copy the tables into TESTING.md. Handler signatures and injection points that moved show up as a crash at startup with an `InvalidInjectionException` naming the mixin.

## 5. Launcher

1. `launcher/hollow_launcher/profiles.json`: add or change the profile for the new version (Minecraft version, minimum loader, Modrinth project ids). Mojang's Java runtime is picked from the version itself.
2. `launcher\.venv\Scripts\python -m pytest`.
3. `scripts\build_all.bat` builds the mod, the legacy jar(s) and the launcher. Then do one real install of the new profile and check the instance folder.

## 6. Docs

README (versions), USER_GUIDE (manual install section), TESTING.md (FPS tables), PROGRESS.md and DECISIONS.md (what changed and why).
