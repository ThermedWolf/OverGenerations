package io.github.thermedwolf.overgenerations.client;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.thermedwolf.overgenerations.OverGenerations;
import io.github.thermedwolf.overgenerations.world.WorldGenSettings;
import io.github.thermedwolf.overgenerations.world.WorldSettingsJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Global defaults used to pre-fill the settings tab on the New World screen.
 * Stored at {@code config/overgenerations.json}. The loader entrypoints call
 * {@link #init(Path)} with their config directory during startup.
 */
public final class OverGenerationsClientConfig {

    public static final String CONFIG_FILE_NAME = "overgenerations.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Path configDirectory;

    private OverGenerationsClientConfig() {
    }

    public static void init(Path directory) {
        configDirectory = directory;
    }

    /**
     * Loads the defaults, creating the file with sensible values on first
     * launch. Never throws: an unreadable config falls back to defaults so
     * world creation always works.
     */
    public static WorldGenSettings loadDefaults() {
        Path configDirectory = OverGenerationsClientConfig.configDirectory;
        if (configDirectory == null) {
            return new WorldGenSettings();
        }
        Path file = configDirectory.resolve(CONFIG_FILE_NAME);
        if (!Files.isRegularFile(file)) {
            WorldGenSettings defaults = new WorldGenSettings();
            write(file, defaults);
            return defaults;
        }
        try {
            return WorldSettingsJson.fromJson(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        } catch (IOException | JsonParseException | IllegalStateException e) {
            OverGenerations.LOGGER.error("Ignoring unreadable config {}, using defaults", file, e);
            return new WorldGenSettings();
        }
    }

    private static void write(Path file, WorldGenSettings settings) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(WorldSettingsJson.toJson(settings)));
        } catch (IOException e) {
            OverGenerations.LOGGER.error("Failed to write config {}", file, e);
        }
    }
}
