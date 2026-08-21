package io.github.j12h36h.dai.server.bootstrap;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.registry.DAI_RegistryWorldStore;
import io.github.j12h36h.dai.server.entity.DAI_EntityRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_ItemRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_VehicleRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_ProjectileRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_EffectRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_AudioRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_FluidRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_InteractiveRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_PortalRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_BlockRuntime;
import io.github.j12h36h.dai.server.network.DAI_ServerNetworkBootstrap;
import io.github.j12h36h.dai.server.worldgen.DAI_WorldgenRuntime;
import io.github.j12h36h.dai.server.worldgen.DAI_NaturalGenerationRuntime;
import net.neoforged.bus.api.IEventBus;

/**
 * Logical-server bootstrap. This class contains no net.minecraft.client
 * references and is safe on both an integrated server and a dedicated server.
 */
public final class DAI_ServerBootstrap {

    private static boolean initialized;

    private DAI_ServerBootstrap() {}

    public static synchronized void initialize(IEventBus modBus) {
        if (initialized) return;
        initialized = true;

        DAI_RegistryWorldStore.initialize();
        DAI_WorldgenRuntime.initialize();
        DAI_NaturalGenerationRuntime.initialize();
        DAI_EntityRuntime.initialize();
        DAI_BlockRuntime.initialize();
        DAI_ItemRuntime.initialize();
        DAI_VehicleRuntime.initialize();
        DAI_ProjectileRuntime.initialize();
        DAI_EffectRuntime.initialize();
        DAI_AudioRuntime.initialize();
        DAI_FluidRuntime.initialize();
        DAI_InteractiveRuntime.initialize();
        DAI_PortalRuntime.initialize();
        DAI_ServerNetworkBootstrap.initialize(modBus);
        DAI_ServerDataBootstrap.initialize();

        DAI_Core.LOGGER.info("<DAI>: Server bootstrap initialized.");
    }
}
