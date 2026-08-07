package io.github.j12h36h.dai.action;

public final class DAI_ActionStatus {

    private static DAI_ActionResult previous =
            DAI_ActionResult.SUCCESS;

    private static DAI_ActionResult current =
            DAI_ActionResult.SUCCESS;

    private DAI_ActionStatus() {
        // Utility class.
    }

    /**
     * Begins execution of a new action.
     *
     * The existing current result becomes the previous result so flow
     * actions and runtime conditions can inspect it during this action.
     */
    public static void begin() {

        previous =
                current;

        current =
                DAI_ActionResult.SUCCESS;
    }

    /**
     * Updates the result of the action or controller currently active.
     */
    public static void set(
            DAI_ActionResult result
    ) {

        current =
                result == null
                        ? DAI_ActionResult.FAILURE
                        : result;
    }

    /**
     * Returns the result of the current or most recently dispatched
     * action.
     */
    public static DAI_ActionResult get() {
        return current;
    }

    /**
     * Returns the result that existed immediately before the current
     * action began.
     */
    public static DAI_ActionResult previous() {
        return previous;
    }

    public static boolean isRunning() {
        return current == DAI_ActionResult.RUNNING;
    }

    public static boolean succeeded() {
        return current == DAI_ActionResult.SUCCESS;
    }

    public static boolean failed() {

        return isFailure(
                current
        );
    }

    public static boolean previousSucceeded() {

        return previous
                == DAI_ActionResult.SUCCESS;
    }

    public static boolean previousFailed() {

        return isFailure(
                previous
        );
    }

    public static boolean previousCancelled() {

        return previous
                == DAI_ActionResult.CANCELLED;
    }

    public static boolean previousTimedOut() {

        return previous
                == DAI_ActionResult.TIMED_OUT;
    }

    /**
     * Copies the current result into previous without beginning another
     * action.
     *
     * This is useful when controller-backed work finishes between
     * queued action dispatches.
     */
    public static void commit() {

        previous =
                current;
    }

    public static void reset() {

        previous =
                DAI_ActionResult.SUCCESS;

        current =
                DAI_ActionResult.SUCCESS;
    }

    private static boolean isFailure(
            DAI_ActionResult result
    ) {

        return result
                == DAI_ActionResult.FAILURE
                || result
                == DAI_ActionResult.TIMED_OUT
                || result
                == DAI_ActionResult.CANCELLED;
    }
}