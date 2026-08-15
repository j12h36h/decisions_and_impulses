package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.client.network.DAI_ServerBridge;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.network.DAI_ServerActionPayload;
import net.minecraft.client.Minecraft;

public final class DAI_CommandLogic {

    private DAI_CommandLogic() {}

    /**
     * Submits a command exactly as the local player would type it. Server
     * permissions still apply, which makes this safe in DAI client-only mode.
     */
    public static void runCommand(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        String command = normalizedCommand(action);

        if (minecraft.getConnection() == null || command == null) {
            fail("run_command requires an active connection and command in 'action'.");
            return;
        }

        try {
            minecraft.getConnection().sendCommand(command);
            DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
            DAI_Core.debug("<DAI>: Submitted player command '{}'.", command);
        } catch (RuntimeException exception) {
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            DAI_Core.LOGGER.error("<DAI>: Failed to submit player command '{}'.", command, exception);
        }
    }

    /**
     * Backward-compatible alias for old datapacks. Unlike the pre-split
     * implementation, the client never reaches into an integrated server by
     * reflection. It requests the optional DAI server capability instead.
     */
    public static void runServerCommand(DAI_ActionDefinition action) {
        String command = normalizedCommand(action);
        if (command == null) {
            fail("run_server_command requires a command in 'action'.");
            return;
        }

        boolean sent = DAI_ServerBridge.send(new DAI_ServerActionPayload(
                "command",
                command,
                action == null ? "" : action.target(),
                action == null ? "" : Boolean.toString(action.state()),
                action == null ? 0.0D : action.value()
        ));

        DAI_ActionStatus.set(sent ? DAI_ActionResult.SUCCESS : DAI_ActionResult.FAILURE);
        if (!sent) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: run_server_command requires DAI server support; use run_command for normal player commands."
            );
        }
    }

    private static String normalizedCommand(DAI_ActionDefinition action) {
        if (action == null || !action.hasAction()) return null;
        String command = action.action().trim();
        while (command.startsWith("/")) command = command.substring(1);
        return command.isBlank() ? null : command;
    }

    private static void fail(String reason) {
        DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
        DAI_Core.LOGGER.warn("<DAI>: {}", reason);
    }
}
