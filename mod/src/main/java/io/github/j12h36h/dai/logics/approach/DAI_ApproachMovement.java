package io.github.j12h36h.dai.logics.approach;

import io.github.j12h36h.dai.logics.input.DAI_InputState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DAI_ApproachMovement {

    private static final float TRAVEL_TARGET_PITCH =
            5.0F;

    private static final float MAX_TRAVEL_PITCH_STEP =
            6.0F;

    private static final float MAX_TRAVEL_YAW_STEP =
            12.0F;

    /*
     * Vertical swimming should not rapidly alternate between jump and
     * sneak when the path node is approximately level with the player.
     */
    private static final double WATER_VERTICAL_DEADZONE =
            0.30D;

    /*
     * Swimming camera pitch is intentionally limited.
     *
     * Vertical motion is primarily controlled by jump/sneak. The camera
     * merely indicates the direction of travel without staring straight
     * upward or downward.
     */
    private static final float MAX_SWIM_PITCH =
            45.0F;

    private DAI_ApproachMovement() {
        // Utility class.
    }

    /**
     * Smoothly aims the travel camera toward the current steering point.
     *
     * On land, DAI maintains the existing shallow travel pitch.
     *
     * In water, pitch follows the vertical component of the steering
     * target so upward/downward swim paths remain visually coherent.
     *
     * The returned yaw must also be used for movement projection during
     * this tick so camera rotation does not change the intended world-space
     * travel direction.
     */
    public static float maintainTravelLook(
            Minecraft minecraft,
            Vec3 steeringPoint
    ) {

        if (
                minecraft.player == null
                        || steeringPoint == null
        ) {
            return 0.0F;
        }

        double deltaX =
                steeringPoint.x
                        - minecraft.player.getX();

        double deltaY =
                steeringPoint.y
                        - minecraft.player.getY();

        double deltaZ =
                steeringPoint.z
                        - minecraft.player.getZ();

        double horizontalDistance =
                Math.sqrt(
                        deltaX * deltaX
                                + deltaZ * deltaZ
                );

        float currentYaw =
                minecraft.player.getYRot();

        float desiredYaw =
                currentYaw;

        if (
                horizontalDistance
                        > 0.001D
        ) {

            desiredYaw =
                    (float) (
                            Math.toDegrees(
                                    Math.atan2(
                                            deltaZ,
                                            deltaX
                                    )
                            )
                                    - 90.0D
                    );
        }

        float yawDifference =
                Mth.wrapDegrees(
                        desiredYaw
                                - currentYaw
                );

        float yawStep =
                Mth.clamp(
                        yawDifference,
                        -MAX_TRAVEL_YAW_STEP,
                        MAX_TRAVEL_YAW_STEP
                );

        float finalYaw =
                Mth.wrapDegrees(
                        currentYaw
                                + yawStep
                );

        float desiredPitch =
                TRAVEL_TARGET_PITCH;

        /*
         * Minecraft pitch:
         *
         * negative = look upward
         * positive = look downward
         */
        if (minecraft.player.isInWater()) {

            if (
                    Math.abs(
                            deltaY
                    )
                            > WATER_VERTICAL_DEADZONE
            ) {

                desiredPitch =
                        (float) (
                                -Math.toDegrees(
                                        Math.atan2(
                                                deltaY,
                                                Math.max(
                                                        0.001D,
                                                        horizontalDistance
                                                )
                                        )
                                )
                        );

                desiredPitch =
                        Mth.clamp(
                                desiredPitch,
                                -MAX_SWIM_PITCH,
                                MAX_SWIM_PITCH
                        );
            } else {

                /*
                 * Near-level swimming should remain approximately
                 * horizontal rather than using the terrestrial 5° pitch.
                 */
                desiredPitch =
                        0.0F;
            }
        }

        float currentPitch =
                minecraft.player.getXRot();

        float pitchDifference =
                desiredPitch
                        - currentPitch;

        float pitchStep =
                Mth.clamp(
                        pitchDifference,
                        -MAX_TRAVEL_PITCH_STEP,
                        MAX_TRAVEL_PITCH_STEP
                );

        float finalPitch =
                Mth.clamp(
                        currentPitch
                                + pitchStep,
                        -90.0F,
                        90.0F
                );

        DAI_InputState
                .look()
                .setRotation(
                        finalYaw,
                        finalPitch
                );

        return finalYaw;
    }

    /**
     * Converts a world-space direction toward the supplied target position
     * into Minecraft-local forward and strafe movement.
     *
     * Positive strafe represents Minecraft's left input.
     *
     * When swimming, this also handles vertical movement:
     *
     * target above -> jump
     * target below -> sneak
     * target level -> neither
     */
    public static MovementResult moveToward(
            Minecraft minecraft,
            Vec3 targetPosition,
            float movementYaw
    ) {

        if (
                minecraft.player == null
                        || targetPosition == null
        ) {
            return null;
        }

        double deltaX =
                targetPosition.x
                        - minecraft.player.getX();

        double deltaY =
                targetPosition.y
                        - minecraft.player.getY();

        double deltaZ =
                targetPosition.z
                        - minecraft.player.getZ();

        double horizontalLength =
                Math.sqrt(
                        deltaX * deltaX
                                + deltaZ * deltaZ
                );

        boolean swimming =
                minecraft.player.isInWater();

        /*
         * Water movement is genuinely three-dimensional.
         *
         * Do this before horizontal projection so a node directly above or
         * below the player still produces useful input.
         */
        if (swimming) {

            applyWaterVerticalMovement(
                    deltaY
            );
        } else {

            /*
             * Approach movement should never retain a downward-swim sneak
             * after leaving water.
             *
             * Jump remains owned by the terrestrial path follower.
             */
            DAI_InputState
                    .movement()
                    .setSneak(
                            false
                    );
        }

        /*
         * A vertically aligned water node may have effectively zero X/Z
         * distance. In that case vertical jump/sneak input above is the
         * complete movement command.
         */
        if (
                horizontalLength
                        <= 0.001D
        ) {

            DAI_InputState
                    .movement()
                    .setMovement(
                            0.0F,
                            0.0F
                    );

            return new MovementResult(
                    deltaX,
                    deltaY,
                    deltaZ,
                    horizontalLength,
                    0.0D,
                    0.0D,
                    movementYaw,
                    0.0F,
                    0.0F,
                    swimming
            );
        }

        double worldX =
                deltaX
                        / horizontalLength;

        double worldZ =
                deltaZ
                        / horizontalLength;

        double yawRadians =
                Math.toRadians(
                        movementYaw
                );

        /*
         * Minecraft local movement axes:
         *
         * yaw   0 = +Z
         * yaw  90 = -X
         *
         * Positive DAI strafe maps to Minecraft's LEFT input.
         */
        double forwardX =
                -Math.sin(
                        yawRadians
                );

        double forwardZ =
                Math.cos(
                        yawRadians
                );

        double leftX =
                Math.cos(
                        yawRadians
                );

        double leftZ =
                Math.sin(
                        yawRadians
                );

        float forward =
                (float) (
                        worldX * forwardX
                                + worldZ * forwardZ
                );

        float strafe =
                (float) (
                        worldX * leftX
                                + worldZ * leftZ
                );

        forward =
                Mth.clamp(
                        forward,
                        -1.0F,
                        1.0F
                );

        strafe =
                Mth.clamp(
                        strafe,
                        -1.0F,
                        1.0F
                );

        DAI_InputState
                .movement()
                .setMovement(
                        forward,
                        strafe
                );

        return new MovementResult(
                deltaX,
                deltaY,
                deltaZ,
                horizontalLength,
                worldX,
                worldZ,
                movementYaw,
                forward,
                strafe,
                swimming
        );
    }

    /**
     * Applies vertical swimming intent without changing horizontal input.
     */
    private static void applyWaterVerticalMovement(
            double deltaY
    ) {

        if (
                deltaY
                        > WATER_VERTICAL_DEADZONE
        ) {

            DAI_InputState
                    .movement()
                    .setJump(
                            true
                    );

            DAI_InputState
                    .movement()
                    .setSneak(
                            false
                    );

            return;
        }

        if (
                deltaY
                        < -WATER_VERTICAL_DEADZONE
        ) {

            DAI_InputState
                    .movement()
                    .setJump(
                            false
                    );

            DAI_InputState
                    .movement()
                    .setSneak(
                            true
                    );

            return;
        }

        /*
         * Path node is approximately level with the player.
         */
        DAI_InputState
                .movement()
                .setJump(
                        false
                );

        DAI_InputState
                .movement()
                .setSneak(
                        false
                );
    }

    public static double horizontalDistance(
            Vec3 first,
            Vec3 second
    ) {

        if (
                first == null
                        || second == null
        ) {
            return Double.POSITIVE_INFINITY;
        }

        double deltaX =
                second.x
                        - first.x;

        double deltaZ =
                second.z
                        - first.z;

        return Math.sqrt(
                deltaX * deltaX
                        + deltaZ * deltaZ
        );
    }

    public record MovementResult(
            double deltaX,
            double deltaY,
            double deltaZ,
            double horizontalLength,
            double worldX,
            double worldZ,
            float yaw,
            float forward,
            float strafe,
            boolean swimming
    ) {
    }
}