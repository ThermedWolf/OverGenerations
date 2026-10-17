package io.github.thermedwolf.overgenerations.mixin;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the vanilla generator's biome source so the OverGenerations
 * generator can reuse it (same biome distribution, new terrain).
 */
@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseBasedChunkGeneratorMixin {

    @Accessor("biomeSource")
    BiomeSource overgenerations$biomeSource();
}
