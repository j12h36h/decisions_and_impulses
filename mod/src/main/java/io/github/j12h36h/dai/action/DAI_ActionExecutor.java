package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.condition.DAI_Condition;
import io.github.j12h36h.dai.condition.DAI_ConditionRegistry;
import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.resources.Identifier;

public final class DAI_ActionExecutor {

    private DAI_ActionExecutor() {
        // Utility class.
    }

    public static void execute(String actionId) {
        Identifier id = DAI_ActionResolver.parseReference(actionId);
        if (id == null) {
            DAI_Core.LOGGER.error("<DAI>: Invalid action id '{}'.", actionId);
            return;
        }

        DAI_ActionCore action = DAI_ActionManager.get(id);
        if (action == null) {
            DAI_Core.LOGGER.error("<DAI>: Unknown action '{}'.", id);
            return;
        }

        for (DAI_Condition condition : action.conditions()) {
            if (!DAI_ConditionRegistry.evaluate(condition)) {
                DAI_Core.LOGGER.debug("<DAI>: Action '{}' was blocked by '{}'.", id, condition.type());
                return;
            }
        }

        DAI_ActionCore withoutTopConditions = new DAI_ActionCore(
                action.type(), action.action(), java.util.List.of(), action.sequence(),
                action.menu(), action.open(), action.yaw(), action.pitch(), action.direction(), action.ticks()
        );
        DAI_ActionQueue.enqueueAll(DAI_ActionResolver.resolve(withoutTopConditions));
    }
}
