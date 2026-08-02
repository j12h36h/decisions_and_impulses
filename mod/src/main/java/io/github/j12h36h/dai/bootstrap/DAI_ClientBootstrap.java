package io.github.j12h36h.dai.bootstrap;

import io.github.j12h36h.dai.core.DAI_ClientTick;
import io.github.j12h36h.dai.core.DAI_Core;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

public final class DAI_ClientBootstrap {

    private DAI_ClientBootstrap() {
        // Utility class.
    }

    public static void initialize(
            IEventBus modBus,
            ModContainer container
    ) {

        DAI_Core.LOGGER.info(
                "<DAI>: Initializing client bootstrap..."
        );

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                ConfigurationScreen::new
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Registered configuration screen factory."
        );

        modBus.addListener(
                DAI_ClientBootstrap::initializeClient
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Registered client setup listener."
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Client bootstrap initialized."
        );
    }

    private static void initializeClient(
            FMLClientSetupEvent event
    ) {

        DAI_Core.LOGGER.info(
                "<DAI>: Performing client setup..."
        );

        NeoForge.EVENT_BUS.addListener(
                ClientTickEvent.Post.class,
                DAI_ClientBootstrap::onClientTick
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Registered post-client-tick listener."
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Client setup complete."
        );
    }

    private static void onClientTick(
            ClientTickEvent.Post event
    ) {

        DAI_ClientTick.tick();
    }
}