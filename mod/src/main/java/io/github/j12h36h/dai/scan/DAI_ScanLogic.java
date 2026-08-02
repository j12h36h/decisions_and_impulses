package io.github.j12h36h.dai.scan;

import io.github.j12h36h.dai.action.DAI_ActionCore;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.input.DAI_InputController;
import io.github.j12h36h.dai.input.DAI_InputTargeting;
import io.github.j12h36h.dai.input.DAI_TargetController;
import net.minecraft.world.entity.LivingEntity;

public final class DAI_ScanLogic {

    private DAI_ScanLogic() {
        // Utility class.
    }

    public static void execute(DAI_ActionCore action) {
        LivingEntity target = DAI_InputTargeting.nearestLivingEntity();
        if (target == null) {
            DAI_TargetController.clear();
            DAI_Core.LOGGER.debug("<DAI>: Scan found no living target.");
            return;
        }

        DAI_InputTargeting.Rotation rotation = DAI_InputTargeting.rotationTo(target);
        if (rotation == null) {
            DAI_TargetController.clear();
            return;
        }

        DAI_TargetController.select(target);
        DAI_InputController.look().setRotation(rotation.yaw(), rotation.pitch());

        DAI_Core.LOGGER.debug(
                "<DAI>: Scan selected '{}' (yaw={}, pitch={}).",
                target.getName().getString(),
                rotation.yaw(),
                rotation.pitch()
        );
    }
}
