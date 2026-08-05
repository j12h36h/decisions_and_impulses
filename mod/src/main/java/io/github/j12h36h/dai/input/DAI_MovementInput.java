package io.github.j12h36h.dai.input;

import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.client.Options;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

public final class DAI_MovementInput extends KeyboardInput {

    private float lastForward;
    private float lastStrafe;

    private boolean lastJump;
    private boolean lastSneak;
    private boolean lastSprint;

    private boolean overrideActive;

    public DAI_MovementInput(Options options) {
        super(options);

        DAI_Core.LOGGER.debug(
                "<DAI>: Movement input initialized."
        );
    }

    @Override
    public void tick() {

        // Build the normal vanilla input state first.
        super.tick();

        if (!DAI_InputController.isOverrideEnabled()) {

            if (overrideActive) {

                overrideActive = false;

                DAI_Core.LOGGER.debug(
                        "<DAI>: Movement input override disabled."
                );
            }

            return;
        }

        if (!overrideActive) {

            overrideActive = true;

            DAI_Core.LOGGER.debug(
                    "<DAI>: Movement input override enabled."
            );
        }

        DAI_InputMovement movement =
                DAI_InputController.movement();

        float forward = movement.forward();
        float strafe = movement.strafe();

        boolean jump = movement.jump();
        boolean sneak = movement.sneak();
        boolean sprint = movement.sprint();

        this.keyPresses = new Input(
                forward > 0.0F,
                forward < 0.0F,
                strafe > 0.0F,
                strafe < 0.0F,
                jump,
                sneak,
                sprint
        );

        Vec2 movementVector = new Vec2(
                strafe,
                forward
        );

        this.moveVector = movementVector.lengthSquared() > 0.0F
                ? movementVector.normalized()
                : Vec2.ZERO;

        logStateChange(
                forward,
                strafe,
                jump,
                sneak,
                sprint
        );
    }

    private void logStateChange(
            float forward,
            float strafe,
            boolean jump,
            boolean sneak,
            boolean sprint
    ) {

        if (
                Float.compare(lastForward, forward) == 0
                        && Float.compare(lastStrafe, strafe) == 0
                        && lastJump == jump
                        && lastSneak == sneak
                        && lastSprint == sprint
        ) {
            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Movement input changed: forward={}, strafe={}, jump={}, sneak={}, sprint={}.",
                forward,
                strafe,
                jump,
                sneak,
                sprint
        );

        lastForward = forward;
        lastStrafe = strafe;
        lastJump = jump;
        lastSneak = sneak;
        lastSprint = sprint;
    }
}