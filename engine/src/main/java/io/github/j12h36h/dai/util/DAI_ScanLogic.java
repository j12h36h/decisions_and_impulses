package io.github.j12h36h.dai.util;

import io.github.j12h36h.dai.action.DAI_Action;
import io.github.j12h36h.dai.core.DAI;
import io.github.j12h36h.dai.input.Input_Manager;
import net.minecraft.world.entity.Entity;

public final class DAI_ScanLogic {

    private DAI_ScanLogic() {
    }

    public static void execute(DAI_Action action) {

        switch (action.open()) {

            case "nearest" -> scanNearest();

            default ->
                    DAI.LOGGER.warn(
                            "<DAI>: Unknown scan type '{}'",
                            action.open()
                    );
        }
    }

    private static void scanNearest() {

        Entity entity = DAI_Targeting.nearestEntity();

        if (entity == null) {
            return;
        }

        Input_Manager.look().setRotation(
                DAI_Targeting.yawTo(entity),
                DAI_Targeting.pitchTo(entity)
        );

        DAI.LOGGER.info(
                "<DAI>: Nearest entity = {} ({})",
                entity.getName().getString(),
                entity.getType()
        );
    }
}