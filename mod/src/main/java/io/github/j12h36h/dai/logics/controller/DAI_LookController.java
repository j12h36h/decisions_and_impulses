package io.github.j12h36h.dai.logics.controller;

import io.github.j12h36h.dai.logics.input.DAI_InputLook;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import net.minecraft.client.Minecraft;

public final class DAI_LookController {

    private DAI_LookController() {
        // Utility class.
    }

    /**
     * Synchronizes DAI's requested look state to the player's current
     * rotation without changing the player's camera.
     *
     * This prevents an old autonomous yaw/pitch from being replayed later
     * when managed input is enabled again after Stop, menu changes, or a
     * session transition.
     */
    public static void reset() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {

            DAI_InputState
                    .look()
                    .setRotation(
                            0.0F,
                            0.0F
                    );

            return;
        }

        DAI_InputState
                .look()
                .setRotation(
                        minecraft.player.getYRot(),
                        minecraft.player.getXRot()
                );
    }

    public static void tick() {

        /*
         * When vanilla keybinds are enabled, allow Minecraft to control
         * the player's look direction normally.
         */
        if (!DAI_InputState.isOverrideEnabled()) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        DAI_InputLook look =
                DAI_InputState.look();

        float yaw =
                look.yaw();

        float pitch =
                Math.clamp(
                        look.pitch(),
                        -90.0F,
                        90.0F
                );

        minecraft.player.setYRot(
                yaw
        );

        minecraft.player.setXRot(
                pitch
        );

        minecraft.player.setYHeadRot(
                yaw
        );

        minecraft.player.setYBodyRot(
                yaw
        );
    }
}