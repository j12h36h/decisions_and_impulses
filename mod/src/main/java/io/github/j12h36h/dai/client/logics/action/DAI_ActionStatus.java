package io.github.j12h36h.dai.client.logics.action;

import io.github.j12h36h.dai.logics.action.*;

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
     * The result of the previously completed action must already have
     * been committed by the action queue before this method is called.
     *
     * Beginning an action therefore does not modify previous.
     */
    public static void begin() {

        current =
                DAI_ActionResult.RUNNING;
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
     * Returns the result of the currently executing or most recently
     * dispatched action.
     */
    public static DAI_ActionResult get() {

        return current;
    }

    /**
     * Returns the committed result of the action that completed before
     * the current action.
     */
    public static DAI_ActionResult previous() {

        return previous;
    }

    public static boolean isRunning() {

        return current
                == DAI_ActionResult.RUNNING;
    }

    public static boolean succeeded() {

        return current
                == DAI_ActionResult.SUCCESS;
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
     * Commits the current action result so that runtime conditions for
     * the next action can inspect it.
     *
     * This is the only operation that promotes current into previous.
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