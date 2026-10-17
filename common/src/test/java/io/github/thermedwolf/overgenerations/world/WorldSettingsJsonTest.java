package io.github.thermedwolf.overgenerations.world;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSettingsJsonTest {

    @TempDir
    Path tempDir;

    private static JsonObject roundTrip(WorldGenSettings settings) {
        return WorldSettingsJson.toJson(WorldSettingsJson.fromJson(WorldSettingsJson.toJson(settings)));
    }

    @Test
    void roundTripPreservesAllSettings() {
        WorldGenSettings settings = new WorldGenSettings();
        settings.setEnabled(true);
        settings.setCustomTerrain(true);
        settings.setBiomeScale(7);
        settings.setStructuresMode(StructuresMode.NORMAL_ONLY);
        settings.setHideExposedOres(true);

        assertEquals(settings, WorldSettingsJson.fromJson(WorldSettingsJson.toJson(settings)));
        assertEquals(settings, WorldSettingsJson.fromJson(roundTrip(settings)));
    }

    @Test
    void missingKeysFallBackToDefaults() {
        WorldGenSettings settings = WorldSettingsJson.fromJson(new JsonObject());
        assertEquals(new WorldGenSettings(), settings);
    }

    @Test
    void biomeScaleIsClampedOnRead() {
        JsonObject json = new JsonObject();
        json.addProperty("biomeScale", 999);
        assertEquals(WorldGenSettings.MAX_BIOME_SCALE, WorldSettingsJson.fromJson(json).getBiomeScale());

        json.addProperty("biomeScale", -3);
        assertEquals(WorldGenSettings.MIN_BIOME_SCALE, WorldSettingsJson.fromJson(json).getBiomeScale());
    }

    @Test
    void unknownStructuresModeFallsBackToAll() {
        JsonObject json = new JsonObject();
        json.addProperty("structuresMode", "SOMETHING_NEW");
        assertEquals(StructuresMode.ALL, WorldSettingsJson.fromJson(json).getStructuresMode());
    }

    @Test
    void storeRoundTripsThroughDisk() throws Exception {
        WorldGenSettings settings = new WorldGenSettings();
        settings.setBiomeScale(20);
        settings.setStructuresMode(StructuresMode.OFF);

        WorldSettingsStore.save(tempDir, settings);
        Optional<WorldGenSettings> loaded = WorldSettingsStore.load(tempDir);

        assertTrue(loaded.isPresent());
        assertEquals(settings, loaded.get());
        assertTrue(Files.isRegularFile(WorldSettingsStore.resolvePath(tempDir)));
    }

    @Test
    void missingFileYieldsEmpty() {
        assertTrue(WorldSettingsStore.load(tempDir).isEmpty());
    }

    @Test
    void corruptFileYieldsEmptyInsteadOfThrowing() throws Exception {
        Path file = WorldSettingsStore.resolvePath(tempDir);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ this is not json");

        assertTrue(WorldSettingsStore.load(tempDir).isEmpty());
    }

    @Test
    void biomeScaleSetterClamps() {
        WorldGenSettings settings = new WorldGenSettings();
        settings.setBiomeScale(0);
        assertEquals(1, settings.getBiomeScale());
        settings.setBiomeScale(100);
        assertEquals(20, settings.getBiomeScale());
        settings.setBiomeScale(2);
        assertEquals(2, settings.getBiomeScale());
    }
}
