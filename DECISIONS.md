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
