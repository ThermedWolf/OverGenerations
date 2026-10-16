package io.github.thermedwolf.overgenerations.fabric.client;

import io.github.thermedwolf.overgenerations.OverGenerations;
import net.fabricmc.api.ClientModInitializer;

public class OverGenerationsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        OverGenerations.LOGGER.info("OverGenerations client initialized (Fabric)");
    }
}
