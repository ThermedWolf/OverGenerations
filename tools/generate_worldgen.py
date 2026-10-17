#!/usr/bin/env python3
"""Generates the OverGenerations overworld terrain data.

Reads the vanilla 26.3 worldgen JSON embedded in the Fabric-Loom client jar,
copies the overworld terrain stack into the `overgenerations` namespace, and
applies the terrain tweaks defined in TWEAKS below.

Everything this script emits lands in
`common/src/main/resources/data/overgenerations/worldgen/`. The generated
files are committed, so building the mod never requires running this script;
re-run it after changing a knob:

    python3 tools/generate_worldgen.py

Design notes:
- Only the terrain-driving density functions are modified. Caves and ore-vein
  functions are copied too because they internally reference the terrain
  functions (depth/jaggedness) and must stay consistent with the new terrain.
- The surface `material_rule` references vanilla's `minecraft:overworld`
  registered rule, which is reused as-is (same surface blocks).
- The biome climate sampler follows the noise router, so biome placement
  automatically tracks the new terrain.
"""

from __future__ import annotations

import argparse
import json
import re
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

# Larger, more coherent landmasses: one octave lower than vanilla (-9).
CONTINENTALNESS_BASE_OCTAVE = -10

# Smoother large-scale transitions: one octave lower than vanilla (-9).
EROSION_BASE_OCTAVE = -10

# Wider mountain ranges: one octave lower than vanilla (-7).
RIDGE_BASE_OCTAVE = -8

# Mountain tops (the linear ridges_folded splines topping around 0.64-0.69)
# are multiplied by this where they occur. ~1.5 pushes regular alpine ranges
# from vanilla ~Y150 to roughly Y200-220.
ALPINE_BOOST = 1.5
ALPINE_TOP_VALUE_RANGE = (0.6, 0.75)  # which ridge-top values count as mountain tops

# Ultra peaks: a rare additive wedge in final_density that builds the "highest
# mountains of the world" (~Y300) in a few spots. The mask is a low-frequency
# noise; only places where it exceeds ULTRA_PEAK_THRESHOLD get boosted.
ULTRA_PEAK = {
    "enabled": True,
    # Single-octave mask noise (one rare large-scale roll): smaller base octave
    # = larger peak blobs.
    "noise_base_octave": -6,
    "noise_base_amplitude": 1.0,
    "threshold": 0.55,     # rarity: higher = rarer
    "gain": 3.0,           # how sharply the mask saturates past the threshold
    "boost_from_y": 160,   # full boost from this height downward
    "boost_to_y": 320,     # zero boost at the build ceiling
    "boost_top_value": 1.9,
}

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


def boost_alpine_tops(node) -> int:
    """Multiplies mountain-top spline values; returns how many were boosted."""
    count = 0

    def visit(obj: dict) -> None:
        nonlocal count
        # Splines look like: {"type": "spline", "coordinate": ..., "points": [...]}
        points = obj.get("points")
        if not isinstance(points, list) or not points:
            return
        top = points[-1]
        if (
            top.get("location") == 1.0
            and isinstance(top.get("value"), (int, float))
            and ALPINE_TOP_VALUE_RANGE[0] <= top["value"] <= ALPINE_TOP_VALUE_RANGE[1]
        ):
            top["value"] = round(top["value"] * ALPINE_BOOST, 6)
            count += 1

    walk(node, visit)
    return count


def add_ultra_peak_term(final_density: dict) -> dict:
    """Wraps final_density with the masked height wedge for ultra peaks."""
    cfg = ULTRA_PEAK
    mask = {
        "type": "minecraft:max",
        "left": {"type": "minecraft:constant", "value": 0.0},
        "right": {
            "type": "minecraft:mul",
            "left": {
                "type": "minecraft:add",
                "left": {
                    "type": "minecraft:noise",
                    "noise": f"{MOD_NS}:ultra_peaks",
                    "xz_scale": 1.0,
                    "y_scale": 0.0,
                },
                "right": {"type": "minecraft:constant", "value": -cfg["threshold"]},
            },
            "right": {"type": "minecraft:constant", "value": cfg["gain"]},
        },
    }
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
        "right": {"type": "minecraft:mul", "left": mask, "right": wedge},
    }


