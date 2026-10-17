package io.github.thermedwolf.overgenerations.fabric;

import io.github.thermedwolf.overgenerations.OverGenerations;
import io.github.thermedwolf.overgenerations.client.OverGenerationsClientConfig;
import io.github.thermedwolf.overgenerations.server.OverGenerationsServerState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public class OverGenerationsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Path configDirectory = FabricLoader.getInstance().getConfigDir();
        OverGenerationsClientConfig.init(configDirectory);

        ServerLifecycleEvents.SERVER_STARTING.register(
            server -> OverGenerationsServerState.load(server.getWorldPath(LevelResource.ROOT))
        );
        ServerLifecycleEvents.SERVER_STOPPED.register(
            server -> OverGenerationsServerState.clear()
        );

        OverGenerations.LOGGER.info("OverGenerations initialized (Fabric)");
    }
}
