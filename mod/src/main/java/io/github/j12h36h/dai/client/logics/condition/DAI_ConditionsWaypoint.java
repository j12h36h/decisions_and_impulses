package io.github.j12h36h.dai.client.logics.condition;

import io.github.j12h36h.dai.client.logics.DAI_WaypointLogic;

public final class DAI_ConditionsWaypoint {

    private DAI_ConditionsWaypoint() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "waypoint_known",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_WaypointLogic.known(
                                        condition.parameter()
                                )
                        )
        );

        DAI_ConditionRegistry.register(
                "waypoint_in_dimension",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_WaypointLogic.knownInCurrentDimension(
                                        condition.parameter()
                                )
                        )
        );

        DAI_ConditionRegistry.register(
                "distance_to_waypoint",
                (context, condition) -> {

                    double distance =
                            DAI_WaypointLogic.distanceTo(
                                    condition.parameter()
                            );

                    if (!Double.isFinite(distance)) {

                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            distance
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "at_waypoint",
                (context, condition) -> {

                    String name =
                            condition.parameter();

                    if (
                            name == null
                                    || name.isBlank()
                    ) {

                        return DAI_ConditionValue.missing();
                    }

                    double radius =
                            condition.numberValue();

                    if (
                            !Double.isFinite(radius)
                                    || radius < 0.0D
                    ) {

                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            DAI_WaypointLogic.atWaypoint(
                                    name,
                                    radius
                            )
                    );
                }
        );
    }
}