package io.github.j12h36h.dai.logics.validation;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class DAI_ValidationListener
        extends SimplePreparableReloadListener<Boolean> {

    public DAI_ValidationListener() {
        // Reload listener.
    }

    @Override
    protected Boolean prepare(
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        /*
         * No resource parsing is needed. Validation operates on the
         * definitions registered by the earlier reload listeners.
         */
        return Boolean.TRUE;
    }

    @Override
    protected void apply(
            Boolean prepared,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {

        DAI_DatapackValidator.validate();
    }
}
