package io.github.j12h36h.dai.core;

import io.github.j12h36h.dai.bootstrap.DAI_Bootstrap;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
@Mod(DAI_Core.MODID)
public final class DAI_Core {

    public static final String MODID =
            "decisions_and_impulses";

    public static final Logger LOGGER =
            LogUtils.getLogger();

    public DAI_Core(
            IEventBus modBus,
            ModContainer container
    ) {

        LOGGER.info(
                "<DAI>: Starting Decisions and Impulses..."
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