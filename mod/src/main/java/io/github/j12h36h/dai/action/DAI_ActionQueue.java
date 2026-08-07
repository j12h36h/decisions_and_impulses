package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.condition.DAI_ConditionEvaluator;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.logic.DAI_ActionLogic;

import java.util.ArrayList;
import java.util.List;

public final class DAI_ActionQueue {

    private static final int MAX_QUEUE_SIZE =
            1024;

    private static final List<DAI_ActionDefinition> ACTIONS =
            new ArrayList<>();

    private static int delayTicks;
    private static int selectedIndex;

    private static long revision;

    private DAI_ActionQueue() {
        // Utility class.
    }

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

    public static void tick() {

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

        /*
         * Runtime conditions such as last_action_success are evaluated
         * BEFORE the next action is dispatched through DAI_ActionRegistry.
         *
         * Commit the most recently completed/current result now so those
         * conditions inspect the action that actually just finished instead
         * of remaining one dispatch behind.
         */
        DAI_ActionStatus.commit();

        /*
         * Atomic-action conditions are evaluated immediately before
         * execution rather than while the containing sequence is
         * resolved.
         *
         * This allows runtime conditions such as last_action_success
         * and last_action_failure to inspect the result of the action
         * that executed immediately before this one.
         *
         * When an action is skipped, the previous action status is
         * intentionally preserved for later conditional branches.
         */
        if (
                !DAI_ConditionEvaluator.evaluateAll(
                        action.conditions()
                )
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Skipped queued action type='{}' because its runtime conditions failed.",
                    action.type()
            );

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

            DAI_Core.LOGGER.error(
                    "<DAI>: Queued action type '{}' failed.",
                    action.type(),
                    exception
            );
        }
    }

    public static List<DAI_ActionDefinition> actions() {

        return List.copyOf(
                ACTIONS
        );
    }

    public static boolean isEmpty() {

        return ACTIONS.isEmpty()
                && delayTicks <= 0;
    }

    public static int size() {
        return ACTIONS.size();
    }


    /**
     * Returns the current queue-head action without removing it.
     *
     * Callers must treat the returned action definition as read-only.
     */
    public static DAI_ActionDefinition peek() {

        if (ACTIONS.isEmpty()) {
            return null;
        }

        return ACTIONS.getFirst();
    }

    public static void clear() {

        ACTIONS.clear();

        delayTicks =
                0;

        selectedIndex =
                0;

        mutate();

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared action queue."
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

    /**
     * Prepends an action and suspends queue dispatch for the requested
     * number of ticks without executing a semantic "delay" action.
     *
     * This is intended for polling asynchronous controller-backed work.
     * Because no delay action is dispatched through DAI_ActionRegistry,
     * the active controller's DAI_ActionStatus is not moved/reset merely
     * to wait one client tick.
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

    /**
     * Replaces the current queue-head action with an otherwise-identical
     * definition carrying the supplied slot value, but only when the head
     * is the requested type.
     *
     * This is the safe binding operation for asynchronous action chains:
     *
     * approach_target_block
     * -> wait_for_approach
     *
     * The approach starter must bind only its own immediately-following wait.
     * Searching deeper into the shared queue can accidentally bind a wait
     * belonging to another objective.
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
                new DAI_ActionDefinition(
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

    /**
     * Replaces the first queued action of the requested type with an
     * otherwise-identical definition carrying the supplied slot value.
     *
     * This remains available for generic queue editing, but asynchronous
     * ownership should prefer bindHeadSlot() so a controller cannot bind an
     * action belonging to a later objective.
     */
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
                    new DAI_ActionDefinition(
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