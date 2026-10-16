package io.github.thermedwolf.overgenerations.fabric;

import io.github.thermedwolf.overgenerations.OverGenerations;
import net.fabricmc.api.ModInitializer;

public class OverGenerationsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        OverGenerations.LOGGER.info("OverGenerations initialized (Fabric)");
    }
}
