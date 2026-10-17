package io.github.thermedwolf.overgenerations.world;

import com.google.gson.JsonObject;
import io.github.thermedwolf.overgenerations.OverGenerations;

/**
 * Gson codec for {@link WorldGenSettings}. Reads defensively: unknown or
 * missing keys fall back to defaults, out-of-range values are clamped, and
 * unknown enum names fall back to {@link StructuresMode#ALL}.
 */
public final class WorldSettingsJson {

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_CUSTOM_TERRAIN = "customTerrain";
    private static final String KEY_BIOME_SCALE = "biomeScale";
    private static final String KEY_STRUCTURES_MODE = "structuresMode";
    private static final String KEY_HIDE_EXPOSED_ORES = "hideExposedOres";

    private WorldSettingsJson() {
    }

    public static JsonObject toJson(WorldGenSettings settings) {
        JsonObject json = new JsonObject();
        json.addProperty(KEY_ENABLED, settings.isEnabled());
        json.addProperty(KEY_CUSTOM_TERRAIN, settings.isCustomTerrain());
        json.addProperty(KEY_BIOME_SCALE, settings.getBiomeScale());
        json.addProperty(KEY_STRUCTURES_MODE, settings.getStructuresMode().name());
        json.addProperty(KEY_HIDE_EXPOSED_ORES, settings.shouldHideExposedOres());
        return json;
    }

    public static WorldGenSettings fromJson(JsonObject json) {
        WorldGenSettings settings = new WorldGenSettings();
        if (json.has(KEY_ENABLED)) {
            settings.setEnabled(json.get(KEY_ENABLED).getAsBoolean());
        }
        if (json.has(KEY_CUSTOM_TERRAIN)) {
            settings.setCustomTerrain(json.get(KEY_CUSTOM_TERRAIN).getAsBoolean());
        }
        if (json.has(KEY_BIOME_SCALE)) {
            settings.setBiomeScale(json.get(KEY_BIOME_SCALE).getAsInt());
        }
        if (json.has(KEY_STRUCTURES_MODE)) {
            settings.setStructuresMode(readStructuresMode(json));
        }
        if (json.has(KEY_HIDE_EXPOSED_ORES)) {
            settings.setHideExposedOres(json.get(KEY_HIDE_EXPOSED_ORES).getAsBoolean());
        }
        return settings;
    }

    private static StructuresMode readStructuresMode(JsonObject json) {
        String raw = json.get(KEY_STRUCTURES_MODE).getAsString();
        try {
            return StructuresMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            OverGenerations.LOGGER.warn("Unknown structures mode '{}', falling back to ALL", raw);
            return StructuresMode.ALL;
        }
    }
}
