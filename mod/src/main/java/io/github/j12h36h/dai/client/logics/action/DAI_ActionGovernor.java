package io.github.j12h36h.dai.client.logics.action;

import io.github.j12h36h.dai.client.config.DAI_PlayerControls;

/**
 * Global semantic-action start governor.
 *
 * Controllers continue ticking every client tick; only NEW queued semantic
 * actions are rate-limited. The player's configured ceiling is additionally
 * capped by the active experience (when it declares one) and by heap pressure.
 */
public final class DAI_ActionGovernor {

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
        int configuredLimit = Math.max(1, Math.min(20, DAI_PlayerControls.maxActionsPerSecond()));
        int configuredInterval = Math.max(1, (int) Math.ceil(20.0D / configuredLimit));

        double pressure = heapPressure();
        int pressureInterval = 1;
        if (pressure >= 0.90D) pressureInterval = CRITICAL_INTERVAL_TICKS;
        else if (pressure >= 0.80D) pressureInterval = HIGH_INTERVAL_TICKS;
        else if (pressure >= 0.70D) pressureInterval = ELEVATED_INTERVAL_TICKS;

        return Math.max(configuredInterval, pressureInterval);
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
