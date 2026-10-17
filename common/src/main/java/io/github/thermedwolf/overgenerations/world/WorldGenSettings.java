package io.github.thermedwolf.overgenerations.world;

/**
 * Per-world OverGenerations settings, chosen on the "New World" screen and
 * stored inside the world folder. Terrain and biome scale are baked in at
 * world creation; the structures mode can be changed on existing worlds.
 *
 * <p>Pure Java: kept free of Minecraft classes so both loaders share it as-is.</p>
 */
public final class WorldGenSettings {

    public static final int MIN_BIOME_SCALE = 1;
    public static final int MAX_BIOME_SCALE = 20;
    public static final int DEFAULT_BIOME_SCALE = 2;

    private boolean enabled = true;
    private boolean customTerrain = true;
    private int biomeScale = DEFAULT_BIOME_SCALE;
    private StructuresMode structuresMode = StructuresMode.ALL;
    private boolean hideExposedOres = false;

    /**
     * Master switch. When off, the mod is completely inert for this world:
     * vanilla terrain, vanilla biomes, vanilla structures.
     */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * When on, the vanilla overworld generator is replaced by the
     * OverGenerations terrain (mountains, valleys, smooth transitions).
     * When off, terrain is vanilla but the mod may still add structures.
     */
    public boolean isCustomTerrain() {
        return customTerrain;
    }

    public void setCustomTerrain(boolean customTerrain) {
        this.customTerrain = customTerrain;
    }

    /**
     * Horizontal biome size multiplier (1-20, default 2). Only scales
     * length and width; terrain height is never affected.
     */
    public int getBiomeScale() {
        return biomeScale;
    }

    public void setBiomeScale(int biomeScale) {
        this.biomeScale = clampBiomeScale(biomeScale);
    }

    public StructuresMode getStructuresMode() {
        return structuresMode;
    }

    public void setStructuresMode(StructuresMode structuresMode) {
        this.structuresMode = structuresMode == null ? StructuresMode.ALL : structuresMode;
    }

    /**
     * When on, ores never generate exposed to air: players must dig into
     * mountains to find them.
     */
    public boolean shouldHideExposedOres() {
        return hideExposedOres;
    }

    public void setHideExposedOres(boolean hideExposedOres) {
        this.hideExposedOres = hideExposedOres;
    }

    public static int clampBiomeScale(int biomeScale) {
        return Math.clamp(biomeScale, MIN_BIOME_SCALE, MAX_BIOME_SCALE);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof WorldGenSettings other
            && enabled == other.enabled
            && customTerrain == other.customTerrain
            && biomeScale == other.biomeScale
            && structuresMode == other.structuresMode
            && hideExposedOres == other.hideExposedOres;
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(enabled);
        result = 31 * result + Boolean.hashCode(customTerrain);
        result = 31 * result + biomeScale;
        result = 31 * result + structuresMode.hashCode();
        result = 31 * result + Boolean.hashCode(hideExposedOres);
        return result;
    }

    @Override
    public String toString() {
        return "WorldGenSettings[enabled=%s, customTerrain=%s, biomeScale=%dx, structuresMode=%s, hideExposedOres=%s]"
            .formatted(enabled, customTerrain, biomeScale, structuresMode, hideExposedOres);
    }
}
