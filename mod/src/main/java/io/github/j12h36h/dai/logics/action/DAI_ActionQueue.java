package io.github.j12h36h.dai.logics.action;

import io.github.j12h36h.dai.logics.DAI_ActionLogic;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionEvaluator;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DAI_ActionQueue {

    private static final int MAX_QUEUE_SIZE =
            128;

    /*
     * High-level automation continuations are queued as tiny references and
     * expanded only when they reach the head. This prevents recursive
     * fp_cycle/fp_decide flows from materializing hundreds of atomic actions
     * that are already stale by the time they execute.
     */
    private static final String DEFERRED_REFERENCE_TYPE =
            "__dai_deferred_reference";

    /*
     * Deferred references are structural queue nodes, not semantic actions.
     * Allow a bounded number to expand inline before dispatching the one
     * semantic action permitted this tick.
     */
    private static final int MAX_INLINE_REFERENCE_EXPANSIONS =
            8;

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

        DAI_Core.debug(
                "<DAI>: Enqueued {} atomic action(s) (size={}).",
                accepted.size(),
                ACTIONS.size()
        );
    }

    /**
     * Queues a high-level action reference without expanding it immediately.
     *
     * Fail-proof / vanilla-goal continuations are coalesced by identifier:
     * once one copy is pending, another copy cannot amplify the queue.
     */
    public static void enqueueDeferredReference(
            String actionId
    ) {

        if (actionId == null || actionId.isBlank()) {
            return;
        }

        String normalized =
                actionId.trim();

        if (
                isCoalescibleReference(
                        normalized
                )
                        && containsDeferredReference(
                        normalized
                )
        ) {

            DAI_Core.debug(
                    "<DAI>: Coalesced duplicate deferred action '{}'.",
                    normalized
            );

            return;
        }

        enqueue(
                new DAI_ActionDefinition(
                        DEFERRED_REFERENCE_TYPE,
                        normalized,
                        List.of(),
                        List.of(),
                        "",
                        "",
                        0.0F,
                        0.0F,
                        "",
                        0,
                        0
                )
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

        DAI_Core.debug(
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

                DAI_Core.debug(
                        "<DAI>: Action queue delay completed."
                );
            }

            return;
        }

        if (ACTIONS.isEmpty()) {
            return;
        }

        /*
         * Controllers continue at full client-tick rate, but NEW semantic
         * actions and their structural expansion are globally governed.
         */
        if (!DAI_ActionGovernor.canStartSemanticAction()) {
            return;
        }

        int referenceExpansions =
                0;

        while (!ACTIONS.isEmpty()) {

            DAI_ActionDefinition action =
                    ACTIONS.removeFirst();

            mutate();

            if (isDeferredReference(action)) {

                expandDeferredReference(
                        action
                );

                referenceExpansions++;

                if (
                        referenceExpansions
                                >= MAX_INLINE_REFERENCE_EXPANSIONS
                ) {

                    DAI_Core.LOGGER.warn(
                            "<DAI>: Deferred-reference expansion budget reached in one tick ({}); remaining queue size={}.",
                            MAX_INLINE_REFERENCE_EXPANSIONS,
                            ACTIONS.size()
                    );

                    return;
                }

                /*
                 * Structural expansion does not consume the semantic-action
                 * budget. Continue until an executable action is reached.
                 */
                continue;
            }

            executeQueuedAction(
                    action
            );

            DAI_ActionGovernor.onSemanticActionStarted();

            return;
        }
    }

    private static void expandDeferredReference(
            DAI_ActionDefinition reference
    ) {

        if (reference == null || !reference.hasAction()) {
            return;
        }

        List<DAI_ActionDefinition> resolved =
                DAI_ActionResolver.resolve(
                        reference.action()
                );

        if (resolved.isEmpty()) {
            return;
        }

        enqueueFirstAll(
                resolved
        );

        DAI_Core.debug(
                "<DAI>: Lazily expanded deferred action '{}' into {} atomic action(s) (queueSize={}).",
                reference.action(),
                resolved.size(),
                ACTIONS.size()
        );
    }

    private static boolean isDeferredReference(
            DAI_ActionDefinition action
    ) {

        return action != null
                && DEFERRED_REFERENCE_TYPE.equals(
                action.type()
        );
    }

    private static boolean containsDeferredReference(
            String actionId
    ) {

        if (
                barrierAction != null
                        && isDeferredReference(barrierAction)
                        && actionId.equals(
                        barrierAction.action()
                )
        ) {
            return true;
        }

        for (DAI_ActionDefinition queued : ACTIONS) {

            if (
                    isDeferredReference(queued)
                            && actionId.equals(
                            queued.action()
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean isCoalescibleReference(
            String actionId
    ) {

        String normalized =
                actionId == null
                        ? ""
                        : actionId.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return normalized.startsWith(
                DAI_Core.MODID + ":fp_"
        )
                || normalized.startsWith(
                DAI_Core.MODID + ":vg_"
        );
    }

    /**
     * Hard control-plane interrupt used by menu actions.
     *
     * Existing queued/barrier work is discarded and the first semantic action
     * of the requested menu command executes immediately on the render thread,
     * bypassing the normal governor. Remaining resolved actions stay at the
     * front and subsequently obey normal rate limits.
     */
    public static void interruptAndDispatch(
            List<DAI_ActionDefinition> actions
    ) {

        clear();
        DAI_ActionGovernor.resetForPriorityInterrupt();

        if (actions == null || actions.isEmpty()) {
            return;
        }

        enqueueFirstAll(actions);

        int referenceExpansions = 0;

        while (!ACTIONS.isEmpty()) {

            DAI_ActionDefinition action =
                    ACTIONS.removeFirst();

            mutate();

            if (isDeferredReference(action)) {
                expandDeferredReference(action);
                referenceExpansions++;

                if (referenceExpansions >= MAX_INLINE_REFERENCE_EXPANSIONS) {
                    return;
                }

                continue;
            }

            executeQueuedAction(action);
            return;
        }
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

            DAI_Core.debug(
                    "<DAI>: Queue barrier installed by action type='{}' slot={}.",
                    action.type(),
                    action.slot()
            );

        } else {

            DAI_Core.debug(
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

        /*
         * A promoted wait is now the continuation of an asynchronous
         * operation that has already started successfully. Its original
         * queue-time conditions must NOT be re-evaluated while polling.
         *
         * In particular, generated datapack waits commonly carry
         * last_action_success so they only follow a successful start. The
         * start action immediately changes the live status to RUNNING, which
         * made that condition false on the very next barrier poll. The old
         * behavior released the barrier early and allowed target/exploration
         * actions behind it to cancel and restart the active controller.
         *
         * Strip conditions only when the action becomes a hard barrier. The
         * initiating queued action already passed its runtime conditions, and
         * the barrier's generation-bound controller result is now the sole
         * authority for completion/failure.
         */
        DAI_ActionDefinition bound =
                withBarrierSlot(
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

        DAI_Core.debug(
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

        DAI_Core.debug(
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

            DAI_Core.debug(
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

        DAI_ActionGovernor.resetForPriorityInterrupt();

        mutate();

        DAI_Core.debug(
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

        DAI_Core.debug(
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

        DAI_Core.debug(
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

            DAI_Core.debug(
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

        DAI_Core.debug(
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

    private static DAI_ActionDefinition withBarrierSlot(
            DAI_ActionDefinition action,
            int slot
    ) {

        return new DAI_ActionDefinition(
                action.type(),
                action.action(),
                List.of(),
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