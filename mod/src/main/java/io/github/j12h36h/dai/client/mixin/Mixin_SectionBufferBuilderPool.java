package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Conservative startup guard for Minecraft's section render-buffer pool.
 *
 * On some systems Minecraft can request many section builder buffers during
 * renderer bootstrap and then discover the native allocator can only satisfy a
 * small subset of them. The failed probes can coincide with native image / GL
 * allocation failures before Minecraft has a chance to produce a normal crash
 * report.
 *
 * DAI limits only the initial requested pool size. This does not change world
 * correctness; it trades some chunk rebuild parallelism for startup/native
 * memory headroom. The cap can be overridden with:
 *
 *   -Ddai.native.sectionBufferCap=<n>
 *
 * Set the property to 0 or a negative value to disable the cap entirely.
 */
@Mixin(targets = "net.minecraft.client.renderer.SectionBufferBuilderPool")
public abstract class Mixin_SectionBufferBuilderPool {

    @Unique
    private static final int dai$defaultSectionBufferCap = 3;

    @Unique
    private static final AtomicBoolean dai$announced = new AtomicBoolean(false);

    @ModifyVariable(
            method = "allocate",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private static int dai$limitStartupSectionBuffers(int requested) {
        int configuredCap = Integer.getInteger(
                "dai.native.sectionBufferCap",
                dai$defaultSectionBufferCap
        );

        if (configuredCap <= 0 || requested <= configuredCap) {
            if (dai$announced.compareAndSet(false, true)) {
                DAI_Core.LOGGER.info(
                        "<DAI:NATIVE_BUFFER_GUARD> Section buffer guard active without reduction. requested={} cap={} {}",
                        requested,
                        configuredCap,
                        dai$memorySnapshot()
                );
            }
            return requested;
        }

        if (dai$announced.compareAndSet(false, true)) {
            DAI_Core.LOGGER.warn(
                    "<DAI:NATIVE_BUFFER_GUARD> Reducing startup section-buffer request from {} to {} to preserve native-memory headroom. {} Override with -Ddai.native.sectionBufferCap=<n>; use 0 to disable.",
                    requested,
                    configuredCap,
                    dai$memorySnapshot()
            );
        }

        return configuredCap;
    }

    @Unique
    private static String dai$memorySnapshot() {
        try {
            MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memory.getHeapMemoryUsage();
            long used = Math.max(0L, heap.getUsed());
            long committed = Math.max(0L, heap.getCommitted());
            long max = Math.max(0L, heap.getMax());
            return "heap=" + dai$mib(used)
                    + "/" + dai$mib(committed)
                    + "/" + dai$mib(max) + "MiB";
        } catch (Throwable ignored) {
            return "heap=unknown";
        }
    }

    @Unique
    private static long dai$mib(long bytes) {
        return bytes / (1024L * 1024L);
    }
}
