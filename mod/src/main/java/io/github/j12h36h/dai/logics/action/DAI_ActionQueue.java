package io.github.j12h36h.dai.logics.action;

import io.github.j12h36h.dai.logics.DAI_ActionLogic;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionEvaluator;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.ArrayList;
import java.util.List;

public final class DAI_ActionQueue {

    private static final int MAX_QUEUE_SIZE =
            1024;

    private static final List<DAI_ActionDefinition> ACTIONS =
            new ArrayList<>();

    /*
     * A barrier action is completely separate from the normal queue.
     *
     * While present, tick() will ONLY dispatch this action. Nothing
     * behind it in ACTIONS may execute until releaseBarrier() is called.
     */
    private static DAI_ActionDefinition barrierAction;

    private static int barrierDelayTicks;

    private static int delayTicks;

    private static int selectedIndex;

    private static long revision;

    /*
     * True once at least one semantic action has been dispatched.
     *
     * The result of that action is committed immediately before the next
     * normal semantic action evaluates its runtime conditions.
     *
     * Barrier polling never performs this commit because the barrier is
     * still part of the same semantic action lifecycle.
     */
    private static boolean hasDispatchedAction;

    private DAI_ActionQueue() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * ENQUEUE
     * ------------------------------------------------------------
     */

    public static void enqueue(
            DAI_ActionDefinition action
    ) {

        if (action == null) {
            return;
        }

        enqueueAll(
                List.of(
                        action
                )
        );
    }

    public static void enqueueAll(
            List<DAI_ActionDefinition> actions
    ) {

        if (
                actions == null
                        || actions.isEmpty()
        ) {
            return;
        }

        int available =
                MAX_QUEUE_SIZE
                        - ACTIONS.size();

        if (available <= 0) {

            DAI_Core.LOGGER.error(
                    "<DAI>: Action queue is full (max={}).",
                    MAX_QUEUE_SIZE
            );

            return;
        }

        List<DAI_ActionDefinition> accepted =
                actions.size() > available
                        ? actions.subList(
                        0,
                        available
                )
                        : actions;

        ACTIONS.addAll(
                accepted
        );

        mutate();

        DAI_Core.LOGGER.debug(
                "<DAI>: Enqueued {} atomic action(s) (size={}).",
                accepted.size(),
                ACTIONS.size()
        );
    }

    public static void enqueueFirst(
            DAI_ActionDefinition action
    ) {

        if (action == null) {
            return;
        }

        enqueueFirstAll(
                List.of(
                        action
                )
        );
    }

    public static void enqueueFirstAll(
            List<DAI_ActionDefinition> actions
    ) {

        if (
                actions == null
                        || actions.isEmpty()
        ) {
            return;
        }

        int available =
                MAX_QUEUE_SIZE
                        - ACTIONS.size();

        if (available <= 0) {

            DAI_Core.LOGGER.error(
                    "<DAI>: Action queue is full (max={}).",
                    MAX_QUEUE_SIZE
            );

            return;
        }

        List<DAI_ActionDefinition> accepted =
                actions.size() > available
                        ? actions.subList(
                        0,
                        available
                )
                        : actions;

        ACTIONS.addAll(
                0,
                accepted
        );

        mutate();

        DAI_Core.LOGGER.debug(
                "<DAI>: Prepended {} atomic action(s) (size={}).",
                accepted.size(),
                ACTIONS.size()
        );
    }

    /*
     * ------------------------------------------------------------
     * TICK
     * ------------------------------------------------------------
     */

    /**
     * Executes at most one semantic action per client tick.
     *
     * Barrier dispatch always takes priority over the normal queue.
     */
    public static void tick() {

        /*
         * Hard asynchronous barrier.
         *
         * IMPORTANT:
         * Re-polling the barrier must not commit the current status.
         * The barrier still belongs to the semantic action that created it.
         */
        if (barrierAction != null) {

            if (barrierDelayTicks > 0) {

                barrierDelayTicks--;

                return;
            }

            executeBarrier();

            return;
        }

        /*
         * Normal semantic delay.
         */
        if (delayTicks > 0) {

            delayTicks--;

            if (delayTicks == 0) {

                mutate();

                DAI_Core.LOGGER.debug(
                        "<DAI>: Action queue delay completed."
                );
            }

            return;
        }

        if (ACTIONS.isEmpty()) {
            return;
        }

        DAI_ActionDefinition action =
                ACTIONS.removeFirst();

        mutate();

        executeQueuedAction(
                action
        );
    }

