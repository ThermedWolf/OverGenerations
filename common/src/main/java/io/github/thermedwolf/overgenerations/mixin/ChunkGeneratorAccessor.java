package io.github.thermedwolf.overgenerations.mixin;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the biome source stored on the ChunkGenerator base class so the
 * OverGenerations generator can reuse it (same biome distribution, new
 * terrain).
 */
@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorAccessor {

    @Accessor("biomeSource")
    BiomeSource overgenerations$biomeSource();
}
