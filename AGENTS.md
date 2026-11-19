# Hammer development

Use Java 25 and the checked-in Gradle wrapper. Stonecutter builds 26.1 and 26.3 from one checkout with 26.1 as the VCS version. Check `versions/<target>-neoforge/gradle.properties` for the matching NeoForge version. Keep gameplay, recipes, progression, controls and player interfaces shared across both targets. Use version conditions for Minecraft or NeoForge API differences. The loader mod ID is `florafauna_hammer`. Existing Hammer items, projectiles, components and packets use the legacy `florafauna` namespace. Keep the mod runnable with just Minecraft and NeoForge.

Test the behavior affected by each edit. JUnit, headless GameTests and the scripted quarry or action-wheel client can overlap independent work in other worktrees. The scripted client sends input inside Minecraft. Reserve the desktop only for steps that use real input or focus.

- For shape cycling or configuration, use `./gradlew :26.1-neoforge:test --tests '*HammerConfigTest'` or the relevant JUnit class.
- For world behavior, run one GameTest or group, for example `./gradlew "-PgameTestFilter=florafauna_hammer:hammer_tunnel_gaps" :26.1-neoforge:runGameTestServer`.
- For controls, rendering and save/reload, run `./scripts/test_hammer.ps1 -Target 26.1 -SourceWorld <closed-save-directory>` under PowerShell 7. After a headless test you can use its generated `versions/26.1-neoforge/run/gameTestServer/gametestserver/gametestworld`. The runner makes a disposable copy.
- For a release or combined change, run `./gradlew :26.1-neoforge:build :26.1-neoforge:runGameTestServer` and repeat for `26.3-neoforge`, or use the verified CI run for the same revision. On Windows use `gradlew.bat`. Check the selected test names and result count.
- Keep worlds, screenshots, generated test output and local settings out of source control. Commit models, tags and other shipped resources under `src/main/resources`. The client quarry fixture and automation belong to `src/playtest`.
