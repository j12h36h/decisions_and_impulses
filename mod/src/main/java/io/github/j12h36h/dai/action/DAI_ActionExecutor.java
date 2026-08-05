package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.resources.Identifier;

public final class DAI_ActionExecutor {

    private DAI_ActionExecutor() {
        // Utility class.
    }

    public static void execute(
            String actionId
    ) {

        Identifier id =
                DAI_ActionResolver.parseReference(
                        actionId
                );

        if (id == null) {

            DAI_Core.LOGGER.error(
                    "<DAI>: Invalid action id '{}'.",
                    actionId
            );

            return;
        }

        DAI_ActionCore action =
                DAI_ActionManager.get(id);

        if (action == null) {

            DAI_Core.LOGGER.error(
                    "<DAI>: Unknown action '{}'.",
                    id
            );

            return;
        }

        DAI_ActionQueue.enqueueAll(
                DAI_ActionResolver.resolve(action)
        );
    }
}
