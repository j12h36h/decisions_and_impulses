package io.github.j12h36h.dai.bootstrap;

import io.github.j12h36h.dai.action.DAI_ActionBootstrap;
import io.github.j12h36h.dai.condition.DAI_ConditionBootstrap;
import io.github.j12h36h.dai.action.DAI_ActionLoader;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.system.DAI_SystemLoader;
import io.github.j12h36h.dai.ui.DAI_MenuCategory;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

public final class DAI_DataBootstrap {

    private DAI_DataBootstrap() {
        // Utility class.
    }

    public static void initialize() {

        DAI_Core.LOGGER.info(
                "<DAI>: Initializing data bootstrap..."
        );

        DAI_ConditionBootstrap.initialize();
        DAI_ActionBootstrap.initialize();

        NeoForge.EVENT_BUS.addListener(
                DAI_DataBootstrap::registerReloadListeners
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Registered server reload-listener handler."
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Data bootstrap initialized."
        );
    }

    private static void registerReloadListeners(
            AddServerReloadListenersEvent event
    ) {

        DAI_Core.LOGGER.info(
                "<DAI>: Registering datapack reload listeners..."
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "actions"
                ),
                new DAI_ActionLoader("actions")
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "systems"
                ),
                new DAI_SystemLoader(
                        "systems",
                        DAI_MenuCategory.SYSTEM
                )
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "impulses"
                ),
                new DAI_SystemLoader(
                        "impulses",
                        DAI_MenuCategory.IMPULSE
                )
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "decisions"
                ),
                new DAI_SystemLoader(
                        "decisions",
                        DAI_MenuCategory.DECISION
                )
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Registered 4 datapack reload listeners."
        );
    }
}