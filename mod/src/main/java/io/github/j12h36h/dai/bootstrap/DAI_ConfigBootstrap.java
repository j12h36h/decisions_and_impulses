package io.github.j12h36h.dai.bootstrap;

import io.github.j12h36h.dai.core.DAI_Config;
import io.github.j12h36h.dai.core.DAI_Core;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

public final class DAI_ConfigBootstrap {

    private DAI_ConfigBootstrap() {
        // Utility class.
    }

    public static void initialize(ModContainer container) {

        DAI_Core.LOGGER.info(
                "<DAI>: Initializing configuration..."
        );

        container.registerConfig(
                ModConfig.Type.COMMON,
                DAI_Config.SPEC
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Registered common configuration."
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Configuration initialized."
        );
    }
}