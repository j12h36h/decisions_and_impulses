package io.github.j12h36h.dai.client.logics.core;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.core.DAI_Config;

import io.github.j12h36h.dai.client.logics.DAI_AutomationLogic;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.client.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.client.logics.controller.DAI_BreakController;
import io.github.j12h36h.dai.client.logics.controller.DAI_CreativeFlightController;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Human-readable debug heartbeat and process-health sentinel.
 *
 * The normal heartbeat stays compact enough for latest.log, while a second
 * system line records JVM/native-memory/thread/GC/timing information needed to
 * diagnose abrupt process or IDE termination where Minecraft never gets a
 * chance to emit an exception or crash report.
 *
 * High-frequency reconstruction data belongs to DAI_HumanTrace while manual
 * gameplay is being demonstrated, and structured autonomous diagnostics belong
 * to DAI_RuntimeTelemetry. This class deliberately samples expensive process
 * information only once per heartbeat.
 */
public final class DAI_Debug {

    private static final int HEARTBEAT_TICKS = 100;
    private static final int FAILURE_DETAIL_COOLDOWN_TICKS = 40;

    private static final long SLOW_CLIENT_TICK_NANOS = 250_000_000L;
    private static final long SLOW_HUMAN_TRACE_NANOS = 20_000_000L;
    private static final double HEAP_PRESSURE_WARNING = 0.85D;

    private static final Path DEBUG_DIRECTORY =
            Path.of("logs", "DAI_Log");

    private static final Path PROCESS_SENTINEL =
            DEBUG_DIRECTORY.resolve("dai_debug_process.log");

    private static final long MAX_SENTINEL_BYTES =
            1024L * 1024L;

    private static final MemoryMXBean MEMORY =
            ManagementFactory.getMemoryMXBean();

    private static final ThreadMXBean THREADS =
            ManagementFactory.getThreadMXBean();

    private static final ClassLoadingMXBean CLASSES =
            ManagementFactory.getClassLoadingMXBean();

    private static final RuntimeMXBean RUNTIME =
            ManagementFactory.getRuntimeMXBean();

    private static final OperatingSystemMXBean OS =
            ManagementFactory.getOperatingSystemMXBean();

    private static final AtomicBoolean SHUTDOWN_HOOK_INSTALLED =
            new AtomicBoolean(false);

    private static int heartbeatTicks;
    private static int failureCooldown;
    private static long heartbeatSequence;
    private static DAI_ActionResult lastStatus = DAI_ActionResult.SUCCESS;

    private static long lastTickNanos;
    private static long maxTickGapNanos;
    private static long maxHumanTraceNanos;
    private static long totalHumanTraceNanos;
    private static long humanTraceCalls;
    private static long maxDebugPreLogNanos;

    private static long previousDirectBytes = -1L;
    private static int previousThreadCount = -1;
    private static boolean sentinelStarted;
    private static boolean sentinelIoFailed;

    private DAI_Debug() {
        // Utility class.
    }

    public static boolean isEnabled() {
        return DAI_Config.isDebuggingEnabled();
    }

    public static void tick() {
        if (!isEnabled()) return;

        final long tickStartNanos = System.nanoTime();
        recordClientTickGap(tickStartNanos);
        ensureProcessSentinel();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        /*
         * Human trace is for manual demonstrations. Time the call as part of
         * crash diagnostics so an unexpectedly expensive trace operation is
         * visible in the final heartbeat before a hard process termination.
         */
        if (!DAI_AutomationLogic.isActive()) {
            long traceStart = System.nanoTime();
            DAI_HumanTrace.tick(minecraft);
            recordHumanTraceCost(System.nanoTime() - traceStart);
        }

        if (failureCooldown > 0) {
            failureCooldown--;
        }

        DAI_ActionResult current = DAI_ActionStatus.get();
        boolean newFailure =
                isFailure(current)
                        && current != lastStatus
                        && failureCooldown <= 0;

        heartbeatTicks++;
        boolean heartbeat = heartbeatTicks >= HEARTBEAT_TICKS;

        long preLogNanos = System.nanoTime() - tickStartNanos;
        if (preLogNanos > maxDebugPreLogNanos) {
            maxDebugPreLogNanos = preLogNanos;
        }

        if (heartbeat || newFailure) {
            if (heartbeat) {
                heartbeatTicks = 0;
                heartbeatSequence++;
            }

            logCompactSnapshot(minecraft, newFailure);
            logSystemSnapshot(minecraft, heartbeatSequence, newFailure);

            if (heartbeat) {
                resetTimingWindow();
            }
        }

        if (newFailure) {
            failureCooldown = FAILURE_DETAIL_COOLDOWN_TICKS;
        }

        lastStatus = current;
    }

