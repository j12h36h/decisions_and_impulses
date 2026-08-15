package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.controller.DAI_MoveController;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import net.minecraft.client.Minecraft;

import java.util.Locale;

public final class DAI_MoveLogic {

    private static final int DEFAULT_DIRECTIONAL_JUMP_TICKS =
            5;

    private static final int JUMP_COOLDOWN_TICKS =
            10;

    private static long nextJumpTick;

    private DAI_MoveLogic() {
        // Utility class.
    }

    public static void move(
            DAI_ActionDefinition action
    ) {

        startDirectionalMovement(
                action.direction(),
                action.ticks()
        );
    }

    public static void jump(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot jump because the player or level is unavailable."
            );

            return;
        }

        long currentTick =
                minecraft.level.getGameTime();

        if (
                currentTick
                        < nextJumpTick
        ) {

            DAI_Core.debug(
                    "<DAI>: Jump blocked by cooldown; remaining={} tick(s).",
                    nextJumpTick
                            - currentTick
            );

            return;
        }

        if (!minecraft.player.onGround()) {

            DAI_Core.debug(
                    "<DAI>: Jump blocked because the player is airborne."
            );

            return;
        }

        String direction =
                normalize(
                        action.direction()
                );

        /*
         * An empty direction performs a normal stationary jump.
         */
        if (direction.isEmpty()) {

            nextJumpTick =
                    currentTick
                            + JUMP_COOLDOWN_TICKS;

            jumpPlayer(
                    minecraft
            );

            DAI_Core.debug(
                    "<DAI>: Stationary jump requested; cooldown={}.",
                    JUMP_COOLDOWN_TICKS
            );

            return;
        }

        int movementTicks =
                action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_DIRECTIONAL_JUMP_TICKS;

        if (
                !startDirectionalMovement(
                        direction,
                        movementTicks
                )
        ) {
            return;
        }

        nextJumpTick =
                currentTick
                        + JUMP_COOLDOWN_TICKS;

        jumpPlayer(
                minecraft
        );

        DAI_Core.debug(
                "<DAI>: Directional jump requested direction='{}', ticks={}, cooldown={}.",
                direction,
                movementTicks,
                JUMP_COOLDOWN_TICKS
        );
    }

    public static void crouchToggle(
            DAI_ActionDefinition action
    ) {

        boolean enabled =
                !DAI_InputState
                        .movement()
                        .sneak();

        DAI_InputState
                .movement()
                .setSneak(
                        enabled
                );

        DAI_Core.debug(
                "<DAI>: Crouch input {}.",
                enabled
                        ? "enabled"
                        : "disabled"
        );
    }

    public static void crouchSet(
            DAI_ActionDefinition action
    ) {

        DAI_InputState
                .movement()
                .setSneak(
                        action.state()
                );

        DAI_Core.debug(
                "<DAI>: Crouch input {}.",
                action.state()
                        ? "enabled"
                        : "disabled"
        );
    }

    public static void sprintToggle(
            DAI_ActionDefinition action
    ) {

        boolean enabled =
                !DAI_InputState
                        .movement()
                        .sprint();

        DAI_InputState
                .movement()
                .setSprint(
                        enabled
                );

        DAI_Core.debug(
                "<DAI>: Sprint input {}.",
                enabled
                        ? "enabled"
                        : "disabled"
        );
    }

    public static void sprintSet(
            DAI_ActionDefinition action
    ) {

        DAI_InputState
                .movement()
                .setSprint(
                        action.state()
                );

        DAI_Core.debug(
                "<DAI>: Sprint input {}.",
                action.state()
                        ? "enabled"
                        : "disabled"
        );
    }

    public static void swimToggle(
            DAI_ActionDefinition action
    ) {

        DAI_MoveController.toggleSwim();
    }

    public static void swimSet(
            DAI_ActionDefinition action
    ) {

        DAI_MoveController.setSwim(
                action.state()
        );
    }

    public static void stopAll(
            DAI_ActionDefinition action
    ) {

        DAI_MoveController.reset();

        nextJumpTick =
                0L;

        DAI_Core.debug(
                "<DAI>: Stopped all managed input."
        );
    }

    private static boolean startDirectionalMovement(
            String direction,
            int ticks
    ) {

        String normalizedDirection =
                normalize(
                        direction
                );

        if (normalizedDirection.equals("stop")) {

            DAI_MoveController.stop();

            return true;
        }

        if (ticks <= 0) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot move '{}' for {} tick(s).",
                    normalizedDirection,
                    ticks
            );

            return false;
        }

        return switch (normalizedDirection) {

            case "forward" -> {

                DAI_MoveController.start(
                        1.0F,
                        0.0F,
                        ticks
                );

                yield true;
            }

            case "backward" -> {

                DAI_MoveController.start(
                        -1.0F,
                        0.0F,
                        ticks
                );

                yield true;
            }

            case "left" -> {

                DAI_MoveController.start(
                        0.0F,
                        1.0F,
                        ticks
                );

                yield true;
            }

            case "right" -> {

                DAI_MoveController.start(
                        0.0F,
                        -1.0F,
                        ticks
                );

                yield true;
            }

            default -> {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Unknown movement direction '{}'.",
                        direction
                );

                yield false;
            }
        };
    }

    private static void jumpPlayer(
            Minecraft minecraft
    ) {

        if (minecraft.player == null) {
            return;
        }

        DAI_Core.debug(
                "<DAI>: Requesting player jump."
        );

        minecraft.player.jumpFromGround();
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