package io.github.j12h36h.dai.client.logics.validation;

import io.github.j12h36h.dai.client.animations.DAI_AnimationRuntime;
import io.github.j12h36h.dai.client.content.DAI_ContentRuntime;
import io.github.j12h36h.dai.client.title.DAI_TitleScreenRepository;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Final physical-client DAI reload pass.
 *
 * The definition loaders run first, then validation and live-runtime rebinding
 * happen here so /reload changes are visible without restarting the client.
 */
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

        // Existing live objects must not retain stale copies of definitions.
        DAI_ContentRuntime.rebindReloadedDefinitions();
        DAI_AnimationRuntime.rebindReloadedDefinitions();

        // Title/experience discovery happens before a world normally exists,
        // so its repository is not a ResourceManager listener. Refresh its
        // cache as part of the client hot-reload completion pass.
        DAI_TitleScreenRepository.reload();

        DAI_Core.LOGGER.info(
                "<DAI>: Client hot reload complete; runtime definitions are live."
        );
    }
}