    /*
     * ------------------------------------------------------------
     * BARRIER
     * ------------------------------------------------------------
     */

    public static void holdBarrier(
            DAI_ActionDefinition action
    ) {

        holdBarrier(
                action,
                0
        );
    }

    /**
     * Installs or refreshes a hard queue barrier.
     */
    public static void holdBarrier(
            DAI_ActionDefinition action,
            int pollDelayTicks
    ) {

        if (action == null) {
            return;
        }

        boolean newlyInstalled =
                barrierAction == null;

        barrierAction =
                action;

        barrierDelayTicks =
                Math.max(
                        0,
                        pollDelayTicks
                );

        mutate();

        if (newlyInstalled) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Queue barrier installed by action type='{}' slot={}.",
                    action.type(),
                    action.slot()
            );

        } else {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Queue barrier refreshed by action type='{}' slot={}.",
                    action.type(),
                    action.slot()
            );
        }
    }

    public static boolean promoteHeadToBarrier(
            String type,
            int slot
    ) {

        return promoteHeadToBarrier(
                type,
                slot,
                0
        );
    }

    /**
     * Atomically promotes the matching queue-head action into a barrier.
     */
    public static boolean promoteHeadToBarrier(
            String type,
            int slot,
            int pollDelayTicks
    ) {

        if (
                type == null
                        || type.isBlank()
                        || ACTIONS.isEmpty()
        ) {
            return false;
        }

        if (barrierAction != null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot promote queue-head action type='{}' because barrier type='{}' slot={} already owns dispatch.",
                    type.trim(),
                    barrierAction.type(),
                    barrierAction.slot()
            );

            return false;
        }

        String normalizedType =
                type.trim();

        DAI_ActionDefinition head =
                ACTIONS.getFirst();

        if (
                !normalizedType.equals(
                        head.type()
                )
        ) {
            return false;
        }

        ACTIONS.removeFirst();

        DAI_ActionDefinition bound =
                withSlot(
                        head,
                        slot
                );

        barrierAction =
                bound;

        barrierDelayTicks =
                Math.max(
                        0,
                        pollDelayTicks
                );

        mutate();

        DAI_Core.LOGGER.debug(
                "<DAI>: Promoted queue-head action type='{}' to barrier slot={} pollDelay={} (remainingQueueSize={}).",
                normalizedType,
                bound.slot(),
                barrierDelayTicks,
                ACTIONS.size()
        );

        return true;
    }

    /**
     * Releases the current hard queue barrier.
     */
    public static void releaseBarrier() {

        if (barrierAction == null) {
            return;
        }

        String type =
                barrierAction.type();

        int slot =
                barrierAction.slot();

        barrierAction =
                null;

        barrierDelayTicks =
                0;

        mutate();

        DAI_Core.LOGGER.debug(
                "<DAI>: Queue barrier released by action type='{}' slot={}.",
                type,
                slot
        );
    }

    public static boolean hasBarrier() {

        return barrierAction != null;
    }

    public static DAI_ActionDefinition barrier() {

        return barrierAction;
    }

    public static int barrierDelayTicks() {

        return barrierDelayTicks;
    }

    public static boolean barrierIs(
            String type
    ) {

        if (
                barrierAction == null
                        || type == null
                        || type.isBlank()
        ) {
            return false;
        }

        return type.trim()
                .equals(
                        barrierAction.type()
                );
    }

    public static boolean barrierIs(
            String type,
            int slot
    ) {

        return barrierIs(
                type
        )
                && barrierAction.slot()
                == slot;
    }

    private static void executeBarrier() {

        DAI_ActionDefinition action =
                barrierAction;

        if (action == null) {
            return;
        }

        /*
         * A barrier is a continuation/poll of an already-started semantic
         * operation. Do NOT commit current -> previous here.
         */
        executeAction(
                action,
                true
        );
    }

    /*
     * ------------------------------------------------------------
     * EXECUTION
     * ------------------------------------------------------------
     */

    /**
     * Dispatches a normal queued semantic action.
     *
     * Immediately before evaluating this action's conditions, commit the
     * result produced by the preceding semantic action.
     */
    private static void executeQueuedAction(
            DAI_ActionDefinition action
    ) {

        if (action == null) {
            return;
        }

        if (hasDispatchedAction) {

            DAI_ActionStatus.commit();

        } else {

            hasDispatchedAction =
                    true;
        }

        executeAction(
                action,
                false
        );
    }

    /**
     * Executes an action without altering the previous-result lifecycle.
     *
     * Queued actions are committed by executeQueuedAction().
     * Barrier actions are continuations and therefore never commit here.
     */
    private static void executeAction(
            DAI_ActionDefinition action,
            boolean barrierDispatch
    ) {

        if (action == null) {
            return;
        }

        if (
                !DAI_ConditionEvaluator.evaluateAll(
                        action.conditions()
                )
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Skipped {} action type='{}' because its runtime conditions failed.",
                    barrierDispatch
                            ? "barrier"
                            : "queued",
                    action.type()
            );

            /*
             * A skipped normal action still has a semantic result.
             *
             * FAILURE is important here because the following action may
             * intentionally use run_if_failure or last_action_failure.
             */
            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            if (barrierDispatch) {

                releaseBarrier();
            }

            return;
        }

        try {

            DAI_ActionLogic.execute(
                    action
            );

        } catch (RuntimeException exception) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            if (barrierDispatch) {

                releaseBarrier();
            }

            DAI_Core.LOGGER.error(
                    "<DAI>: {} action type '{}' failed.",
                    barrierDispatch
                            ? "Barrier"
                            : "Queued",
                    action.type(),
                    exception
            );
        }
    }

    /*
     * ------------------------------------------------------------
     * STATE / INSPECTION
     * ------------------------------------------------------------
     */

    public static List<DAI_ActionDefinition> actions() {

        return List.copyOf(
                ACTIONS
        );
    }

    public static boolean isEmpty() {

        return ACTIONS.isEmpty()
                && delayTicks <= 0
                && barrierAction == null;
    }

    public static int size() {

        return ACTIONS.size();
    }

    public static DAI_ActionDefinition peek() {

        if (ACTIONS.isEmpty()) {
            return null;
        }

        return ACTIONS.getFirst();
    }

    /**
     * Replaces the current normal queue head while preserving queue order.
     * Primarily used when a higher-level action can specialize an already
     * queued generic continuation instead of duplicating it.
     */
    public static boolean replaceHead(
            DAI_ActionDefinition replacement
    ) {

        if (
                replacement == null
                        || ACTIONS.isEmpty()
        ) {
            return false;
        }

        ACTIONS.set(
                0,
                replacement
        );

        mutate();

        return true;
    }

    public static DAI_ActionDefinition dispatchHead() {

        if (barrierAction != null) {
            return barrierAction;
        }

        return peek();
    }

    /*
     * ------------------------------------------------------------
     * CLEAR / EDIT
     * ------------------------------------------------------------
     */

    public static void clear() {

        ACTIONS.clear();

        delayTicks =
                0;

        barrierAction =
                null;

        barrierDelayTicks =
                0;

        selectedIndex =
                0;

        hasDispatchedAction =
                false;

        mutate();

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared action queue and active barrier."
        );
    }

    public static void remove(
            int index
    ) {

        if (
                index < 0
                        || index >= ACTIONS.size()
        ) {
            return;
        }

        ACTIONS.remove(
                index
        );

        mutate();
    }

    public static int selectedIndex() {

        normalizeSelectedIndex();

        return selectedIndex;
    }

    public static DAI_ActionDefinition selected() {

        if (ACTIONS.isEmpty()) {
            return null;
        }

        normalizeSelectedIndex();

        return ACTIONS.get(
                selectedIndex
        );
    }

    public static void previous() {

        if (selectedIndex <= 0) {
            return;
        }

        selectedIndex--;

        mutate();
    }

    public static void next() {

        if (
                selectedIndex
                        >= ACTIONS.size() - 1
        ) {
            return;
        }

        selectedIndex++;

        mutate();
    }

    /*
     * ------------------------------------------------------------
     * LEGACY POLLING
     * ------------------------------------------------------------
     */

    public static void deferFirst(
            DAI_ActionDefinition action,
            int ticks
    ) {

        if (action == null) {
            return;
        }

        enqueueFirst(
                action
        );

        delayTicks =
                Math.max(
                        0,
                        ticks
                );

        mutate();

        DAI_Core.LOGGER.debug(
                "<DAI>: Deferred queued action type='{}' for {} tick(s).",
                action.type(),
                delayTicks
        );
    }

    /*
     * ------------------------------------------------------------
     * SLOT BINDING
     * ------------------------------------------------------------
     */

    public static boolean bindHeadSlot(
            String type,
            int slot
    ) {

        if (
                type == null
                        || type.isBlank()
                        || ACTIONS.isEmpty()
        ) {
            return false;
        }

        String normalizedType =
                type.trim();

        DAI_ActionDefinition action =
                ACTIONS.getFirst();

        if (
                !normalizedType.equals(
                        action.type()
                )
        ) {
            return false;
        }

        DAI_ActionDefinition bound =
                withSlot(
                        action,
                        slot
                );

        ACTIONS.set(
                0,
                bound
        );

        mutate();

        DAI_Core.LOGGER.debug(
                "<DAI>: Bound queue-head action type='{}' to slot={}.",
                normalizedType,
                bound.slot()
        );

        return true;
    }

    public static boolean bindFirstSlot(
            String type,
            int slot
    ) {

        if (
                type == null
                        || type.isBlank()
                        || ACTIONS.isEmpty()
        ) {
            return false;
        }

        String normalizedType =
                type.trim();

        for (
                int index = 0;
                index < ACTIONS.size();
                index++
        ) {

            DAI_ActionDefinition action =
                    ACTIONS.get(
                            index
                    );

            if (
                    !normalizedType.equals(
                            action.type()
                    )
            ) {
                continue;
            }

            DAI_ActionDefinition bound =
                    withSlot(
                            action,
                            slot
                    );

            ACTIONS.set(
                    index,
                    bound
            );

            mutate();

            DAI_Core.LOGGER.debug(
                    "<DAI>: Bound first queued action type='{}' to slot={}.",
                    normalizedType,
                    bound.slot()
            );

            return true;
        }

        return false;
    }

    /*
     * ------------------------------------------------------------
     * DELAY
     * ------------------------------------------------------------
     */

    public static void delay(
            int ticks
    ) {

        delayTicks =
                Math.max(
                        0,
                        ticks
                );

        mutate();

        DAI_Core.LOGGER.debug(
                "<DAI>: Action queue delayed for {} tick(s).",
                delayTicks
        );
    }

    public static int delayTicks() {

        return delayTicks;
    }

    public static long revision() {

        return revision;
    }

    /*
     * ------------------------------------------------------------
     * HELPERS
     * ------------------------------------------------------------
     */

    private static DAI_ActionDefinition withSlot(
            DAI_ActionDefinition action,
            int slot
    ) {

        return new DAI_ActionDefinition(
                action.type(),
                action.action(),
                action.conditions(),
                action.sequence(),
                action.menu(),
                action.open(),
                action.yaw(),
                action.pitch(),
                action.direction(),
                action.ticks(),
                Math.max(
                        0,
                        slot
                ),
                action.state(),
                action.value()
        );
    }

    private static void mutate() {

        normalizeSelectedIndex();

        revision++;
    }

    private static void normalizeSelectedIndex() {

        if (ACTIONS.isEmpty()) {

            selectedIndex =
                    0;

            return;
        }

        selectedIndex =
                Math.max(
                        0,
                        Math.min(
                                selectedIndex,
                                ACTIONS.size() - 1
                        )
                );
    }
}