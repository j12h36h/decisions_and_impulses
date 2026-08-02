package io.github.j12h36h.dai.input;

import net.minecraft.client.Minecraft;

public final class DAI_LookController {

    private DAI_LookController() {
        // Utility class.
    }

    public static void tick() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        DAI_InputLook look =
                DAI_InputController.look();

        float yaw = look.yaw();
        float pitch = Math.clamp(
                look.pitch(),
                -90.0F,
                90.0F
        );

        minecraft.player.setYRot(yaw);
        minecraft.player.setXRot(pitch);

        minecraft.player.setYHeadRot(yaw);
        minecraft.player.setYBodyRot(yaw);
    }
}