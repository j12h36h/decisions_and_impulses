package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.condition.DAI_Condition;
import io.github.j12h36h.dai.condition.DAI_ConditionLogic;
import io.github.j12h36h.dai.core.DAI;
import io.github.j12h36h.dai.input.DAI_Input;
import net.minecraft.resources.Identifier;
public final class DAI_ActionExecutor {

    private DAI_ActionExecutor() {
    }

    public static void execute(String actionId) {

        Identifier id = Identifier.tryParse(actionId);

        if (id == null) {
            DAI.LOGGER.error("<DAI>: Invalid action id '{}'", actionId);
            return;
        }

        DAI_Action action = DAI_ActionManager.get(id);

        if (action == null) {
            DAI.LOGGER.error("<DAI>: Unknown action '{}'", id);
            return;
        }

        for (DAI_Condition condition : action.conditions()) {

            if (!DAI_ConditionLogic.evaluate(condition)) {
                return;
            }
        }

        switch (action.type()) {

            case "open_inventory" ->
                    DAI_ActionLogic.openInventory();

            case "pause_menu" ->
                    DAI_ActionLogic.openPauseMenu();

            case "update_menu" ->
                    DAI_ActionLogic.updateMenu(
                            action.menu(),
                            action.open()
                    );

            default ->
                    DAI.LOGGER.warn(
                            "<DAI>: Unknown action type '{}'",
                            action.type()
                    );
        }
    }
}