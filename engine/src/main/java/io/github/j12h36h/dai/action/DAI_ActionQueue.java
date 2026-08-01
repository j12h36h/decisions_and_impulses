package io.github.j12h36h.dai.action;

import java.util.ArrayDeque;
import java.util.Queue;

public final class DAI_ActionQueue {

    private static int delayTicks;

    private static final Queue<DAI_Action> QUEUE = new ArrayDeque<>();

    private DAI_ActionQueue() {
    }

    public static void enqueue(DAI_Action action) {
        QUEUE.offer(action);
    }

    public static DAI_Action poll() {
        return QUEUE.poll();
    }

    public static void clear() {
        QUEUE.clear();
    }

    public static void delay(int ticks) {
        delayTicks = ticks;
    }

    public static void tick() {

        DAI_Action action = QUEUE.poll();

        if (action == null) {
            return;
        }

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        DAI_ActionLogic.execute(action);
    }
}