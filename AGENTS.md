# AGENTS.md — OverGenerations

Minecraft 26.3 mod (Fabric + NeoForge) that replaces overworld generation.
Versions for everything (MC, Loom, loaders, APIs) live in `gradle.properties` —
that file is the single source of truth; CI pins Java via committed daemon
criteria, not via machine JDKs.

## Commands

```bash
./gradlew build                    # build + test both loaders
./gradlew :fabric:runClient        # dev client (needs a display)
./gradlew :neoforge:runClient      # dev client
./gradlew :fabric:test --tests "*.WorldSettingsJsonTest"   # single test class
./gradlew :fabric:genSources       # decompile Minecraft sources (see below)
```

Jars land in `fabric/build/libs/` and `neoforge/build/libs/` (plain jar, not
`-sources`). CI (`.github/workflows/build.yml`) builds both on every push to
`main` and uploads them as artifacts. Conventional Commits (`feat:`, `fix:`,
`docs:`, `ci:`).

## Build system gotchas

- `gradle/gradle-daemon-jvm.properties` pins Temurin **25** for the Gradle
  daemon on all platforms (generated via `./gradlew updateDaemonJvm
  --jvm-version=25`). It overrides any machine-level `org.gradle.java.home`.
  Do not hand-edit; regenerate it. Do not "fix" daemon errors by bumping this
  to a locally-installed JDK.
- Loom 1.18.x is built against **Gradle 9.7 API** — the wrapper must stay on
  Gradle 9.7.x. When changing Gradle versions, `./gradlew wrapper` fails with
  plugin-resolution errors before the task runs (chicken-and-egg): edit
  `gradle/wrapper/gradle-wrapper.properties` `distributionUrl` directly.
- Never add `mappings loom.officialMojangMappings()` — Minecraft 26.3 ships
  **unobfuscated** and Mojang publishes no mapping files anymore. The Fabric
  module has no `mappings` line on purpose, and loader/API deps use plain
  `implementation` (not `modImplementation`) since nothing is remapped.

## Multi-loader layout

- `common/` is **not a Gradle project**. Its sources are compiled straight
  into both loader jars via `srcDir(...)` lines in `fabric/build.gradle` and
  `neoforge/build.gradle`. `settings.gradle` intentionally includes only
  `fabric` and `neoforge`.
- Both loaders use the same (unobfuscated, official) names, so shared code
  references Minecraft classes directly with no per-loader split.
- Mixin classes live in `common/.../mixin/` and are listed in **both**
  `overgenerations.mixins.json` files (one copy per jar; each loader applies
  only its own config).
- MixinExtras: `compileOnly` + `annotationProcessor` from Maven Central
  (`io.github.llamalad7:mixinextras-common`, see `gradle.properties`). Both
  loaders bundle it at runtime — never `include`/JarJar it into the mod.

## Minecraft 26.3 specifics

- Ground truth for mixin targets and API signatures is the decompiled game:
  run `./gradlew :fabric:genSources`, then read
  `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/26.3/*-sources.jar`
  (or `javap -p` against `~/.gradle/caches/fabric-loom/26.3/minecraft-merged.jar`).
  26.3 moved many classes — e.g. `WorldOpenFlows` is in
  `net.minecraft.client.gui.screens.worldselection`, `GameRules` in
  `net.minecraft.world.level.gamerules`. Never trust remembered mappings.
- Data pack format 121. Compared to 1.21.x: configured features moved to
  `worldgen/feature` (Feature + FeatureConfiguration merged), surface rules
  became registered **material rules** (`worldgen/material_rule`,
  `worldgen/material_condition`). Check the Fabric 26.3 blog post before
  writing worldgen JSON.

## Per-world settings flow (architecture)

Settings are chosen on a native "OverGenerations" tab injected into
`CreateWorldScreen` (`common/.../mixin/CreateWorldScreenMixin.java`), persisted
to `<world>/overgenerations/settings.json` **between** world-directory creation
and server start (race-free injection point inside `createNewWorld`), then read
by the server at startup into `OverGenerationsServerState`.

- A missing or corrupt settings file means the mod is completely inert — this
  is deliberate (pre-mod worlds must stay vanilla). Never "fix" this by
  applying defaults to unmanaged worlds.
- Terrain and biome scale are baked in at world creation; only the structures
  mode will be changeable on existing worlds (planned `/overgenerations`).
- Global defaults come from `config/overgenerations.json` (created on first
  launch, runtime file, not in the repo).

## Testing

Shared tests live in `common/src/test/java` and run in **both** modules via
`srcDir` (same suite executes twice; that's intended). The test classpath does
not inherit Minecraft's libraries — gson and slf4j are declared explicitly in
both build files; add new main-source dependencies there if tests need them.

QA instance (owner's machine, PrismLauncher):
`/home/thermed/.local/share/PrismLauncher/instances/Testing fabric 26.3/minecraft`
— copy the built Fabric jar into its `mods/` folder (Fabric API is already
installed there) to let the owner verify GUI/world behavior.

## Project conventions

- Code, identifiers, and comments in English; lang entries in
  `common/src/main/resources/assets/overgenerations/lang/en_us.json`.
- `common` code must stay loader-agnostic: no Fabric/NeoForge API imports.
  Loader-specific glue belongs in `fabric/` / `neoforge/` entrypoints.
- Client-only classes in `common` (e.g. the settings tab) are never loaded on
  dedicated servers — they're only referenced from client mixins. Keep it that
  way rather than splitting them per loader.
- Roadmap and milestone status live in `README.md` (M0 scaffold, M1 per-world
  settings done; M2 custom terrain next). The repo owner hand-builds all
  structure NBTs in-game; agent work is wiring template pools, structure sets,
  and placement (M4) — do not try to author structure content yourself.
