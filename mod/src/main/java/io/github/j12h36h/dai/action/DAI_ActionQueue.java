package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI_Core;

import java.util.ArrayList;
import java.util.List;

public final class DAI_ActionQueue {

    private static final int MAX_QUEUE_SIZE = 1024;
    private static final List<DAI_ActionCore> ACTIONS = new ArrayList<>();

    private static int delayTicks;
    private static int selectedIndex;
    private static long revision;

    private DAI_ActionQueue() {
        // Utility class.
    }

    public static void enqueue(DAI_ActionCore action) {
        if (action == null) {
            return;
        }
        enqueueAll(List.of(action));
    }

    public static void enqueueAll(List<DAI_ActionCore> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        int available = MAX_QUEUE_SIZE - ACTIONS.size();
        if (available <= 0) {
            DAI_Core.LOGGER.error("<DAI>: Action queue is full (max={}).", MAX_QUEUE_SIZE);
            return;
        }
        List<DAI_ActionCore> accepted = actions.size() > available
                ? actions.subList(0, available)
                : actions;
        ACTIONS.addAll(accepted);
        mutate();
        DAI_Core.LOGGER.debug("<DAI>: Enqueued {} atomic action(s) (size={}).", accepted.size(), ACTIONS.size());
    }

    public static void tick() {
        if (delayTicks > 0) {
            delayTicks--;
            if (delayTicks == 0) {
                mutate();
                DAI_Core.LOGGER.debug("<DAI>: Action queue delay completed.");
            }
            return;
        }

        if (ACTIONS.isEmpty()) {
            return;
        }

        DAI_ActionCore action = ACTIONS.getFirst();
        try {
            DAI_ActionLogic.execute(action);
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.error("<DAI>: Queued action type '{}' failed.", action.type(), exception);
        } finally {
            ACTIONS.removeFirst();
            mutate();
        }
    }

    public static List<DAI_ActionCore> actions() {
        return List.copyOf(ACTIONS);
    }

    public static void clear() {
        ACTIONS.clear();
        delayTicks = 0;
        selectedIndex = 0;
        mutate();
    }

    public static void remove(int index) {
        if (index < 0 || index >= ACTIONS.size()) {
            return;
        }
        ACTIONS.remove(index);
        mutate();
    }

    public static int selectedIndex() {
        normalizeSelectedIndex();
        return selectedIndex;
    }

    public static DAI_ActionCore selected() {
        if (ACTIONS.isEmpty()) {
            return null;
        }
        normalizeSelectedIndex();
        return ACTIONS.get(selectedIndex);
    }

    public static void previous() {
        if (selectedIndex > 0) {
            selectedIndex--;
            mutate();
        }
    }

    public static void next() {
        if (selectedIndex < ACTIONS.size() - 1) {
            selectedIndex++;
            mutate();
        }
    }

    public static void delay(int ticks) {
        delayTicks = Math.max(0, ticks);
        mutate();
        DAI_Core.LOGGER.debug("<DAI>: Action queue delayed for {} tick(s).", delayTicks);
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
            selectedIndex = 0;
        } else {
            selectedIndex = Math.max(0, Math.min(selectedIndex, ACTIONS.size() - 1));
        }
    }
}
