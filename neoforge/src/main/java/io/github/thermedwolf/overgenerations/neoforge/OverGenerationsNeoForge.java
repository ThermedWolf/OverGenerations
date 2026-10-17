package io.github.thermedwolf.overgenerations.neoforge;

import io.github.thermedwolf.overgenerations.OverGenerations;
import io.github.thermedwolf.overgenerations.client.OverGenerationsClientConfig;
import io.github.thermedwolf.overgenerations.server.OverGenerationsServerState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod(OverGenerations.MOD_ID)
public class OverGenerationsNeoForge {

    public OverGenerationsNeoForge(IEventBus modEventBus) {
        OverGenerationsClientConfig.init(FMLPaths.CONFIGDIR.get());

        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);

        OverGenerations.LOGGER.info("OverGenerations initialized (NeoForge)");
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        OverGenerationsServerState.load(event.getServer().getWorldPath(LevelResource.ROOT));
    }

    private void onServerStopped(ServerStoppedEvent event) {
        OverGenerationsServerState.clear();
    }
}
