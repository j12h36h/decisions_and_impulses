package io.github.j12h36h.dai.server.bootstrap;

import io.github.j12h36h.dai.attributes.DAI_AttributeLoader;
import io.github.j12h36h.dai.animations.DAI_AnimationLoader;
import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentLoader;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationLoader;
import io.github.j12h36h.dai.logics.action.DAI_ActionLoader;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventLoader;
import io.github.j12h36h.dai.reactions.DAI_ReactionLoader;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

public final class DAI_ServerDataBootstrap {

    private DAI_ServerDataBootstrap() {
        // Utility class.
    }

    public static void initialize() {

        DAI_Core.LOGGER.info(
                "<DAI>: Initializing data bootstrap..."
        );

        NeoForge.EVENT_BUS.addListener(
                DAI_ServerDataBootstrap::registerReloadListeners
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Data bootstrap initialized."
        );
    }

    private static void registerReloadListeners(
            AddServerReloadListenersEvent event
    ) {

        // Server-owned action definitions are needed by entity behaviors and
        // authoritative experience/content systems. Client automation loads
        // its own local copy through DAI_ClientDataBootstrap.
        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "objectives"),
                new DAI_ActionLoader("objectives/definitions", true)
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "logics"),
                new DAI_ActionLoader("logics/definitions", false)
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "dai_attributes"),
                new DAI_AttributeLoader()
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "dai_animations"),
                new DAI_AnimationLoader()
        );

        for (DAI_ContentKind kind : DAI_ContentKind.values()) {
            event.addListener(
                    Identifier.fromNamespaceAndPath(DAI_Core.MODID, kind.folder()),
                    new DAI_ContentLoader(kind)
            );
        }

        for (DAI_GameCustomizationKind kind : DAI_GameCustomizationKind.values()) {
            event.addListener(
                    Identifier.fromNamespaceAndPath(DAI_Core.MODID, kind.folder()),
                    new DAI_GameCustomizationLoader(kind)
            );
        }

        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "reaction_events"),
                new DAI_ReactionEventLoader()
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "reactions"),
                new DAI_ReactionLoader()
        );

        // Registry compatibility is authoritative server state and must be
        // evaluated even on a headless dedicated server.
        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "registry_preflight"),
                new DAI_ServerPreflightListener()
        );

        DAI_Core.LOGGER.info(
                "<DAI>: Server datapack reload listeners registered."
        );
    }
}