def ultra_peak_noise() -> dict:
    """The noise parameter behind the ultra-peak mask in final_density.

    Mod-defined, so there is no vanilla JSON to copy — this materializes the
    ULTRA_PEAK noise knobs that the mask references, in the 26.3 schema.
    """
    cfg = ULTRA_PEAK
    return {
        "base_amplitude": cfg["noise_base_amplitude"],
        "base_octave": cfg["noise_base_octave"],
        "octave_count": 1,
        "amplitude_modifiers": [1.0],
    }


def collect_noise_ids(node, into: set, namespace: str = "minecraft") -> None:
    prefix = f"{namespace}:"

    def visit(obj: dict) -> None:
        noise = obj.get("noise")
        if isinstance(noise, str) and noise.startswith(prefix):
            into.add(noise.removeprefix(prefix))

    walk(node, visit)


def verify_noise_references() -> None:
    """Exits with an error if a mod-namespaced noise reference has no JSON.

    A dangling reference parses fine but crashes the game at registry freeze
    with "Unbound values in registry minecraft:worldgen/noise".
    """
    referenced: set[str] = set()
    for path in sorted(OUTPUT_ROOT.rglob("*.json")):
        collect_noise_ids(json.loads(path.read_text()), referenced, MOD_NS)

    missing = [
        noise_id
        for noise_id in sorted(referenced)
        if not (OUTPUT_ROOT / "noise" / f"{noise_id}.json").exists()
    ]
    if missing:
        sys.exit(f"Dangling noise references (no noise/<id>.json emitted): {missing}")


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
        if ULTRA_PEAK["enabled"]:
            settings["noise_router"]["final_density"] = add_ultra_peak_term(
                settings["noise_router"]["final_density"]
            )
        write_json("noise_settings/overworld.json", settings)

        # 2. All overworld density functions, fully namespaced for consistency.
        #    The offset spline carries the terrain height shaping, so the
        #    alpine boost applies to it.
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
                boosted = boost_alpine_tops(data)
                print(f"Boosted {boosted} mountain-top spline values in the offset spline")
            if rel == "density_function/overworld/final_density.json" and ULTRA_PEAK["enabled"]:
                data = add_ultra_peak_term(data)
            write_json(rel, data)
        print(f"Copied {len(function_names)} density functions")

        # 3. Noise parameters: copy the ones our functions reference; the three
        #    terrain noises get tweaked, everything else stays vanilla-identical.
        #    The ultra-peak noise is mod-defined (no vanilla counterpart to copy),
        #    so it is generated from ULTRA_PEAK instead.
        referenced: set[str] = set()
        for name in function_names:
            collect_noise_ids(load_json_from_jar(jar, name.removeprefix("data/minecraft/")), referenced)
        collect_noise_ids(settings, referenced)
        retargeted = set(noise_param_overrides)
        for noise_id in sorted(referenced - retargeted):
            write_json(f"noise/{noise_id}.json", load_json_from_jar(jar, f"worldgen/noise/{noise_id}.json"))
        for noise_id, overrides in noise_param_overrides.items():
            param = load_json_from_jar(jar, f"worldgen/noise/{noise_id}.json")
            param.update(overrides)
            write_json(f"noise/{noise_id}.json", param)
        if ULTRA_PEAK["enabled"]:
            write_json("noise/ultra_peaks.json", ultra_peak_noise())
            print("Generated the ultra_peaks noise parameter")
        print(f"Copied {len(referenced - retargeted)} noise params + {len(retargeted)} tweaked")

    verify_noise_references()

    total = sum(1 for _ in OUTPUT_ROOT.rglob("*.json"))
    print(f"Wrote {total} files to {OUTPUT_ROOT}")


if __name__ == "__main__":
    main()
