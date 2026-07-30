package io.github.j12h36h.dai.core;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(DAI.MODID)
public class DAI {
    public static final String MODID = "decisions_and_impulses";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DAI(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (Config.TOGGLE_KEYBINDS.getAsBoolean()) {
            LOGGER.info("<DAI>: TOGGLE_KEYBINDS = true");
        }
        LOGGER.info("{}{}", Config.ACTION_DELAY.get(), Config.ACTION_DELAY.getAsInt());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("<DAI>: Server Starting");
    }
}
