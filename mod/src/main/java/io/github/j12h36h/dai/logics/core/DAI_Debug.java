package io.github.j12h36h.dai.logics.core;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.logics.controller.DAI_BreakController;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

public final class DAI_Debug {

    /*
     * Hardcoded for now.
     *
     * Later:
     * Config.DEBUG_TELEMETRY.getAsBoolean()
     */
    /*
     * Hardcoded enabled during the reliability pass.
     *
     * Future config bridge:
     * Config.DEBUG_TELEMETRY.getAsBoolean()
     */
    private static final boolean ENABLED =
            true;

    public static boolean isEnabled() {

        return ENABLED;
    }

    private static final int LOG_INTERVAL_TICKS =
            20;

    private static int ticks;

    private DAI_Debug() {
        // Utility class.
    }

    public static void tick() {

        if (!ENABLED) {
            return;
        }

        ticks++;

        if (ticks < LOG_INTERVAL_TICKS) {
            return;
        }

        ticks =
                0;

        logSnapshot();
    }

    private static void logSnapshot() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_Core.LOGGER.info(
                    "<DAI:DEBUG> player=null level={}",
                    minecraft.level != null
            );

            return;
        }

        Vec3 position =
                minecraft.player.position();

        Vec3 velocity =
                minecraft.player.getDeltaMovement();

        BlockPos selectedBlock =
                DAI_TargetState.selectedBlock();

        BlockPos interactionTarget =
                DAI_ApproachController.interactionTarget();

        DAI_ActionDefinition queueHead =
                DAI_ActionQueue.peek();

        String hit =
                describeHit(
                        minecraft.hitResult
                );

        String queuedActions =
                describeQueue();

        DAI_Core.LOGGER.info(
                """
                <DAI:DEBUG>
                  PLAYER pos=({},{},{}) block={} velocity=({},{},{}) ground={} yaw={} pitch={} headYaw={} bodyYaw={}
                  HIT {}
                  INPUT forward={} strafe={} jump={} sneak={} sprint={} lookYaw={} lookPitch={}
                  ACTION current={} previous={} queueSize={} queueDelay={} queueHead={} queue={}
                  TARGET selected={} interaction={}
                  APPROACH active={} generation={}
                  BREAK active={}
                  WORLD dimension={} gameTime={}
                """,
                format(
                        position.x
                ),
                format(
                        position.y
                ),
                format(
                        position.z
                ),
                minecraft.player.blockPosition(),
                format(
                        velocity.x
                ),
                format(
                        velocity.y
                ),
                format(
                        velocity.z
                ),
                minecraft.player.onGround(),
                format(
                        minecraft.player.getYRot()
                ),
                format(
                        minecraft.player.getXRot()
                ),
                format(
                        minecraft.player.getYHeadRot()
                ),
                format(
                        minecraft.player.yBodyRot
                ),
                hit,
                format(
                        DAI_InputState
                                .movement()
                                .forward()
                ),
                format(
                        DAI_InputState
                                .movement()
                                .strafe()
                ),
                DAI_InputState
                        .movement()
                        .jump(),
                DAI_InputState
                        .movement()
                        .sneak(),
                DAI_InputState
                        .movement()
                        .sprint(),
                format(
                        DAI_InputState
                                .look()
                                .yaw()
                ),
                format(
                        DAI_InputState
                                .look()
                                .pitch()
                ),
                DAI_ActionStatus.get(),
                DAI_ActionStatus.previous(),
                DAI_ActionQueue.size(),
                DAI_ActionQueue.delayTicks(),
                queueHead == null
                        ? "none"
                        : queueHead.type(),
                queuedActions,
                selectedBlock,
                interactionTarget,
                DAI_ApproachController.isActive(),
                DAI_ApproachController.generation(),
                DAI_BreakController.isActive(),
                minecraft.level.dimension()
                        .identifier(),
                minecraft.level.getGameTime()
        );
    }

    private static String describeQueue() {

        List<DAI_ActionDefinition> actions =
                DAI_ActionQueue.actions();

        if (actions.isEmpty()) {
            return "[]";
        }

        StringBuilder builder =
                new StringBuilder(
                        "["
                );

        int count =
                Math.min(
                        8,
                        actions.size()
                );

        for (
                int index = 0;
                index < count;
                index++
        ) {

            if (index > 0) {
                builder.append(
                        ", "
                );
            }

            DAI_ActionDefinition action =
                    actions.get(
                            index
                    );

            builder.append(
                    action.type()
            );

            if (
                    action.action() != null
                            && !action.action().isBlank()
            ) {

                builder.append(
                        "("
                );

                builder.append(
                        action.action()
                );

                builder.append(
                        ")"
                );
            }
        }

        if (actions.size() > count) {

            builder.append(
                    ", ..."
            );
        }

        builder.append(
                "]"
        );

        return builder.toString();
    }

    private static String describeHit(
            HitResult hitResult
    ) {

        if (hitResult == null) {
            return "null";
        }

        if (hitResult instanceof BlockHitResult blockHit) {

            return "BLOCK pos="
                    + blockHit.getBlockPos()
                    + " face="
                    + blockHit.getDirection();
        }

        if (hitResult instanceof EntityHitResult entityHit) {

            return "ENTITY id="
                    + entityHit.getEntity()
                    .getId()
                    + " type="
                    + entityHit.getEntity()
                    .getType();
        }

        return hitResult.getType()
                .name();
    }

    private static String format(
            double value
    ) {

        return String.format(
                Locale.ROOT,
                "%.3f",
                value
        );
    }
}