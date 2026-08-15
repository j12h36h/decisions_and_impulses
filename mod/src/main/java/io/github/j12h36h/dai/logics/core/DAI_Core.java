package io.github.j12h36h.dai.logics.core;

import io.github.j12h36h.dai.logics.bootstrap.DAI_Bootstrap;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
@Mod(DAI_Core.MODID)
public final class DAI_Core {

    public static final String MODID =
            "decisions_and_impulses";

    /** Data/API feature level represented by this source tree. */
    public static final String FEATURE_LEVEL =
            "1.9";

    public static final Logger LOGGER =
            LogUtils.getLogger();

    /**
     * DAI-owned debug logging is gated by the common debugging config so a
     * release user can disable verbose diagnostics without suppressing normal
     * INFO/WARN/ERROR messages from the mod or Minecraft.
     */
    public static void debug(
            String message,
            Object... arguments
    ) {

        if (!DAI_Config.isDebuggingEnabled()) {
            return;
        }

        LOGGER.debug(
                message,
                arguments
        );
    }

    public DAI_Core(
            IEventBus modBus,
            ModContainer container
    ) {

        LOGGER.info(
                "<DAI>: Starting Decisions and Impulses feature level {}...",
                FEATURE_LEVEL
        );

        try {

            DAI_Bootstrap.initialize(
                    modBus,
                    container
            );

            LOGGER.info(
                    "<DAI>: Decisions and Impulses initialized successfully."
            );

        } catch (Exception exception) {

            LOGGER.error(
                    "<DAI>: Failed to initialize Decisions and Impulses.",
                    exception
            );

            throw exception;
        }
    }
}