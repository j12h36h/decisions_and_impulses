package io.github.j12h36h.dai.client.logics.condition;

import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.client.logics.controller.DAI_ScaffoldController;

/** Runtime conditions exposed for datapack scaffold recovery. */
public final class DAI_ConditionsScaffold {

    private DAI_ConditionsScaffold() {
        // Utility class.
    }

    public static void registerAll() {


        DAI_ConditionRegistry.register(
                "approach_last_success",
                (context, condition) -> {

                    int generation = DAI_ApproachController.generation();

                    return DAI_ConditionValue.bool(
                            generation > 0
                                    && !DAI_ApproachController.isActive()
                                    && DAI_ApproachController.resultForGeneration(generation)
                                    == DAI_ActionResult.SUCCESS
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "approach_last_failed",
                (context, condition) -> {

                    int generation = DAI_ApproachController.generation();

                    if (generation <= 0 || DAI_ApproachController.isActive()) {
                        return DAI_ConditionValue.bool(false);
                    }

                    DAI_ActionResult result =
                            DAI_ApproachController.resultForGeneration(generation);

                    return DAI_ConditionValue.bool(
                            result == DAI_ActionResult.FAILURE
                                    || result == DAI_ActionResult.TIMED_OUT
                                    || result == DAI_ActionResult.CANCELLED
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "scaffold_last_success",
                (context, condition) -> {

                    int generation = DAI_ScaffoldController.generation();

                    return DAI_ConditionValue.bool(
                            generation > 0
                                    && !DAI_ScaffoldController.isActive()
                                    && DAI_ScaffoldController.resultForGeneration(generation)
                                    == DAI_ActionResult.SUCCESS
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "scaffold_last_failed",
                (context, condition) -> {

                    int generation = DAI_ScaffoldController.generation();

                    if (generation <= 0 || DAI_ScaffoldController.isActive()) {
                        return DAI_ConditionValue.bool(false);
                    }

                    DAI_ActionResult result =
                            DAI_ScaffoldController.resultForGeneration(generation);

                    return DAI_ConditionValue.bool(
                            result == DAI_ActionResult.FAILURE
                                    || result == DAI_ActionResult.TIMED_OUT
                                    || result == DAI_ActionResult.CANCELLED
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "scaffold_active",
                (context, condition) ->
                        DAI_ConditionValue.bool(
                                DAI_ScaffoldController.isActive()
                        )
        );

        DAI_ConditionRegistry.register(
                "scaffold_used_count",
                (context, condition) ->
                        DAI_ConditionValue.number(
                                DAI_ScaffoldController.usedCount()
                        )
        );

        DAI_ConditionRegistry.register(
                "scaffold_material_count",
                (context, condition) ->
                        DAI_ConditionValue.number(
                                DAI_ScaffoldController.availableMaterialCount()
                        )
        );
    }
}
