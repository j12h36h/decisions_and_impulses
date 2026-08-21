package io.github.j12h36h.dai.client.logics.input;

import io.github.j12h36h.dai.client.network.DAI_ServerBridge;
import io.github.j12h36h.dai.network.DAI_VehicleInputPayload;
import net.minecraft.client.Minecraft;

/** Publishes ordinary physical movement input while the local player rides. */
public final class DAI_VehicleInputBridge {

    private DAI_VehicleInputBridge() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.isPassenger()) return;

        float forward = 0.0F;
        float strafe = 0.0F;
        if (minecraft.options.keyUp.isDown()) forward += 1.0F;
        if (minecraft.options.keyDown.isDown()) forward -= 1.0F;
        if (minecraft.options.keyLeft.isDown()) strafe += 1.0F;
        if (minecraft.options.keyRight.isDown()) strafe -= 1.0F;

        DAI_ServerBridge.send(new DAI_VehicleInputPayload(
                forward,
                strafe,
                minecraft.options.keyJump.isDown(),
                minecraft.options.keyShift.isDown(),
                minecraft.options.keySprint.isDown(),
                minecraft.player.getYRot(),
                minecraft.player.getXRot()
        ));
    }
}