    private static void logCompactSnapshot(
            Minecraft minecraft,
            boolean includeFailureDetail
    ) {
        Vec3 position = minecraft.player.position();
        Vec3 velocity = minecraft.player.getDeltaMovement();
        BlockPos selectedBlock = DAI_TargetState.selectedBlock();
        DAI_ActionDefinition queueHead = DAI_ActionQueue.peek();

        DAI_Core.LOGGER.info(
                "<DAI:DEBUG> pos={} vel={} act={}/{} auto={} mode={} q={} dly={} head={} target={} ap={} br={} fly={} fp={} fd={}/{} stall={} assist={} hotbar={} input={},{},j{},s{} look={}/{} screen={}",
                vec(position),
                vec(velocity),
                DAI_ActionStatus.get(),
                DAI_ActionStatus.previous(),
                DAI_AutomationLogic.isActive(),
                DAI_AutomationLogic.modeName(),
                DAI_ActionQueue.size(),
                DAI_ActionQueue.delayTicks(),
                queueHead == null ? "none" : action(queueHead),
                selectedBlock == null ? "-" : selectedBlock,
                DAI_ApproachController.isActive(),
                DAI_BreakController.isActive(),
                DAI_CreativeFlightController.isActive(),
                DAI_CreativeFlightController.phaseName(),
                distance(DAI_CreativeFlightController.distanceToMovementTarget()),
                distance(DAI_CreativeFlightController.distanceToTarget()),
                DAI_CreativeFlightController.stalledTicks(),
                DAI_CreativeFlightController.velocityAssistActive(),
                hotbar(minecraft),
                format(DAI_InputState.movement().forward()),
                format(DAI_InputState.movement().strafe()),
                DAI_InputState.movement().jump() ? 1 : 0,
                DAI_InputState.movement().sneak() ? 1 : 0,
                format(minecraft.player.getYRot()),
                format(minecraft.player.getXRot()),
                DAI_HumanTraceFormat.screenState(minecraft)
        );

        if (!includeFailureDetail) return;

        DAI_Core.LOGGER.info(
                "<DAI:DEBUG:FAIL> act={}/{} head={} target={} hit={}",
                DAI_ActionStatus.get(),
                DAI_ActionStatus.previous(),
                queueHead == null ? "none" : action(queueHead),
                selectedBlock == null ? "-" : selectedBlock,
                DAI_HumanTraceFormat.describeHit(minecraft)
        );
    }

