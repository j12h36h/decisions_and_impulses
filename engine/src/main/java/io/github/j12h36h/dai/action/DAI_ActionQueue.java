package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI;
import io.github.j12h36h.dai.ui.DAI_Menu;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
public final class DAI_ActionQueue {

    private static int delayTicks;

    private static int selectedIndex;

    private static final Queue<DAI_Action> QUEUE = new ArrayDeque<>();

    private static Runnable refreshCallback = () -> {};

    private DAI_ActionQueue() {
    }

    /**
     * Adds an action to the end of the queue.
     */
    public static void enqueue(DAI_Action action) {

        boolean wasEmpty = QUEUE.isEmpty();

        QUEUE.offer(action);

        // Start this action's delay immediately if it's the only action.
        if (wasEmpty) {
            delayTicks = action.ticks();
        }

        refreshCallback.run();

        DAI.LOGGER.info(
                "<DAI>: Enqueue {}",
                action.type()
        );
    }

    public static DAI_Action poll() {
        return QUEUE.poll();
    }

    public static DAI_Action peek() {
        return QUEUE.peek();
    }

    public static List<DAI_Action> actions() {
        return List.copyOf(QUEUE);
    }

    public static int size() {
        return QUEUE.size();
    }

    public static boolean isEmpty() {
        return QUEUE.isEmpty();
    }

    public static void clear() {
        QUEUE.clear();
        selectedIndex = 0;
        delayTicks = 0;
        refreshCallback.run();
    }

    public static void remove(int index) {

        if (index < 0 || index >= QUEUE.size()) {
            return;
        }

        List<DAI_Action> actions = new ArrayList<>(QUEUE);

        actions.remove(index);

        QUEUE.clear();
        QUEUE.addAll(actions);

        if (selectedIndex >= QUEUE.size()) {
            selectedIndex = Math.max(0, QUEUE.size() - 1);
        }

        if (!QUEUE.isEmpty()) {
            delayTicks = QUEUE.peek().ticks();
        } else {
            delayTicks = 0;
        }

        refreshCallback.run();
    }

    /**
     * Executes one queued action each tick.
     */
    public static void tick() {

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        DAI_Action action = QUEUE.peek();

        if (action == null) {
            return;
        }

        DAI_ActionLogic.execute(action);

        completeCurrent();
    }

    public static int selectedIndex() {
        return selectedIndex;
    }

    public static DAI_Action selected() {

        if (QUEUE.isEmpty()) {
            return null;
        }

        List<DAI_Action> actions = List.copyOf(QUEUE);

        if (selectedIndex >= actions.size()) {
            selectedIndex = actions.size() - 1;
        }

        if (selectedIndex < 0) {
            selectedIndex = 0;
        }

        return actions.get(selectedIndex);
    }

    public static void previous() {

        if (selectedIndex > 0) {
            selectedIndex--;
        }
    }

    public static void next() {

        if (selectedIndex < QUEUE.size() - 1) {
            selectedIndex++;
        }
    }

    public static void setRefreshCallback(Runnable callback) {
        refreshCallback = callback;
    }

    public static void delay(int ticks) {
        delayTicks = ticks;
    }

    public static int delayTicks() {
        return delayTicks;
    }

    public static void completeCurrent() {

        if (QUEUE.isEmpty()) {
            return;
        }

        QUEUE.poll();

        if (selectedIndex >= QUEUE.size()) {
            selectedIndex = Math.max(0, QUEUE.size() - 1);
        }

        if (!QUEUE.isEmpty()) {
            delayTicks = QUEUE.peek().ticks();
        } else {
            delayTicks = 0;
        }

        refreshCallback.run();
    }
    public static void enqueueExpanded(DAI_Action action) {

        // Expand sequences.
        if ("sequence".equals(action.type())) {

            for (DAI_Action child : action.sequence()) {
                enqueueExpanded(child);
            }

            return;
        }

        // Resolve datapack action references.
        if (!action.action().isEmpty()) {

            Identifier id = Identifier.tryParse(
                    "decisions_and_impulses:" + action.action()
            );

            if (id == null) {
                DAI.LOGGER.warn(
                        "<DAI>: Invalid action reference '{}'",
                        action.action()
                );
                return;
            }

            DAI_Action resolved = DAI_ActionManager.get(id);

            if (resolved == null) {
                DAI.LOGGER.warn(
                        "<DAI>: Unknown datapack action '{}'",
                        action.action()
                );
                return;
            }

            enqueueExpanded(resolved);
            return;
        }

        // Atomic action.
        enqueue(action);
    }
}