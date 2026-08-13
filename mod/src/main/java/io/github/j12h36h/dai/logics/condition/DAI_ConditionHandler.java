package io.github.j12h36h.dai.logics.condition;

public final class DAI_ConditionHandler {

    private DAI_ConditionHandler() {
        // Utility class.
    }

    public static void initialize() {

        DAI_ConditionRegistry.clear();

        DAI_ConditionsRegistry.registerAll();

        DAI_ConditionsPlayer.registerAll();
        DAI_ConditionsMode.registerAll();
        DAI_ConditionsStatus.registerAll();
        DAI_ConditionsMovement.registerAll();
        DAI_ConditionsPosition.registerAll();
        DAI_ConditionsGeometry.registerAll();

        DAI_ConditionsTarget.registerAll();
        DAI_ConditionsEntity.registerAll();
        DAI_ConditionsNearby.registerAll();
        DAI_ConditionsNearbyItem.registerAll();

        DAI_ConditionsInventory.registerAll();
        DAI_ConditionsItem.registerAll();
        DAI_ConditionsEquipment.registerAll();
        DAI_ConditionsTag.registerAll();

        DAI_ConditionsWorld.registerAll();
        DAI_ConditionsEnvironment.registerAll();
        DAI_ConditionsBlock.registerAll();

        DAI_ConditionsCombat.registerAll();
        DAI_ConditionsEffect.registerAll();
        DAI_ConditionsNavigation.registerAll();
        DAI_ConditionsScaffold.registerAll();
        DAI_ConditionsAdvancement.registerAll();
        DAI_ConditionsKnowledge.registerAll();
        DAI_ConditionsMovementProgress.registerAll();

        DAI_ConditionsWaypoint.registerAll();
        DAI_ConditionsSpatial.registerAll();
    }
}