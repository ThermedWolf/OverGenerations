package io.github.thermedwolf.overgenerations.worldgen;

import io.github.thermedwolf.overgenerations.OverGenerations;
import io.github.thermedwolf.overgenerations.mixin.ChunkGeneratorAccessor;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;

/**
 * Builds the OverGenerations overworld generator from the data-pack-loaded
 * noise settings ({@code overgenerations:overworld}) and swaps it into the
 * world's {@link WorldGenSettings}, reusing vanilla's biome source so biome
 * distribution stays consistent with the terrain.
 *
 * <p>Runs on the client at world creation, before the server starts.</p>
 */
public final class OverworldGeneratorSwap {

    public static final ResourceKey<NoiseGeneratorSettings> OVERWORLD_SETTINGS =
        ResourceKey.create(Registries.NOISE_SETTINGS, Identifier.fromNamespaceAndPath(OverGenerations.MOD_ID, "overworld"));

    private OverworldGeneratorSwap() {
    }

    /**
     * Returns a copy of {@code genSettings} whose overworld uses the
     * OverGenerations terrain. Falls back to the input unchanged (with a
     * warning) when the settings entry or biome source is unavailable.
     */
    public static WorldGenSettings swap(LayeredRegistryAccess<RegistryLayer> registryAccess, WorldGenSettings genSettings) {
        RegistryAccess.Frozen access = registryAccess.compositeAccess();
        var settingsHolder = access.lookupOrThrow(Registries.NOISE_SETTINGS).get(OVERWORLD_SETTINGS);
        if (settingsHolder.isEmpty()) {
            OverGenerations.LOGGER.warn("OverGenerations noise settings not loaded; keeping vanilla terrain");
            return genSettings;
        }

        if (!(genSettings.dimensions().overworld() instanceof NoiseBasedChunkGenerator vanillaGenerator)) {
            OverGenerations.LOGGER.warn("Overworld generator is not noise based; keeping vanilla terrain");
            return genSettings;
        }
        // The biome source field lives on the ChunkGenerator base class;
        // the accessor targets it there and works on any subclass.
        BiomeSource biomeSource =
            ((ChunkGeneratorAccessor) (Object) vanillaGenerator).overgenerations$biomeSource();

        NoiseBasedChunkGenerator overGenerationsGenerator =
            new NoiseBasedChunkGenerator(biomeSource, settingsHolder.get());
        return new WorldGenSettings(
            genSettings.options(),
            genSettings.dimensions().replaceOverworldGenerator(access, overGenerationsGenerator)
        );
    }
}
