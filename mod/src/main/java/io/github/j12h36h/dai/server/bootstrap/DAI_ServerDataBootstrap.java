package io.github.j12h36h.dai.server.bootstrap;

import io.github.j12h36h.dai.attributes.DAI_AttributeLoader;
import io.github.j12h36h.dai.animations.DAI_AnimationLoader;
import io.github.j12h36h.dai.animations.eras.DAI_ErasCinematicLoader;
import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentLoader;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationLoader;
import io.github.j12h36h.dai.logics.action.DAI_ActionLoader;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventLoader;
import io.github.j12h36h.dai.reactions.DAI_ReactionLoader;
import io.github.j12h36h.dai.state.DAI_StateLoader;
import io.github.j12h36h.dai.sync.DAI_ClientDatapackSyncLoader;
import io.github.j12h36h.dai.sync.DAI_ClientDatapackSyncRepository;
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

        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "dai_cinematics"),
                new DAI_ErasCinematicLoader(DAI_ErasCinematicLoader.Source.SERVER_DATA)
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "dai_state"),
                new DAI_StateLoader()
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

        // Raw client-visible JSON snapshots. These are side-neutral loaders so
        // a dedicated server can distribute its datapack-authored UI/events
        // without loading any net.minecraft.client classes.
        registerClientSync(event, "sync_objectives", DAI_ClientDatapackSyncRepository.OBJECTIVES, "objectives/definitions");
        registerClientSync(event, "sync_logics", DAI_ClientDatapackSyncRepository.LOGICS, "logics/definitions");
        registerClientSync(event, "sync_reaction_events", DAI_ClientDatapackSyncRepository.REACTION_EVENTS, "reaction_events");
        registerClientSync(event, "sync_reactions", DAI_ClientDatapackSyncRepository.REACTIONS, "reactions");
        registerClientSync(event, "sync_data_screens", DAI_ClientDatapackSyncRepository.DATA_SCREENS, "dai_screens");
        registerClientSync(event, "sync_screen_overrides", DAI_ClientDatapackSyncRepository.SCREEN_OVERRIDES, "screen_overrides");
        registerClientSync(event, "sync_scenes", DAI_ClientDatapackSyncRepository.SCENES, "scene_environments");
        registerClientSync(event, "sync_shell_presentations", DAI_ClientDatapackSyncRepository.SHELL_PRESENTATIONS, "dai_shell_presentations");
        registerClientSync(event, "sync_title_screens", DAI_ClientDatapackSyncRepository.TITLE_SCREENS, "dai_title_screens");

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

    private static void registerClientSync(
            AddServerReloadListenersEvent event,
            String listenerId,
            String kind,
            String folder
    ) {
        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, listenerId),
                new DAI_ClientDatapackSyncLoader(kind, folder)
        );
    }
}