package io.github.j12h36h.dai.server.bootstrap;

import io.github.j12h36h.dai.registry.DAI_RegistryPreflight;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Dedicated-safe registry preflight pass that runs after server DAI data reloads. */
public final class DAI_ServerPreflightListener
        extends SimplePreparableReloadListener<Boolean> {

    @Override
    protected Boolean prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return Boolean.TRUE;
    }

    @Override
    protected void apply(Boolean prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        DAI_RegistryPreflight.evaluate();
    }
}
