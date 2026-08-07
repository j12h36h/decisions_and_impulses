package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.controller.DAI_InteractionController;
import io.github.j12h36h.dai.input.DAI_KeyInput;

public final class DAI_InteractionLogic {

    private DAI_InteractionLogic() {
        // Utility class.
    }

    /**
     * Performs a normal interaction with the block or entity
     * currently under the player's crosshair.
     */
    public static void interact(
            DAI_ActionDefinition action
    ) {

        DAI_InteractionController.requestInteract();
    }

    /**
     * Performs Minecraft's pick-block action against the current
     * crosshair target.
     */
    public static void pickBlock(
            DAI_ActionDefinition action
    ) {

        DAI_KeyInput.pickBlock();
    }
}
