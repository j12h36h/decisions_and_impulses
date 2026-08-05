package io.github.j12h36h.dai.bootstrap;

import io.github.j12h36h.dai.action.DAI_ActionBootstrap;
import io.github.j12h36h.dai.condition.DAI_ConditionBootstrap;
import io.github.j12h36h.dai.action.DAI_ActionLoader;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.recognition.DAI_RecogGroupLoader;
import io.github.j12h36h.dai.recognition.DAI_RecogLoader;
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
        DAI_RecogBootstrap.initialize();

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

    public static void registerReloadListeners(
            AddServerReloadListenersEvent event
    ) {

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "recognition_groups"
                ),
                new DAI_RecogGroupLoader()
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "sequences"
                ),
                new DAI_ActionLoader("sequences")
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
                        "actions"
                ),
                new DAI_SystemLoader(
                        "actions",
                        DAI_MenuCategory.ACTION
                )
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "recognition"
                ),
                new DAI_RecogLoader()
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Registered 4 datapack reload listeners."
        );
    }
}