package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class DAI_LookLogic {

    private DAI_LookLogic() {
        // Utility class.
    }

    public static void setLook(
            DAI_ActionDefinition action
    ) {

        setLook(
                action.yaw(),
                action.pitch()
        );
    }

    public static void addLook(
            DAI_ActionDefinition action
    ) {

        addLook(
                action.yaw(),
                action.pitch()
        );
    }

    public static void setLook(
            float yaw,
            float pitch
    ) {

        DAI_Core.debug(
                "<DAI>: Setting look rotation to yaw={}, pitch={}.",
                yaw,
                pitch
        );

        DAI_InputState
                .look()
                .setRotation(
                        yaw,
                        pitch
                );
    }

    public static void addLook(
            float yaw,
            float pitch
    ) {

        DAI_Core.debug(
                "<DAI>: Adding look rotation yaw={}, pitch={}.",
                yaw,
                pitch
        );

        DAI_InputState
                .look()
                .addRotation(
                        yaw,
                        pitch
                );
    }

    public static boolean lookAt(
            Entity entity
    ) {

        if (entity == null) {
            return false;
        }

        return lookAt(
                entity.getEyePosition()
        );
    }

    public static boolean lookAt(
            Vec3 position
    ) {

        if (position == null) {
            return false;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {
            return false;
        }

        Vec3 eyePosition =
                minecraft.player.getEyePosition();

        double deltaX =
                position.x
                        - eyePosition.x;

        double deltaY =
                position.y
                        - eyePosition.y;

        double deltaZ =
                position.z
                        - eyePosition.z;

        double horizontalDistance =
                Math.sqrt(
                        deltaX * deltaX
                                + deltaZ * deltaZ
                );

        float yaw =
                (float) (
                        Math.toDegrees(
                                Math.atan2(
                                        deltaZ,
                                        deltaX
                                )
                        )
                                - 90.0D
                );

        float pitch =
                (float) -Math.toDegrees(
                        Math.atan2(
                                deltaY,
                                horizontalDistance
                        )
                );

        setLook(
                yaw,
                pitch
        );

        return true;
    }

    public static void center() {

        setLook(
                0.0F,
                0.0F
        );
    }
}