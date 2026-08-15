package io.github.j12h36h.dai.client.logics.condition;

import net.minecraft.world.phys.Vec3;

public final class DAI_ConditionsGeometry {

    private DAI_ConditionsGeometry() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "player_facing_target",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasTarget()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    Vec3 look =
                            context.player()
                                    .getLookAngle()
                                    .normalize();

                    Vec3 toTarget =
                            context.target()
                                    .position()
                                    .subtract(
                                            context.player()
                                                    .position()
                                    )
                                    .normalize();

                    double dot =
                            look.dot(toTarget);

                    double threshold =
                            condition.parameterNumber() != 0.0D
                                    ? condition.parameterNumber()
                                    : 0.8D;

                    return DAI_ConditionValue.bool(
                            dot >= threshold
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_in_front",
                (context, condition) -> {

                    Double dot =
                            horizontalDotToTarget(context);

                    if (dot == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            dot > 0.0D
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_behind",
                (context, condition) -> {

                    Double dot =
                            horizontalDotToTarget(context);

                    if (dot == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            dot < 0.0D
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_left",
                (context, condition) -> {

                    Double side =
                            horizontalSideToTarget(context);

                    if (side == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            side > 0.0D
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "target_right",
                (context, condition) -> {

                    Double side =
                            horizontalSideToTarget(context);

                    if (side == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            side < 0.0D
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "horizontal_angle_to_target",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasTarget()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    double deltaX =
                            context.target().getX()
                                    - context.player().getX();

                    double deltaZ =
                            context.target().getZ()
                                    - context.player().getZ();

                    double targetYaw =
                            Math.toDegrees(
                                    Math.atan2(
                                            -deltaX,
                                            deltaZ
                                    )
                            );

                    double angle =
                            wrapDegrees(
                                    targetYaw
                                            - context.player()
                                            .getYRot()
                            );

                    return DAI_ConditionValue.number(
                            angle
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "vertical_angle_to_target",
                (context, condition) -> {

                    if (
                            !context.hasPlayer()
                                    || !context.hasTarget()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    double deltaX =
                            context.target().getX()
                                    - context.player().getX();

                    double deltaY =
                            context.target().getEyeY()
                                    - context.player().getEyeY();

                    double deltaZ =
                            context.target().getZ()
                                    - context.player().getZ();

                    double horizontalDistance =
                            Math.sqrt(
                                    deltaX * deltaX
                                            + deltaZ * deltaZ
                            );

                    double angle =
                            -Math.toDegrees(
                                    Math.atan2(
                                            deltaY,
                                            horizontalDistance
                                    )
                            );

                    return DAI_ConditionValue.number(
                            angle
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "distance_to_position",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    Vec3 position =
                            parsePosition(
                                    condition.parameter()
                            );

                    if (position == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .position()
                                    .distanceTo(position)
                    );
                }
        );
    }

    private static Double horizontalDotToTarget(
            DAI_ConditionContext context
    ) {

        if (
                !context.hasPlayer()
                        || !context.hasTarget()
        ) {
            return null;
        }

        Vec3 look =
                context.player()
                        .getLookAngle();

        Vec3 horizontalLook =
                new Vec3(
                        look.x,
                        0.0D,
                        look.z
                );

        Vec3 toTarget =
                context.target()
                        .position()
                        .subtract(
                                context.player()
                                        .position()
                        );

        Vec3 horizontalTarget =
                new Vec3(
                        toTarget.x,
                        0.0D,
                        toTarget.z
                );

        if (
                horizontalLook.lengthSqr() == 0.0D
                        || horizontalTarget.lengthSqr() == 0.0D
        ) {
            return null;
        }

        return horizontalLook
                .normalize()
                .dot(
                        horizontalTarget.normalize()
                );
    }

    private static Double horizontalSideToTarget(
            DAI_ConditionContext context
    ) {

        if (
                !context.hasPlayer()
                        || !context.hasTarget()
        ) {
            return null;
        }

        Vec3 look =
                context.player()
                        .getLookAngle();

        Vec3 forward =
                new Vec3(
                        look.x,
                        0.0D,
                        look.z
                );

        Vec3 toTarget =
                context.target()
                        .position()
                        .subtract(
                                context.player()
                                        .position()
                        );

        Vec3 targetDirection =
                new Vec3(
                        toTarget.x,
                        0.0D,
                        toTarget.z
                );

        if (
                forward.lengthSqr() == 0.0D
                        || targetDirection.lengthSqr() == 0.0D
        ) {
            return null;
        }

        Vec3 normalizedForward =
                forward.normalize();

        Vec3 normalizedTarget =
                targetDirection.normalize();

        return normalizedForward.x
                * normalizedTarget.z
                - normalizedForward.z
                * normalizedTarget.x;
    }

    private static Vec3 parsePosition(
            String value
    ) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String[] parts =
                value.trim()
                        .split(",");

        if (parts.length != 3) {
            return null;
        }

        try {

            return new Vec3(
                    Double.parseDouble(
                            parts[0].trim()
                    ),
                    Double.parseDouble(
                            parts[1].trim()
                    ),
                    Double.parseDouble(
                            parts[2].trim()
                    )
            );

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static double wrapDegrees(
            double angle
    ) {

        double wrapped =
                angle % 360.0D;

        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        }

        if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }

        return wrapped;
    }
}
