package io.github.thermedwolf.overgenerations.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.thermedwolf.overgenerations.OverGenerations;
import io.github.thermedwolf.overgenerations.client.OverGenerationsClientConfig;
import io.github.thermedwolf.overgenerations.client.OverGenerationsTab;
import io.github.thermedwolf.overgenerations.world.WorldSettingsStore;
import io.github.thermedwolf.overgenerations.worldgen.OverworldGeneratorSwap;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/**
 * Adds the OverGenerations tab to the New World screen and persists the
 * chosen settings into the world folder at creation time.
 *
 * <p>Injected between world directory creation and server start, so the
 * settings file is always readable when the server boots.</p>
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {

    @Unique
    private final io.github.thermedwolf.overgenerations.world.WorldGenSettings overgenerations$settings =
        OverGenerationsClientConfig.loadDefaults();

    @Unique
    public io.github.thermedwolf.overgenerations.world.WorldGenSettings overgenerations$getSettings() {
        return overgenerations$settings;
    }

    @WrapOperation(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/tabs/MenuTabBar$Builder;addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)Lnet/minecraft/client/gui/components/tabs/MenuTabBar$Builder;"
        )
    )
    private MenuTabBar.Builder overgenerations$addSettingsTab(
        MenuTabBar.Builder instance, Tab[] tabs, Operation<MenuTabBar.Builder> original
    ) {
        Tab[] withSettingsTab = Arrays.copyOf(tabs, tabs.length + 1);
        withSettingsTab[tabs.length] = new OverGenerationsTab(overgenerations$settings);
        return original.call(instance, withSettingsTab);
    }

    @WrapOperation(
        method = "createNewWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/worldselection/WorldOpenFlows;createLevelFromExistingSettings(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/server/ReloadableServerResources;Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/world/level/storage/LevelDataAndDimensions$WorldDataAndGenSettings;Ljava/util/Optional;)V"
        )
    )
    private void overgenerations$persistSettings(
        WorldOpenFlows instance,
        LevelStorageSource.LevelStorageAccess levelSourceAccess,
        ReloadableServerResources serverResources,
        LayeredRegistryAccess<RegistryLayer> registryAccess,
        LevelDataAndDimensions.WorldDataAndGenSettings worldDataAndGenSettings,
        Optional<GameRules> gameRules,
        Operation<Void> original
    ) {
        WorldGenSettings effectiveSettings = worldDataAndGenSettings.genSettings();
        if (overgenerations$shouldSwapTerrain()) {
            effectiveSettings = OverworldGeneratorSwap.swap(registryAccess, effectiveSettings);
            OverGenerations.LOGGER.info("Using OverGenerations terrain for this world");
        }
        try {
            Path worldRoot = levelSourceAccess.getLevelPath(LevelResource.ROOT);
            WorldSettingsStore.save(worldRoot, overgenerations$settings);
            OverGenerations.LOGGER.info("Saved OverGenerations settings for new world: {}", overgenerations$settings);
        } catch (IOException e) {
            // The world is still created; it just runs with the mod inert.
            OverGenerations.LOGGER.error("Failed to save OverGenerations settings, mod will stay inactive", e);
        }
        original.call(
            instance,
            levelSourceAccess,
            serverResources,
            registryAccess,
            new LevelDataAndDimensions.WorldDataAndGenSettings(worldDataAndGenSettings.data(), effectiveSettings),
            gameRules
        );
    }

    @Unique
    private boolean overgenerations$shouldSwapTerrain() {
        if (!overgenerations$settings.isEnabled() || !overgenerations$settings.isCustomTerrain()) {
            return false;
        }
        // Only take over the default world type; flat, large biomes, amplified
        // and debug worlds keep the generator the player explicitly chose.
        CreateWorldScreen self = (CreateWorldScreen) (Object) this;
        return self.getUiState().getWorldType().preset().unwrapKey()
            .map(key -> key.equals(WorldPresets.NORMAL))
            .orElse(false);
    }
}
