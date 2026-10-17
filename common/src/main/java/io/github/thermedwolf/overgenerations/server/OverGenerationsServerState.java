package io.github.thermedwolf.overgenerations.server;

import io.github.thermedwolf.overgenerations.OverGenerations;
import io.github.thermedwolf.overgenerations.world.WorldGenSettings;
import io.github.thermedwolf.overgenerations.world.WorldSettingsStore;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Holds the settings of the world the running server belongs to. Populated by
 * the loader-specific server lifecycle hooks; absent or disabled settings mean
 * the mod stays completely inert.
 */
public final class OverGenerationsServerState {

    private static volatile WorldGenSettings active;

    private OverGenerationsServerState() {
    }

    public static void load(Path worldRoot) {
        WorldGenSettings loaded = WorldSettingsStore.load(worldRoot).orElse(null);
        active = loaded;
        if (loaded == null || !loaded.isEnabled()) {
            OverGenerations.LOGGER.info("OverGenerations is inactive for this world (no settings or disabled)");
        } else {
            OverGenerations.LOGGER.info("OverGenerations settings: {}", loaded);
        }
    }

    public static void clear() {
        active = null;
    }

    /**
     * The active settings, or empty when the world is not managed by
     * OverGenerations (file missing, corrupt, or master toggle off).
     */
    public static Optional<WorldGenSettings> get() {
        WorldGenSettings settings = active;
        return settings != null && settings.isEnabled() ? Optional.of(settings) : Optional.empty();
    }
}
