package io.github.j12h36h.dai.logics.bootstrap;

import io.github.j12h36h.dai.logics.creation.DAI_RecipeLoader;
import io.github.j12h36h.dai.attributes.DAI_AttributeLoader;
import io.github.j12h36h.dai.animations.DAI_AnimationLoader;
import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentLoader;
import io.github.j12h36h.dai.logics.action.DAI_ActionLoader;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.validation.DAI_ValidationListener;
import io.github.j12h36h.dai.menus.DAI_MenuCategory;
import io.github.j12h36h.dai.menus.DAI_ScreenProfileLoader;
import io.github.j12h36h.dai.menus.system.DAI_SystemLoader;
import io.github.j12h36h.dai.objectives.recognition.DAI_RecogGroupLoader;
import io.github.j12h36h.dai.objectives.recognition.DAI_RecogLoader;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventLoader;
import io.github.j12h36h.dai.reactions.DAI_ReactionLoader;
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

        /*
         * ------------------------------------------------------------
         * RECOGNITION GROUPS
         * ------------------------------------------------------------
         */

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "recognition_groups"
                ),
                new DAI_RecogGroupLoader()
        );

        /*
         * ------------------------------------------------------------
         * OBJECTIVES
         * ------------------------------------------------------------
         */

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "objectives"
                ),
                new DAI_ActionLoader(
                        "objectives/definitions",
                        true
                )
        );

        /*
         * ------------------------------------------------------------
         * LOGICS
         * ------------------------------------------------------------
         */

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "logics"
                ),
                new DAI_ActionLoader(
                        "logics/definitions",
                        false
                )
        );

        /*
         * ------------------------------------------------------------
         * MENU SYSTEMS
         * ------------------------------------------------------------
         */

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "systems"
                ),
                new DAI_SystemLoader(
                        "menus/systems",
                        DAI_MenuCategory.SYSTEM
                )
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "actions"
                ),
                new DAI_SystemLoader(
                        "menus/actions",
                        DAI_MenuCategory.ACTION
                )
        );

        /*
         * ------------------------------------------------------------
         * RECOGNITION DEFINITIONS
         * ------------------------------------------------------------
         */

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "recognition"
                ),
                new DAI_RecogLoader()
        );

        /*
         * ------------------------------------------------------------
         * SCREEN PROFILES
         * ------------------------------------------------------------
         */

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "screen_profiles"
                ),
                new DAI_ScreenProfileLoader()
        );

        /*
         * ------------------------------------------------------------
         * PROCESSING RECIPES
         * ------------------------------------------------------------
         *
         * Loads DAI-defined deterministic crafting, smelting,
         * and custom-container processing recipes.
         *
         * These recipes provide an authoritative/fallback path when
         * vanilla or modded recipe resolution is missing, ambiguous,
         * overridden, or otherwise unsuitable for autonomous execution.
         */

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "processing_recipes"
                ),
                new DAI_RecipeLoader()
        );

        /*
         * ------------------------------------------------------------
         * DATAPACK EXTENSIONS
         * ------------------------------------------------------------
         *
         * These are ordinary DAI datapack folders. Attributes, animations,
         * and virtual content identities are loaded before reactions and the
         * final validator so action/condition references can be checked in a
         * single reload pass.
         */

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
                    Identifier.fromNamespaceAndPath(
                            DAI_Core.MODID,
                            kind.folder()
                    ),
                    new DAI_ContentLoader(kind)
            );
        }

        /*
         * ------------------------------------------------------------
         * REACTION EVENTS
         * ------------------------------------------------------------
         *
         * Third-party datapacks may declare their own event vocabulary in
         * data/<namespace>/reaction_events/*.json before reactions validate.
         */

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "reaction_events"
                ),
                new DAI_ReactionEventLoader()
        );

        /*
         * ------------------------------------------------------------
         * REACTIONS
         * ------------------------------------------------------------
         */

        event.addListener(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "reactions"
                ),
                new DAI_ReactionLoader()
        );

        /*
         * ------------------------------------------------------------
         * VALIDATION
         * ------------------------------------------------------------
         *
         * This must remain last.
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