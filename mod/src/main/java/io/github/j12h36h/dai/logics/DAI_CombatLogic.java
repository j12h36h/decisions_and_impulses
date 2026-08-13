package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.controller.DAI_CombatController;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class DAI_CombatLogic {

    private DAI_CombatLogic() {
        // Utility class.
    }

    /**
     * Performs a normal one-shot attack using the current crosshair target.
     */
    public static void attackBasic(
            DAI_ActionDefinition action
    ) {

        DAI_CombatController.requestAttack();
    }

    /**
     * Attacks the currently selected entity target.
     *
     * The selected target is validated and faced before the attack
     * request is passed to DAI_ActionController.
     */
    public static void attackTarget(
            DAI_ActionDefinition action
    ) {

        Entity selectedTarget =
                DAI_TargetState.selected();

        if (
                !(selectedTarget
                        instanceof LivingEntity livingTarget)
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot attack because no living target is selected."
            );

            return;
        }

        if (
                livingTarget.isRemoved()
                        || !livingTarget.isAlive()
        ) {

            DAI_TargetState.clearEntity();

            DAI_Core.debug(
                    "<DAI>: Selected attack target is no longer valid."
            );

            return;
        }

        DAI_CombatController.engage(
                livingTarget
        );
    }

    /**
     * Begins held attack input.
     */
    public static void attackStart(
            DAI_ActionDefinition action
    ) {

        DAI_CombatController.startAttack();
    }

    /**
     * Releases held attack input.
     */
    public static void attackStop(
            DAI_ActionDefinition action
    ) {

        DAI_CombatController.stopAttack();
    }
}
