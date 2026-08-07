package io.github.j12h36h.dai.bootstrap;

import io.github.j12h36h.dai.action.DAI_ActionLoader;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.recognition.DAI_RecogGroupLoader;
import io.github.j12h36h.dai.recognition.DAI_RecogLoader;
import io.github.j12h36h.dai.system.DAI_SystemLoader;
import io.github.j12h36h.dai.ui.DAI_MenuCategory;
import io.github.j12h36h.dai.validation.DAI_ValidationListener;
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

        NeoForge.EVENT_BUS.addListener(
                DAI_DataBootstrap::registerReloadListeners
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Data bootstrap initialized."
        );
    }

    private static void registerReloadListeners(
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
                new DAI_ActionLoader(
                        "sequences"
                )
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

        /*
         * This must remain last. It validates the final state created
         * by every preceding datapack reload listener.
         */
        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "validation"
                ),
                new DAI_ValidationListener()
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Datapack reload listeners registered."
        );
    }
}