package io.github.j12h36h.dai.logics.bootstrap;

import io.github.j12h36h.dai.content.DAI_ContentComponents;
import io.github.j12h36h.dai.api.DAI_RuntimeCapabilities;
import io.github.j12h36h.dai.entity.DAI_EntityBootstrap;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.packs.DAI_GlobalDatapackLibrary;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventRegistry;
import io.github.j12h36h.dai.registry.DAI_DynamicRegistryBootstrap;
import io.github.j12h36h.dai.server.bootstrap.DAI_ServerBootstrap;
import io.github.j12h36h.dai.worldgen.DAI_GeneratedWorldDataPack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/** Physical-side-neutral DAI bootstrap. */
public final class DAI_Bootstrap {

    private DAI_Bootstrap() {}

    public static void initialize(IEventBus modBus, ModContainer container) {
        DAI_Core.LOGGER.info("<DAI>: Initializing common DAI bootstrap...");

        /* Register config first. DAI_Config can also read the explicit common
         * config file early, so startup/registry modules can be omitted before
         * their normal NeoForge config Loading event fires. */
        DAI_ConfigBootstrap.initialize(modBus, container);

        if (DAI_Config.featureModuleEnabled("managed_packs")) {
            DAI_GlobalDatapackLibrary.initialize();
        } else {
            disabled("managed_packs");
        }

        DAI_RuntimeCapabilities.refresh();

        if (DAI_Config.featureModuleEnabled("worldgen")) {
            DAI_GeneratedWorldDataPack.initialize(modBus);
        } else {
            disabled("worldgen");
        }

        boolean nativeRuntimeNeeded = anyEnabled(
                "content", "entities", "blocks", "items", "vehicles", "projectiles",
                "effects", "audio", "fluids", "interactive", "portals", "worldgen"
        );

        if (nativeRuntimeNeeded) {
            DAI_DynamicRegistryBootstrap.initialize(modBus);

            if (DAI_DynamicRegistryBootstrap.hasNativeContent()) {
                if (DAI_Config.featureModuleEnabled("entities")) {
                    DAI_EntityBootstrap.initialize(modBus);
                }

                if (anyEnabled(
                        "content", "entities", "blocks", "items", "vehicles", "projectiles",
                        "effects", "audio", "fluids", "interactive", "portals"
                )) {
                    DAI_ContentComponents.initialize(modBus);
                }

                DAI_Core.LOGGER.info("<DAI>: Native-content mode enabled for this JVM.");
            } else {
                DAI_Core.LOGGER.info("<DAI>: Native-content mode inactive; client-only compatibility remains available.");
            }
        } else {
            DAI_Core.LOGGER.info("<DAI>: Native-content registry bootstrap skipped; all native-content modules are disabled.");
        }

        if (DAI_Config.featureModuleEnabled("reactions")) {
            DAI_ReactionEventRegistry.initialize();
        } else {
            disabled("reactions");
        }

        /* Logical-server bootstrap stays present as the small server-side
         * coordination spine. It independently gates each optional runtime. */
        DAI_ServerBootstrap.initialize(modBus);

        DAI_Core.LOGGER.info("<DAI>: Common bootstrap complete.");
    }

    private static boolean anyEnabled(String... modules) {
        if (modules == null) return false;
        for (String module : modules) {
            if (DAI_Config.featureModuleEnabled(module)) return true;
        }
        return false;
    }

    private static void disabled(String module) {
        DAI_Core.LOGGER.info("<DAI>: Feature module '{}' disabled; bootstrap skipped.", module);
    }
}
