package io.github.j12h36h.dai.logics.bootstrap;

import io.github.j12h36h.dai.content.DAI_ContentComponents;
import io.github.j12h36h.dai.api.DAI_RuntimeCapabilities;
import io.github.j12h36h.dai.entity.DAI_EntityBootstrap;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.packs.DAI_GlobalDatapackLibrary;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventRegistry;
import io.github.j12h36h.dai.registry.DAI_DynamicRegistryBootstrap;
import io.github.j12h36h.dai.server.bootstrap.DAI_ServerBootstrap;
import io.github.j12h36h.dai.worldgen.DAI_GeneratedWorldDataPack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/**
 * Physical-side-neutral bootstrap. Client-only classes are deliberately not
 * referenced here so the same DAI jar can load on dedicated servers.
 */
public final class DAI_Bootstrap {

    private DAI_Bootstrap() {}

    public static void initialize(IEventBus modBus, ModContainer container) {
        DAI_Core.LOGGER.info("<DAI>: Initializing common DAI bootstrap...");

        // DAI 1.8.2: provide a global datapack library beside resourcepacks
        // before early experience/worldgen/native-content discovery begins.
        DAI_GlobalDatapackLibrary.initialize();
        DAI_RuntimeCapabilities.refresh();

        // Compile friendly DAI world/dimension JSON into a required server-data
        // pack before Minecraft builds dynamic worldgen registries.
        DAI_GeneratedWorldDataPack.initialize(modBus);

        // Registry declarations must be known on every physical side that is
        // participating in full DAI content mode.
        DAI_DynamicRegistryBootstrap.initialize(modBus);
        if (DAI_DynamicRegistryBootstrap.hasNativeContent()) {
            DAI_EntityBootstrap.initialize(modBus);
            DAI_ContentComponents.initialize(modBus);
            DAI_Core.LOGGER.info("<DAI>: Native-content mode enabled for this JVM.");
        } else {
            DAI_Core.LOGGER.info("<DAI>: Native-content mode inactive; client-only compatibility remains available.");
        }

        DAI_ConfigBootstrap.initialize(modBus, container);
        DAI_ReactionEventRegistry.initialize();

        // Registers logical-server lifecycle hooks. On a client-only remote
        // connection these hooks simply never receive a local server.
        DAI_ServerBootstrap.initialize(modBus);

        DAI_Core.LOGGER.info("<DAI>: Common bootstrap complete.");
    }
}
