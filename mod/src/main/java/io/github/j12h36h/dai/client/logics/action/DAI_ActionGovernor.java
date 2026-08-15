package io.github.j12h36h.dai.client.logics.action;

import io.github.j12h36h.dai.logics.action.*;

/**
 * Global semantic-action start governor.
 *
 * Controllers continue ticking every client tick; only NEW queued semantic
 * actions are rate-limited. This keeps movement/camera/breaking smooth while
 * bounding queue churn and short-lived allocations.
 */
public final class DAI_ActionGovernor {

    private static final int NORMAL_INTERVAL_TICKS = 2;   // 10/sec
    private static final int ELEVATED_INTERVAL_TICKS = 3; // ~6.7/sec
    private static final int HIGH_INTERVAL_TICKS = 4;     // 5/sec
    private static final int CRITICAL_INTERVAL_TICKS = 10;// 2/sec

    private static int cooldownTicks;
    private static long semanticActionsStarted;
    private static long throttledTicks;

    private DAI_ActionGovernor() {
        // Utility class.
    }

    public static void tick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
    }

    public static boolean canStartSemanticAction() {
        if (cooldownTicks <= 0) {
            return true;
        }
        throttledTicks++;
        return false;
    }

    public static void onSemanticActionStarted() {
        semanticActionsStarted++;
        cooldownTicks = currentIntervalTicks();
    }

    /** Menu/control-plane actions intentionally bypass normal throttling. */
    public static void resetForPriorityInterrupt() {
        cooldownTicks = 0;
    }

    public static int currentIntervalTicks() {
        double pressure = heapPressure();
        if (pressure >= 0.90D) return CRITICAL_INTERVAL_TICKS;
        if (pressure >= 0.80D) return HIGH_INTERVAL_TICKS;
        if (pressure >= 0.70D) return ELEVATED_INTERVAL_TICKS;
        return NORMAL_INTERVAL_TICKS;
    }

    public static double currentActionLimitPerSecond() {
        return 20.0D / currentIntervalTicks();
    }

    public static long semanticActionsStarted() {
        return semanticActionsStarted;
    }

    public static long throttledTicks() {
        return throttledTicks;
    }

    private static double heapPressure() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        if (max <= 0L) return 0.0D;
        long used = runtime.totalMemory() - runtime.freeMemory();
        return (double) used / (double) max;
    }
}
