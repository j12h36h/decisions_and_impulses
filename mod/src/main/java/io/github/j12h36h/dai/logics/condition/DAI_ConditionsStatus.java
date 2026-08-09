package io.github.j12h36h.dai.logics.condition;

import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;

public final class DAI_ConditionsStatus {

    private DAI_ConditionsStatus() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "last_action_running",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_ActionStatus.get()
                                        == DAI_ActionResult.RUNNING
                        )
        );

        DAI_ConditionRegistry.register(
                "last_action_success",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_ActionStatus.succeeded()
                        )
        );

        DAI_ConditionRegistry.register(
                "last_action_failure",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_ActionStatus.failed()
                        )
        );

        DAI_ConditionRegistry.register(
                "last_action_cancelled",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_ActionStatus.get()
                                        == DAI_ActionResult.CANCELLED
                        )
        );

        DAI_ConditionRegistry.register(
                "last_action_timed_out",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_ActionStatus.get()
                                        == DAI_ActionResult.TIMED_OUT
                        )
        );
    }
}