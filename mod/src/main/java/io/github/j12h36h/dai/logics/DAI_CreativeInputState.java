package io.github.j12h36h.dai.logics;

public final class DAI_CreativeInputState {

    /*
     * DAI dispatches actions during ClientTickEvent.Post. A queued KeyMapping
     * click is consumed by vanilla on a subsequent client tick, so Ctrl must
     * survive beyond the action method that scheduled that click.
     *
     * Arming for two DAI post-ticks yields one complete intervening vanilla
     * input-processing window while remaining tightly bounded.
     */
    private static final int CONTROL_ARM_TICKS =
            2;

    private static int forceControlTicks;

    private DAI_CreativeInputState() {
        // Utility class.
    }

    public static boolean forceControlModifier() {
        return forceControlTicks > 0;
    }

    public static void armControlModifier() {
        forceControlTicks =
                Math.max(
                        forceControlTicks,
                        CONTROL_ARM_TICKS
                );
    }

    public static void tick() {

        if (forceControlTicks > 0) {
            forceControlTicks--;
        }
    }

    public static void reset() {
        forceControlTicks = 0;
    }
}
