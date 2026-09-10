package io.github.j12h36h.dai.server.bootstrap;

import io.github.j12h36h.dai.logics.core.DAI_Config;
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
import io.github.j12h36h.dai.server.runtime.DAI_PhysicsRuntime;
import io.github.j12h36h.dai.server.creator.DAI_CreatorServerRuntime;
import io.github.j12h36h.dai.server.network.DAI_ServerNetworkBootstrap;
import io.github.j12h36h.dai.server.state.DAI_ServerStateRuntime;
import io.github.j12h36h.dai.server.worldgen.DAI_WorldgenRuntime;
import io.github.j12h36h.dai.server.worldgen.DAI_NaturalGenerationRuntime;
import net.neoforged.bus.api.IEventBus;

/** Logical-server bootstrap with optional feature-module gating. */
public final class DAI_ServerBootstrap {

    private static boolean initialized;

    private DAI_ServerBootstrap() {}

    public static synchronized void initialize(IEventBus modBus) {
        if (initialized) return;
        initialized = true;

        /* Core world-store/data/network plumbing remains available so DAI can
         * safely load and report pack definitions even when gameplay modules
         * are selectively disabled. */
        DAI_RegistryWorldStore.initialize();

        if (enabled("worldgen")) {
            DAI_WorldgenRuntime.initialize();
            DAI_NaturalGenerationRuntime.initialize();
        }
        if (enabled("entities")) DAI_EntityRuntime.initialize();
        if (enabled("blocks")) DAI_BlockRuntime.initialize();
        if (enabled("items")) DAI_ItemRuntime.initialize();
        if (enabled("vehicles")) DAI_VehicleRuntime.initialize();
        if (enabled("projectiles")) DAI_ProjectileRuntime.initialize();
        if (enabled("effects")) DAI_EffectRuntime.initialize();
        if (enabled("audio")) DAI_AudioRuntime.initialize();
        if (enabled("fluids")) DAI_FluidRuntime.initialize();
        if (enabled("interactive") && enabled("interaction")) DAI_InteractiveRuntime.initialize();
        if (enabled("portals")) DAI_PortalRuntime.initialize();
        if (enabled("creator")) DAI_CreatorServerRuntime.initialize();
        if (enabled("physics")) DAI_PhysicsRuntime.initialize();
        if (enabled("state")) DAI_ServerStateRuntime.initialize();

        DAI_ServerNetworkBootstrap.initialize(modBus);
        DAI_ServerDataBootstrap.initialize();

        DAI_Core.LOGGER.info("<DAI>: Server bootstrap initialized with feature-module gating.");
    }

    private static boolean enabled(String id) {
        boolean enabled = DAI_Config.featureModuleEnabled(id);
        if (!enabled) DAI_Core.LOGGER.info("<DAI>: Server feature module '{}' disabled; runtime registration skipped.", id);
        return enabled;
    }
}
