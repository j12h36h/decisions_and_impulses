package io.github.j12h36h.dai.server.bootstrap;

import io.github.j12h36h.dai.experience.DAI_ExperienceRepository;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.registry.DAI_RegistryPreflight;
import io.github.j12h36h.dai.server.entity.DAI_EntityRuntime;
import io.github.j12h36h.dai.worldgen.DAI_WorldgenRepository;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Dedicated-safe final DAI server reload pass.
 *
 * All ordinary DAI definitions have already been replaced by their reload
 * listeners when this executes. Registry preflight gates only native shell
 * changes; early/title-time repositories are refreshed so their JSON is not
 * stuck at JVM-start values.
 */
public final class DAI_ServerPreflightListener
        extends SimplePreparableReloadListener<Boolean> {

    @Override
    protected Boolean prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return Boolean.TRUE;
    }

    @Override
    protected void apply(Boolean prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        DAI_RegistryPreflight.evaluate();
        DAI_ExperienceRepository.reload();
        DAI_WorldgenRepository.reload();
        DAI_EntityRuntime.onDefinitionsReloaded();

        if (DAI_RegistryPreflight.restartRequired()) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Server hot reload applied runtime definitions; {} native addition/change(s) and {} native removal(s) still require restart.",
                    DAI_RegistryPreflight.pendingSpecs().size(),
                    DAI_RegistryPreflight.removedSpecs().size()
            );
        } else {
            DAI_Core.LOGGER.info(
                    "<DAI>: Server hot reload complete; no native registry restart is required."
            );
        }
    }
}
