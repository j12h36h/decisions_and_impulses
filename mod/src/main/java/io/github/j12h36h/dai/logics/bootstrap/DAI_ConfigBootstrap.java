package io.github.j12h36h.dai.logics.bootstrap;

import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

public final class DAI_ConfigBootstrap {

    private DAI_ConfigBootstrap() {
        // Utility class.
    }

    public static void initialize(
            ModContainer container
    ) {

        container.registerConfig(
                ModConfig.Type.COMMON,
                DAI_Config.SPEC
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Registered common configuration."
        );
    }
}