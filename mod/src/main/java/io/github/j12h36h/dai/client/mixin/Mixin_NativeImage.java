package io.github.j12h36h.dai.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import org.lwjgl.stb.STBImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mitigates transient native-memory exhaustion inside Minecraft's PNG/image
 * decoder during parallel resource reloads.
 *
 * Minecraft decodes many textures concurrently. STB performs the actual image
 * decode in native memory, so several large decodes can temporarily overlap
 * even when the Java heap still has plenty of room. On affected systems STB
 * can then return null with "Out of memory", which NativeImage converts into
 * an IOException and the resource reload may be discarded or the process can
 * terminate before Minecraft produces a normal crash report.
 *
 * DAI serializes only the native STB decode call and retries an allocation
 * failure once after giving the JVM an opportunity to reclaim unreachable
 * Java/native wrapper state. Real, persistent exhaustion is deliberately not
 * hidden: after the retry the original null result is returned and vanilla
 * keeps its normal error path.
 */
@Mixin(NativeImage.class)
public abstract class Mixin_NativeImage {

    @Unique
    private static final Object dai$stbiDecodeLock = new Object();

    @Unique
    private static final AtomicBoolean dai$guardAnnounced = new AtomicBoolean(false);

    @Unique
    private static final AtomicLong dai$nativeOomRecoveries = new AtomicLong();

    @Unique
    private static final AtomicLong dai$nativeOomFailures = new AtomicLong();

    @Redirect(
            method = "read",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/stb/STBImage;stbi_load_from_memory(Ljava/nio/ByteBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)Ljava/nio/ByteBuffer;"
            ),
            require = 0
    )
    private static ByteBuffer dai$guardNativeImageDecode(
            ByteBuffer source,
            IntBuffer width,
            IntBuffer height,
            IntBuffer channels,
            int desiredChannels
    ) {
        if (dai$guardAnnounced.compareAndSet(false, true)) {
            DAI_Core.LOGGER.info(
                    "<DAI:NATIVE_IMAGE_GUARD> NativeImage STB decode serialization/retry guard active."
            );
        }

        synchronized (dai$stbiDecodeLock) {
            ByteBuffer decoded = STBImage.stbi_load_from_memory(
                    source,
                    width,
                    height,
                    channels,
                    desiredChannels
            );

            if (decoded != null) {
                return decoded;
            }

            String reason = dai$stbiFailureReason();
            if (!dai$isNativeOutOfMemory(reason)) {
                return null;
            }

            long attempt = dai$nativeOomRecoveries.incrementAndGet();
            DAI_Core.LOGGER.warn(
                    "<DAI:NATIVE_IMAGE_GUARD> STB image decode reported native out-of-memory; serial decode guard is retrying once. recovery={} {} reason='{}'",
                    attempt,
                    dai$memorySnapshot(),
                    reason
            );

            /*
             * This is intentionally a one-shot recovery request, not a GC loop.
             * A loop can turn genuine memory exhaustion into a permanent stall.
             */
            System.gc();
            Thread.yield();

            decoded = STBImage.stbi_load_from_memory(
                    source,
                    width,
                    height,
                    channels,
                    desiredChannels
            );

            if (decoded != null) {
                DAI_Core.LOGGER.info(
                        "<DAI:NATIVE_IMAGE_GUARD> Native image decode recovered after transient allocation failure. recovery={} {}",
                        attempt,
                        dai$memorySnapshot()
                );
                return decoded;
            }

            long failures = dai$nativeOomFailures.incrementAndGet();
            DAI_Core.LOGGER.error(
                    "<DAI:NATIVE_IMAGE_GUARD> Native image decode still out of memory after guarded retry; returning control to vanilla. failures={} {} reason='{}'",
                    failures,
                    dai$memorySnapshot(),
                    dai$stbiFailureReason()
            );

            return null;
        }
    }

    @Unique
    private static boolean dai$isNativeOutOfMemory(String reason) {
        if (reason == null || reason.isBlank()) {
            return false;
        }

        String normalized = reason.toLowerCase(Locale.ROOT);
        return normalized.contains("out of memory")
                || normalized.contains("out-of-memory")
                || normalized.contains("allocation failed")
                || normalized.contains("cannot allocate");
    }

    @Unique
    private static String dai$stbiFailureReason() {
        String reason = STBImage.stbi_failure_reason();
        return reason == null ? "unknown" : reason;
    }

    @Unique
    private static String dai$memorySnapshot() {
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
        long heapCommitted = Math.max(0L, runtime.totalMemory());
        long heapMax = Math.max(0L, runtime.maxMemory());

        long direct = 0L;
        long mapped = 0L;

        try {
            List<BufferPoolMXBean> pools =
                    ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);

            for (BufferPoolMXBean pool : pools) {
                if ("direct".equalsIgnoreCase(pool.getName())) {
                    direct = Math.max(0L, pool.getMemoryUsed());
                } else if ("mapped".equalsIgnoreCase(pool.getName())) {
                    mapped = Math.max(0L, pool.getMemoryUsed());
                }
            }
        } catch (Throwable ignored) {
            // Diagnostics must never turn a recoverable texture failure into one.
        }

        return "heap=" + dai$mib(heapUsed)
                + "/" + dai$mib(heapCommitted)
                + "/" + dai$mib(heapMax) + "MiB"
                + " direct=" + dai$mib(direct) + "MiB"
                + " mapped=" + dai$mib(mapped) + "MiB";
    }

    @Unique
    private static long dai$mib(long bytes) {
        return bytes / (1024L * 1024L);
    }
}
