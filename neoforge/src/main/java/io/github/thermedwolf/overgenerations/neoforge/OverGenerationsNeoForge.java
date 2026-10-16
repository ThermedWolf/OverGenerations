package io.github.thermedwolf.overgenerations.neoforge;

import io.github.thermedwolf.overgenerations.OverGenerations;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(OverGenerations.MOD_ID)
public class OverGenerationsNeoForge {

    public OverGenerationsNeoForge(IEventBus modEventBus) {
        OverGenerations.LOGGER.info("OverGenerations initialized (NeoForge)");
    }
}
