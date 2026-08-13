package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;

public final class DAI_CommandLogic {

    private DAI_CommandLogic() {
        // Utility class.
    }

    public static void runCommand(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft = Minecraft.getInstance();

        if (
                minecraft.getConnection() == null
                        || action == null
                        || !action.hasAction()
        ) {
            fail("run_command requires an active connection and command in 'action'.");
            return;
        }

        String command = action.action().trim();
        while (command.startsWith("/")) {
            command = command.substring(1);
        }

        if (command.isBlank()) {
            fail("run_command received an empty command.");
            return;
        }

        try {
            minecraft.getConnection().sendCommand(command);
            DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
            DAI_Core.debug("<DAI>: Submitted command '{}'.", command);
        } catch (RuntimeException exception) {
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            DAI_Core.LOGGER.error("<DAI>: Failed to submit command '{}'.", command, exception);
        }
    }

    private static void fail(String reason) {
        DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
        DAI_Core.LOGGER.warn("<DAI>: {}", reason);
    }
}