    private static void logSystemSnapshot(
            Minecraft minecraft,
            long sequence,
            boolean failureTriggered
    ) {
        MemoryUsage heap = MEMORY.getHeapMemoryUsage();
        MemoryUsage nonHeap = MEMORY.getNonHeapMemoryUsage();
        BufferStats buffers = bufferStats();
        GcStats gc = gcStats();

        long heapUsed = nonNegative(heap.getUsed());
        long heapCommitted = nonNegative(heap.getCommitted());
        long heapMax = nonNegative(heap.getMax());
        double heapPressure = heapMax > 0L
                ? (double) heapUsed / (double) heapMax
                : 0.0D;

        int threadCount = THREADS.getThreadCount();
        long directDelta = previousDirectBytes < 0L
                ? 0L
                : buffers.directMemoryBytes() - previousDirectBytes;
        int threadDelta = previousThreadCount < 0
                ? 0
                : threadCount - previousThreadCount;

        String timing =
                "gap_ms=" + millis(maxTickGapNanos)
                        + ",trace_max_ms=" + millis(maxHumanTraceNanos)
                        + ",trace_avg_ms=" + millis(averageHumanTraceNanos())
                        + ",debug_prelog_max_ms=" + millis(maxDebugPreLogNanos);

        String memory =
                "heap=" + mib(heapUsed)
                        + "/" + mib(heapCommitted)
                        + "/" + mib(heapMax) + "MiB"
                        + ",heap_pct=" + percent(heapPressure)
                        + ",nonheap=" + mib(nonNegative(nonHeap.getUsed())) + "MiB"
                        + ",direct=" + mib(buffers.directMemoryBytes()) + "MiB"
                        + ",direct_count=" + buffers.directCount()
                        + ",mapped=" + mib(buffers.mappedMemoryBytes()) + "MiB";

        String runtime =
                "uptime_ms=" + RUNTIME.getUptime()
                        + ",pid=" + currentPid()
                        + ",threads=" + threadCount
                        + ",peak=" + THREADS.getPeakThreadCount()
                        + ",daemon=" + THREADS.getDaemonThreadCount()
                        + ",classes=" + CLASSES.getLoadedClassCount()
                        + ",gc_count=" + gc.collections()
                        + ",gc_ms=" + gc.collectionTimeMs()
                        + ",cpu=" + OS.getAvailableProcessors()
                        + ",load=" + formatLoad(OS.getSystemLoadAverage());

        DAI_Core.LOGGER.info(
                "<DAI:DEBUG:SYS> hb={} trigger={} tick={} {} {} {} delta_direct={}MiB delta_threads={}",
                sequence,
                failureTriggered ? "failure" : "heartbeat",
                minecraft.level.getGameTime(),
                memory,
                runtime,
                timing,
                signedMib(directDelta),
                threadDelta
        );

        appendSentinel(
                "heartbeat hb=" + sequence
                        + " tick=" + minecraft.level.getGameTime()
                        + " " + memory
                        + " " + runtime
                        + " " + timing
                        + " delta_direct=" + signedMib(directDelta) + "MiB"
                        + " delta_threads=" + threadDelta
        );

        boolean warning =
                heapPressure >= HEAP_PRESSURE_WARNING
                        || maxTickGapNanos >= SLOW_CLIENT_TICK_NANOS
                        || maxHumanTraceNanos >= SLOW_HUMAN_TRACE_NANOS
                        || Math.abs(directDelta) >= 64L * 1024L * 1024L
                        || threadDelta >= 32;

        if (warning) {
            DAI_Core.LOGGER.warn(
                    "<DAI:DEBUG:WATCH> heap_pct={} gap_ms={} trace_max_ms={} direct_delta={}MiB thread_delta={}",
                    percent(heapPressure),
                    millis(maxTickGapNanos),
                    millis(maxHumanTraceNanos),
                    signedMib(directDelta),
                    threadDelta
            );

            appendSentinel(
                    "watch heap_pct=" + percent(heapPressure)
                            + " gap_ms=" + millis(maxTickGapNanos)
                            + " trace_max_ms=" + millis(maxHumanTraceNanos)
                            + " direct_delta=" + signedMib(directDelta) + "MiB"
                            + " thread_delta=" + threadDelta
            );
        }

        previousDirectBytes = buffers.directMemoryBytes();
        previousThreadCount = threadCount;
    }

    private static void recordClientTickGap(long nowNanos) {
        if (lastTickNanos > 0L) {
            long gap = nowNanos - lastTickNanos;
            if (gap > maxTickGapNanos) {
                maxTickGapNanos = gap;
            }
        }
        lastTickNanos = nowNanos;
    }

    private static void recordHumanTraceCost(long elapsedNanos) {
        humanTraceCalls++;
        totalHumanTraceNanos += elapsedNanos;
        if (elapsedNanos > maxHumanTraceNanos) {
            maxHumanTraceNanos = elapsedNanos;
        }
    }

    private static long averageHumanTraceNanos() {
        if (humanTraceCalls <= 0L) {
            return 0L;
        }
        return totalHumanTraceNanos / humanTraceCalls;
    }

    private static void resetTimingWindow() {
        maxTickGapNanos = 0L;
        maxHumanTraceNanos = 0L;
        totalHumanTraceNanos = 0L;
        humanTraceCalls = 0L;
        maxDebugPreLogNanos = 0L;
    }

    private static void ensureProcessSentinel() {
        if (!sentinelStarted) {
            sentinelStarted = true;
            sentinelIoFailed = false;

            try {
                Files.createDirectories(DEBUG_DIRECTORY);
                Files.writeString(
                        PROCESS_SENTINEL,
                        "",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                );
            } catch (IOException exception) {
                sentinelIoFailed = true;
                DAI_Core.LOGGER.warn(
                        "<DAI>: Could not initialize debug process sentinel '{}'.",
                        PROCESS_SENTINEL,
                        exception
                );
            }

            appendSentinel(
                    "session_start pid=" + currentPid()
                            + " jvm=" + System.getProperty("java.vm.name", "")
                            + " java=" + System.getProperty("java.version", "")
                            + " os=" + System.getProperty("os.name", "")
                            + "/" + System.getProperty("os.arch", "")
                            + " max_heap=" + mib(Runtime.getRuntime().maxMemory()) + "MiB"
            );
        }

        if (SHUTDOWN_HOOK_INSTALLED.compareAndSet(false, true)) {
            try {
                Runtime.getRuntime().addShutdownHook(
                        new Thread(
                                () -> appendSentinel(
                                        "shutdown_hook uptime_ms=" + RUNTIME.getUptime()
                                                + " pid=" + currentPid()
                                ),
                                "DAI-Debug-Shutdown"
                        )
                );
            } catch (IllegalStateException | SecurityException exception) {
                DAI_Core.LOGGER.debug(
                        "<DAI>: Could not install debug shutdown hook.",
                        exception
                );
            }
        }
    }

