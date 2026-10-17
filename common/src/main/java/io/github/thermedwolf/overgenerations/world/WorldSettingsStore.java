package io.github.thermedwolf.overgenerations.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.thermedwolf.overgenerations.OverGenerations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads and writes the per-world settings file at
 * {@code <world>/overgenerations/settings.json}.
 *
 * <p>A missing or corrupt file yields {@link Optional#empty()}: the world was
 * created before the mod existed (or its file is damaged), and in both cases
 * the mod must stay completely inert instead of crashing the world load.</p>
 */
public final class WorldSettingsStore {

    public static final String DIRECTORY_NAME = "overgenerations";
    public static final String FILE_NAME = "settings.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WorldSettingsStore() {
    }

    public static Path resolvePath(Path worldRoot) {
        return worldRoot.resolve(DIRECTORY_NAME).resolve(FILE_NAME);
    }

    public static void save(Path worldRoot, WorldGenSettings settings) throws IOException {
        Path file = resolvePath(worldRoot);
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(WorldSettingsJson.toJson(settings)));
    }

    public static Optional<WorldGenSettings> load(Path worldRoot) {
        Path file = resolvePath(worldRoot);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(file);
            WorldGenSettings settings = WorldSettingsJson.fromJson(JsonParser.parseString(content).getAsJsonObject());
            return Optional.of(settings);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            OverGenerations.LOGGER.error("Ignoring unreadable OverGenerations settings file {}", file, e);
            return Optional.empty();
        }
    }
}
