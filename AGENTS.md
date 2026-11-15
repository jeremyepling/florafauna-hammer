# Hammer development

Use Java 25 and the checked-in Gradle wrapper. `main` targets Minecraft 26.1 and `26.3` targets 26.3. Check `gradle.properties` for the matching NeoForge version. The loader mod ID is `florafauna_hammer`. Existing Hammer items, projectiles, components and packets use the legacy `florafauna` namespace. Keep the mod runnable with just Minecraft and NeoForge.

Test the behavior affected by each edit.

- For shape cycling or configuration, use `./gradlew test --tests '*HammerConfigTest'` or the relevant JUnit class.
- For world behavior, run one GameTest or group, for example `./gradlew "-PgameTestFilter=florafauna_hammer:hammer_tunnel_gaps" runGameTestServer`.
- For controls, rendering and save/reload, run `./scripts/test_hammer.ps1 -SourceWorld <closed-save-directory>` under PowerShell 7. After a headless test you can use its generated `run/gameTestServer/gametestserver/gametestworld`. The runner makes a disposable copy.
- For a release or combined change, run `./gradlew build runGameTestServer` or use the verified CI run for the same revision. On Windows use `gradlew.bat`. Check the selected test names and result count.
- Keep worlds, screenshots, generated test output and local settings out of source control. Commit models, tags and other shipped resources under `src/main/resources`. The client quarry fixture and automation belong to `src/playtest`.