    private static void appendSentinel(String message) {
        if (sentinelIoFailed || message == null) {
            return;
        }

        try {
            if (Files.exists(PROCESS_SENTINEL)
                    && Files.size(PROCESS_SENTINEL) >= MAX_SENTINEL_BYTES) {
                return;
            }

            Files.writeString(
                    PROCESS_SENTINEL,
                    Instant.now() + " <DAI:PROCESS> " + message + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            sentinelIoFailed = true;
            DAI_Core.LOGGER.warn(
                    "<DAI>: Debug process sentinel disabled after I/O failure '{}'.",
                    PROCESS_SENTINEL,
                    exception
            );
        }
    }

    private static BufferStats bufferStats() {
        long directMemory = 0L;
        long directCount = 0L;
        long mappedMemory = 0L;

        for (BufferPoolMXBean pool
                : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if (pool == null || pool.getName() == null) {
                continue;
            }

            String name = pool.getName().toLowerCase(Locale.ROOT);
            if ("direct".equals(name)) {
                directMemory += nonNegative(pool.getMemoryUsed());
                directCount += nonNegative(pool.getCount());
            } else if (name.startsWith("mapped")) {
                mappedMemory += nonNegative(pool.getMemoryUsed());
            }
        }

        return new BufferStats(
                directMemory,
                directCount,
                mappedMemory
        );
    }

    private static GcStats gcStats() {
        long collections = 0L;
        long time = 0L;

        for (GarbageCollectorMXBean collector
                : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (collector == null) {
                continue;
            }

            collections += nonNegative(collector.getCollectionCount());
            time += nonNegative(collector.getCollectionTime());
        }

        return new GcStats(collections, time);
    }

    private static long currentPid() {
        try {
            return ProcessHandle.current().pid();
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static boolean isFailure(DAI_ActionResult result) {
        return result == DAI_ActionResult.FAILURE
                || result == DAI_ActionResult.TIMED_OUT;
    }

    private static String hotbar(Minecraft minecraft) {
        int slot = minecraft.player.getInventory().getSelectedSlot();
        ItemStack stack = minecraft.player.getInventory().getItem(slot);

        if (stack.isEmpty()) {
            return slot + ":empty";
        }

        return slot
                + ":"
                + BuiltInRegistries.ITEM.getKey(stack.getItem())
                + "x"
                + stack.getCount();
    }

    private static String describeQueue() {
        List<DAI_ActionDefinition> actions = DAI_ActionQueue.actions();
        if (actions.isEmpty()) return "[]";

        int count = Math.min(4, actions.size());
        StringJoiner joiner = new StringJoiner(
                ",",
                "[",
                actions.size() > count ? ",...]" : "]"
        );

        for (int index = 0; index < count; index++) {
            joiner.add(action(actions.get(index)));
        }

        return joiner.toString();
    }

    private static String action(DAI_ActionDefinition action) {
        if (action == null) return "none";
        return action.type()
                + (action.hasAction() ? "(" + action.action() + ")" : "");
    }

    private static String vec(Vec3 value) {
        return format(value.x) + "," + format(value.y) + "," + format(value.z);
    }

    private static String distance(double value) {
        return value < 0.0D ? "-" : format(value);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0D);
    }

    private static String mib(long bytes) {
        if (bytes < 0L) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f", bytes / 1_048_576.0D);
    }

    private static String signedMib(long bytes) {
        double value = bytes / 1_048_576.0D;
        return String.format(Locale.ROOT, "%+.1f", value);
    }

    private static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.1f", ratio * 100.0D);
    }

    private static String formatLoad(double load) {
        if (!Double.isFinite(load) || load < 0.0D) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f", load);
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private record BufferStats(
            long directMemoryBytes,
            long directCount,
            long mappedMemoryBytes
    ) {
    }

    private record GcStats(
            long collections,
            long collectionTimeMs
    ) {
    }
}
