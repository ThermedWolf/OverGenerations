#!/usr/bin/env python3
"""Generates the OverGenerations overworld terrain data.

Reads the vanilla 26.3 worldgen JSON embedded in the Fabric-Loom client jar,
copies the overworld terrain stack into the `overgenerations` namespace, and
applies the terrain tweaks defined in the knobs below.

Everything this script emits lands in
`common/src/main/resources/data/overgenerations/worldgen/`. The generated
files are committed, so building the mod never requires running this script;
re-run it after changing a knob:

    python3 tools/generate_worldgen.py

Design notes:
- Terrain height is scaled PROPORTIONALLY (every positive offset spline value
  by the same factor) so relative land shapes — and the transitions between
  them — keep vanilla's character. Boosting only the mountain columns creates
  straight cliffs along erosion contours; don't do that.
- The ultra-peak wedge is GATED on the offset value: it only extends mountains
  that are already tall. An ungated wedge fills valleys with vertical walls.
- Caves and ore-vein functions are copied too because they internally
  reference the terrain functions (depth/jaggedness) and must stay consistent
  with the new terrain.
- The surface `material_rule` references vanilla's `minecraft:overworld`
  registered rule, which is reused as-is (same surface blocks).
- The biome climate sampler follows the noise router, so biome placement
  automatically tracks the new terrain.
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_ROOT = REPO_ROOT / "common/src/main/resources/data/overgenerations/worldgen"

MOD_NS = "overgenerations"
MC_VERSION = "26.3"
VANILLA_OVERWORLD = "minecraft:overworld"
OUR_OVERWORLD = f"{MOD_NS}:overworld"

# ---------------------------------------------------------------------------
# TWEAKS — terrain personality lives here. Re-run the script after editing.
# ---------------------------------------------------------------------------

# Noise parameters in 26.3 use base_octave + octave_count +
# amplitude_modifiers (the old firstOctave/amplitudes format is gone).
# One octave lower = features twice as large.
CONTINENTALNESS_BASE_OCTAVE = -10  # vanilla -9
EROSION_BASE_OCTAVE = -10          # vanilla -9
RIDGE_BASE_OCTAVE = -8             # vanilla -7

# Uniform multiplier on every positive offset-spline value. Scales all land
# (plains, hills, mountains) proportionally, preserving vanilla's relative
# transitions. ~1.3 puts regular alpine ranges around Y200-220.
TERRAIN_HEIGHT_SCALE = 1.3

# Ultra peaks: a rare, gated height wedge that extends the tallest mountains
# toward Y300. The wedge strength is the product of two smooth ramps:
#   - a rarity mask (low-frequency noise, wide fade-in)
#   - a terrain gate (only where the offset already forms high mountains)
# boost_top_value adds roughly boost_top_value / 0.0078 blocks of extra height
# at full strength (~90 blocks at 0.7).
ULTRA_PEAK = {
    "enabled": True,
    "noise_base_octave": -6,
    "noise_octave_count": 1,
    "threshold": 0.45,       # mask fade-in starts at this noise value
    "mask_gain": 2.5,        # mask saturates 1/(1-threshold)*gain... wide ramp
    "gate_start": 0.30,      # offset value where the gate begins to open
    "gate_gain": 6.0,        # gate fully open 0.15-0.2 offset above the start
    "boost_from_y": 240,     # full boost from this height downward
    "boost_to_y": 320,       # zero boost at the build ceiling
    "boost_top_value": 0.7,
}

# Per-peak spikiness from the jagged noise, 1.0 = vanilla. Lower = smoother
# summit profiles.
JAGGEDNESS_SCALE = 0.7

# Scales the cave-entrance carving term. 1.0 = vanilla (lots of open holes in
# the surface); lower = fewer, smaller surface openings.
CAVE_ENTRANCE_SCALE = 0.65

# ---------------------------------------------------------------------------


def locate_client_jar(explicit: str | None) -> Path:
    if explicit:
        return Path(explicit)
    candidates = sorted(
        Path.home().glob(".gradle/caches/fabric-loom/*/minecraft-client.jar")
    ) + sorted((REPO_ROOT / ".gradle/loom-cache").rglob("minecraft*client*.jar"))
    # Prefer the jar matching the project's Minecraft version.
    versioned = [c for c in candidates if f"/{MC_VERSION}/" in str(c)]
    if not versioned:
        sys.exit(
            f"Could not locate the Loom client jar for Minecraft {MC_VERSION}; pass --client-jar"
        )
    return versioned[-1]


def load_json_from_jar(jar: zipfile.ZipFile, name: str):
    return json.loads(jar.read(f"data/minecraft/{name}"))


def walk(node, fn):
    """Applies fn to every dict in a JSON tree, in place."""
    if isinstance(node, dict):
        fn(node)
        for value in node.values():
            walk(value, fn)
    elif isinstance(node, list):
        for value in node:
            walk(value, fn)


def rewrite_namespace(node) -> None:
    """Points every `minecraft:overworld/...` function reference at our copy."""

    def visit(obj: dict) -> None:
        for key, value in list(obj.items()):
            if isinstance(key, str) and key.startswith(VANILLA_OVERWORLD + "/"):
                # spawn_target maps are keyed by density function ID.
                new_key = OUR_OVERWORLD + key[len(VANILLA_OVERWORLD):]
                obj[new_key] = obj.pop(key)
            if isinstance(value, str) and value.startswith(VANILLA_OVERWORLD + "/"):
                obj[key] = OUR_OVERWORLD + value[len(VANILLA_OVERWORLD):]

    walk(node, visit)


def retarget_noise(node, vanilla_noise: str) -> None:
    def visit(obj: dict) -> None:
        if obj.get("noise") == f"minecraft:{vanilla_noise}":
            obj["noise"] = f"{MOD_NS}:{vanilla_noise}"

    walk(node, visit)


def scale_positive_spline_values(node, scale: float, label: str) -> int:
    """Multiplies every numeric spline point value > 0; returns how many."""
    count = 0

    def visit(obj: dict) -> None:
        nonlocal count
        points = obj.get("points")
        if not isinstance(points, list):
            return
        for point in points:
            value = point.get("value")
            if isinstance(value, (int, float)) and value > 0:
                point["value"] = round(value * scale, 6)
                count += 1

    walk(node, visit)
    return count


def constant(value: float) -> dict:
    return {"type": "minecraft:constant", "value": value}


def add_ultra_peak_term(final_density: dict) -> dict:
    """Wraps final_density with the gated, masked height wedge."""
    cfg = ULTRA_PEAK
    # Rarity mask: fades in over a wide noise range, then saturates at 1.
    mask = {
        "type": "minecraft:min",
        "left": constant(1.0),
        "right": {
            "type": "minecraft:mul",
            "left": {
                "type": "minecraft:max",
                "left": constant(0.0),
                "right": {
                    "type": "minecraft:add",
                    "left": {
                        "type": "minecraft:noise",
                        "noise": f"{MOD_NS}:ultra_peaks",
                        "xz_scale": 1.0,
                        "y_scale": 0.0,
                    },
                    "right": constant(-cfg["threshold"]),
                },
            },
            "right": constant(cfg["mask_gain"]),
        },
    }
    # Terrain gate: opens only where the offset already forms high mountains.
    gate = {
        "type": "minecraft:min",
        "left": constant(1.0),
        "right": {
            "type": "minecraft:max",
            "left": constant(0.0),
            "right": {
                "type": "minecraft:mul",
                "left": {
                    "type": "minecraft:add",
                    "left": OUR_OVERWORLD + "/offset",
                    "right": constant(-cfg["gate_start"]),
                },
                "right": constant(cfg["gate_gain"]),
            },
        },
    }
    # Height wedge: linear fade from full boost at boost_from_y to 0 at the
    # build ceiling.
    wedge = {
        "type": "minecraft:gradient",
        "axis": "y",
        "from_coordinate": cfg["boost_to_y"],
        "from_value": 0.0,
        "to_coordinate": cfg["boost_from_y"],
        "to_value": cfg["boost_top_value"],
    }
    return {
        "type": "minecraft:add",
        "left": final_density,
        "right": {
            "type": "minecraft:mul",
            "left": {"type": "minecraft:mul", "left": mask, "right": gate},
            "right": wedge,
        },
    }


def scale_cave_entrances(entrances: dict) -> dict:
    if CAVE_ENTRANCE_SCALE == 1.0:
        return entrances
    return {
        "type": "minecraft:mul",
        "left": constant(CAVE_ENTRANCE_SCALE),
        "right": entrances,
    }


def collect_noise_ids(node, into: set) -> None:
    def visit(obj: dict) -> None:
        noise = obj.get("noise")
        if isinstance(noise, str) and noise.startswith("minecraft:"):
            into.add(noise.removeprefix("minecraft:"))

    walk(node, visit)


def write_json(rel_path: str, data) -> None:
    path = OUTPUT_ROOT / rel_path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--client-jar", help="Path to the Loom minecraft client jar")
    args = parser.parse_args()

    jar_path = locate_client_jar(args.client_jar)
    print(f"Reading vanilla worldgen from {jar_path}")

    if OUTPUT_ROOT.exists():
        import shutil

        shutil.rmtree(OUTPUT_ROOT)

    noise_param_overrides = {
        "continentalness": {"base_octave": CONTINENTALNESS_BASE_OCTAVE},
        "erosion": {"base_octave": EROSION_BASE_OCTAVE},
        "ridge": {"base_octave": RIDGE_BASE_OCTAVE},
    }

    with zipfile.ZipFile(jar_path) as jar:
        # 1. Noise settings: the router now points at our function copies.
        settings = load_json_from_jar(jar, "worldgen/noise_settings/overworld.json")
        rewrite_namespace(settings)
        print("Rewrote router to overgenerations namespace")
        # NOTE: the wedge is applied to the final_density FILE below; the
        # router only holds a string reference to that file, so wrapping both
        # would apply the wedge twice.
        write_json("noise_settings/overworld.json", settings)

        # 2. All overworld density functions, fully namespaced for consistency.
        function_names = [
            name
            for name in jar.namelist()
            if name.startswith("data/minecraft/worldgen/density_function/overworld/")
            and name.endswith(".json")
        ]
        for name in function_names:
            rel = name.removeprefix("data/minecraft/worldgen/")
            data = load_json_from_jar(jar, name.removeprefix("data/minecraft/"))
            rewrite_namespace(data)
            retarget_noise(data, "continentalness")
            retarget_noise(data, "erosion")
            retarget_noise(data, "ridge")
            if rel == "density_function/overworld/offset.json":
                scaled = scale_positive_spline_values(data, TERRAIN_HEIGHT_SCALE, "offset")
                print(f"Scaled {scaled} offset spline values x{TERRAIN_HEIGHT_SCALE}")
            if rel == "density_function/overworld/jaggedness.json":
                scaled = scale_positive_spline_values(data, JAGGEDNESS_SCALE, "jaggedness")
                print(f"Scaled {scaled} jaggedness spline values x{JAGGEDNESS_SCALE}")
            if rel == "density_function/overworld/final_density.json" and ULTRA_PEAK["enabled"]:
                data = add_ultra_peak_term(data)
            if rel == "density_function/overworld/caves/entrances.json":
                data = scale_cave_entrances(data)
            write_json(rel, data)
        print(f"Copied {len(function_names)} density functions")

        # 3. Noise parameters: copy the ones our functions reference; the three
        #    terrain noises get tweaked, everything else stays vanilla-identical.
        referenced: set[str] = set()
        for name in function_names:
            collect_noise_ids(load_json_from_jar(jar, name.removeprefix("data/minecraft/")), referenced)
        collect_noise_ids(settings, referenced)
        retargeted = set(noise_param_overrides)
        for noise_id in sorted(referenced - retargeted):
            write_json(f"noise/{noise_id}.json", load_json_from_jar(jar, f"worldgen/noise/{noise_id}.json"))
        for noise_id, overrides in noise_param_overrides.items():
            param = load_json_from_jar(jar, f"worldgen/noise/{noise_id}.json")
            # 26.3 schema: base_octave drives feature size; drop stale keys.
            param.pop("firstOctave", None)
            param.pop("amplitudes", None)
            param.update(overrides)
            write_json(f"noise/{noise_id}.json", param)
        if ULTRA_PEAK["enabled"]:
            write_json(
                "noise/ultra_peaks.json",
                {
                    "base_octave": ULTRA_PEAK["noise_base_octave"],
                    "octave_count": ULTRA_PEAK["noise_octave_count"],
                },
            )
        print(f"Copied {len(referenced - retargeted)} noise params + {len(retargeted)} tweaked")

    total = sum(1 for _ in OUTPUT_ROOT.rglob("*.json"))
    print(f"Wrote {total} files to {OUTPUT_ROOT}")


if __name__ == "__main__":
    main()
