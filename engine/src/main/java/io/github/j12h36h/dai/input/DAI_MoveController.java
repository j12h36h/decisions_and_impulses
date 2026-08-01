package io.github.j12h36h.dai.input;

import io.github.j12h36h.dai.core.DAI;
import net.minecraft.client.Minecraft;

public final class DAI_MoveController {

    private static boolean active;
    private static int remainingTicks;


    private DAI_MoveController() {
    }


    public static void start(float forward, float sideways, int ticks) {
        Input_Manager.movement().setMovement(forward, sideways);
        remainingTicks = ticks;
        active = true;
    }

    public static void tick() {

        if (!active) {
            return;
        }

        if (--remainingTicks <= 0) {
            stop();
        }
    }

    public static void stop() {
        Input_Manager.clearMovement();
        active = false;
    }
}