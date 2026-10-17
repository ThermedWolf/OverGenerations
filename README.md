# OverGenerations

A Minecraft mod that enhances overworld terrain generation: smoother landscapes
with real mountains and valleys, real-world-inspired biomes, and custom
structures — every feature configurable per world.

Targets **Minecraft 26.3** (data pack format 121) on **Fabric** and **NeoForge**.

## Planned features

| Feature | Description |
|---|---|
| Smoother terrain | Higher, real mountains and wide valleys with smooth transitions between large regions. |
| Real-world biomes | Desert, sea, ocean, alps, and more, placed to match the new terrain. |
| Custom structures | Villages, city buildings, ruins, strongholds, and hidden historic stories to uncover. |
| Natural panoramas | Waterfalls, alpine walks, and scenic vistas. |

### Per-world settings (New World screen)

- **Enable OverGenerations** — master toggle; off means the mod is completely
  inert for that world.
- **Custom terrain generation** — on/off; when off, terrain is vanilla but the
  mod can still generate custom structures.
- **Biome size** — 1x to 20x (default 2x). Scales biome length and width only,
  never terrain height.
- **Structures** — `All` (including never-before-seen builds), `Normal only`
  (structures with vanilla equivalents), or `Off` (vanilla structures only).
- **Non-exposed ore blocks** — off by default; when on, ores never generate
  exposed on the surface, so you have to dig into mountains to find them.

Terrain and biome size are chosen at world creation and permanent for that
world, matching how Minecraft stores generation data. The structures mode will
also be switchable on existing worlds via `/overgenerations` (planned).

## Roadmap

| Milestone | Deliverable | Status |
|---|---|---|
| M0 | Build scaffold, both loaders, CI, public repo | ✅ done |
| M1 | Per-world settings + New World screen integration | ✅ done |
| M2 | Custom terrain: mountains, valleys, smooth transitions | planned |
| M3 | Real-world biomes + biome size setting | planned |
| M4 | Structures pipeline (template wiring, All/Normal/Off gating) | planned |
| M5 | Custom-only structures: ruins, hidden stories, strongholds | planned |
| M6 | Panoramas: waterfalls, alpine walks | planned |

## Building the structures

Custom structures are hand-built in-game and then wired into the mod:

1. Build a structure in a creative test world and copy it with structure blocks.
2. Export the `.nbt` file and place it under
   `common/src/main/resources/data/overgenerations/structure/...`.
3. Template pools, structure sets, and placement rules are defined as data pack
   JSON in the same tree, so structures need no code changes.

## Repository layout

```
common/    Shared Java sources and data pack resources (compiled into both jars)
fabric/    Fabric entrypoints, mixins, and metadata (Loom)
neoforge/  NeoForge entrypoints, mixins, and metadata (ModDevGradle)
```

## Development

Requirements: JDK 25+ (Gradle downloads the toolchain automatically).

```bash
./gradlew build              # build both loader jars
./gradlew :fabric:runClient  # run a Fabric dev client
./gradlew :neoforge:runClient  # run a NeoForge dev client
```

Built jars end up in `fabric/build/libs/` and `neoforge/build/libs/`.

## License

[MIT](LICENSE)
