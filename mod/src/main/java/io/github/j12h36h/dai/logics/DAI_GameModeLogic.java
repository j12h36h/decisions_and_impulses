package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.Set;

public final class DAI_GameModeLogic {

    private static final Set<String> SUPPORTED_GAME_MODES =
            Set.of(
                    "survival",
                    "creative",
                    "adventure",
                    "spectator"
            );

    private DAI_GameModeLogic() {
        // Utility class.
    }

    /**
     * Requests a gamemode change through Minecraft's normal command
     * system.
     *
     * The requested gamemode is supplied through action.action().
     *
     * Supported values:
     *
     * survival
     * creative
     * adventure
     * spectator
     *
     * The server remains authoritative and may reject the command when
     * the player does not have sufficient permission.
     */
    public static void setGameMode(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.getConnection() == null
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot change gamemode without an active player and server connection."
            );

            return;
        }

        if (
                action == null
                        || !action.hasAction()
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: set_gamemode requires a gamemode in 'action'."
            );

            return;
        }

        String gameMode =
                normalize(
                        action.action()
                );

        if (!SUPPORTED_GAME_MODES.contains(gameMode)) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Unsupported gamemode '{}'. Expected survival, creative, adventure, or spectator.",
                    action.action()
            );

            return;
        }

        try {

            minecraft.getConnection()
                    .sendCommand(
                            "gamemode "
                                    + gameMode
                    );

            /*
             * This means the command was successfully submitted.
             * The server may still reject it due to insufficient
             * permission.
             */
            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            DAI_Core.LOGGER.debug(
                    "<DAI>: Submitted gamemode change request '{}'.",
                    gameMode
            );

        } catch (RuntimeException exception) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.error(
                    "<DAI>: Failed to submit gamemode change request '{}'.",
                    gameMode,
                    exception
            );
        }
    }

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}
