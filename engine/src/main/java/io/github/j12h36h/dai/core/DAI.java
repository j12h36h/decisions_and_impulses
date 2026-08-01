package io.github.j12h36h.dai.core;

import io.github.j12h36h.dai.action.DAI_ActionLoader;
import io.github.j12h36h.dai.ui.DAI_MenuCategory;
import io.github.j12h36h.dai.util.DAI_SystemLoader;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
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

    @SubscribeEvent
    public void registerReloadListeners(AddServerReloadListenersEvent event) {
        LOGGER.info("<DAI>: Registering Reload Listeners");

        event.addListener(
                Identifier.fromNamespaceAndPath("decisions_and_impulses", "actions"),
                new DAI_ActionLoader(
                        "actions"
                )
        );

        event.addListener(
                Identifier.fromNamespaceAndPath("decisions_and_impulses", "systems"),
                new DAI_SystemLoader(
                        "systems",
                        DAI_MenuCategory.SYSTEM
                )
        );

        event.addListener(
                Identifier.fromNamespaceAndPath("decisions_and_impulses", "impulses"),
                new DAI_SystemLoader(
                        "impulses",
                        DAI_MenuCategory.IMPULSE
                )
        );

        event.addListener(
                Identifier.fromNamespaceAndPath("decisions_and_impulses", "decisions"),
                new DAI_SystemLoader(
                        "decisions",
                        DAI_MenuCategory.DECISION
                )
        );
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
