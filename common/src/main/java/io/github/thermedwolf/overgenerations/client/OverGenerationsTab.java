package io.github.thermedwolf.overgenerations.client;

import io.github.thermedwolf.overgenerations.world.StructuresMode;
import io.github.thermedwolf.overgenerations.world.WorldGenSettings;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The "OverGenerations" tab on the New World screen. Follows the exact widget
 * pattern of the vanilla Game/World/More tabs so it blends in natively.
 */
public class OverGenerationsTab extends GridLayoutTab {

    private static final Component TITLE = Component.translatable("overgenerations.tab.title");
    private static final int WIDGET_WIDTH = 210;
    private static final int WIDGET_HEIGHT = 20;

    private final WorldGenSettings settings;

    public OverGenerationsTab(WorldGenSettings settings) {
        super(TITLE);
        this.settings = settings;

        var helper = this.layout.rowSpacing(8).createRowHelper(1);

        helper.addChild(toggle(
            "enabled", settings.isEnabled(), settings::setEnabled
        ));
        helper.addChild(toggle(
            "customTerrain", settings.isCustomTerrain(), settings::setCustomTerrain
        ));
        helper.addChild(new BiomeSizeSlider(settings));
        helper.addChild(
            CycleButton.<StructuresMode>builder(OverGenerationsTab::structuresModeLabel, settings.getStructuresMode())
                .withValues(StructuresMode.values())
                .create(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, label("structures"),
                    (button, value) -> settings.setStructuresMode(value))
        );
        helper.addChild(toggle(
            "hideExposedOres", settings.shouldHideExposedOres(), settings::setHideExposedOres
        ));
    }

    private CycleButton<Boolean> toggle(String key, boolean initialValue, java.util.function.Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(initialValue)
            .create(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, label(key),
                (button, value) -> setter.accept(value));
    }

    private static Component label(String key) {
        return Component.translatable("overgenerations.setting." + key);
    }

    private static Component structuresModeLabel(StructuresMode mode) {
        return Component.translatable("overgenerations.structures." + mode.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Maps the 1-20 biome scale onto a vanilla-style slider. Terrain height is
     * never touched: only the horizontal biome scale is stored.
     */
    private static final class BiomeSizeSlider extends AbstractSliderButton {

        private final WorldGenSettings settings;

        private BiomeSizeSlider(WorldGenSettings settings) {
            super(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(), toSliderValue(settings.getBiomeScale()));
            this.settings = settings;
            updateMessage();
        }

        private static double toSliderValue(int scale) {
            int min = WorldGenSettings.MIN_BIOME_SCALE;
            int max = WorldGenSettings.MAX_BIOME_SCALE;
            return (scale - min) / (double) (max - min);
        }

        private int sliderScale() {
            int min = WorldGenSettings.MIN_BIOME_SCALE;
            int max = WorldGenSettings.MAX_BIOME_SCALE;
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("overgenerations.setting.biomeSize.value", sliderScale()));
        }

        @Override
        protected void applyValue() {
            settings.setBiomeScale(sliderScale());
        }
    }
}
