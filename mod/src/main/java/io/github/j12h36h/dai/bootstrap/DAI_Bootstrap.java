package io.github.j12h36h.dai.bootstrap;

import io.github.j12h36h.dai.core.DAI_Core;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

public final class DAI_Bootstrap {

    private DAI_Bootstrap() {
        // Utility class.
    }

    public static void initialize(
            IEventBus modBus,
            ModContainer container
    ) {

        DAI_Core.LOGGER.info("<DAI>: Initializing DAI...");

        DAI_Core.LOGGER.info("<DAI>: Bootstrapping configuration...");
        DAI_ConfigBootstrap.initialize(container);

        DAI_Core.LOGGER.info("<DAI>: Bootstrapping data...");
        DAI_DataBootstrap.initialize();

        DAI_Core.LOGGER.info("<DAI>: Bootstrapping client...");
        DAI_ClientBootstrap.initialize(modBus, container);

        DAI_Core.LOGGER.info("<DAI>: Bootstrapping server...");

        DAI_Core.LOGGER.info("<DAI>: Bootstrap complete.");
    }
}