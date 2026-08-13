package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResolver;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;

public final class DAI_ObjectiveLogic {

    private DAI_ObjectiveLogic() {
        // Utility class.
    }

    public static void execute(
            DAI_ActionDefinition action
    ) {

        if (!action.hasAction()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: objective_execute requires an objective id."
            );

            return;
        }

        Identifier objectiveId =
                parseObjectiveId(
                        action.action()
                );

        if (objectiveId == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid objective id '{}'.",
                    action.action()
            );

            return;
        }

        String[] pathParts =
                objectiveId.getPath()
                        .split("/");

        String objectiveName =
                pathParts.length >= 2
                        ? pathParts[0]
                        + "_"
                        + pathParts[pathParts.length - 1]
                        : pathParts[0];

        String objectiveAction =
                DAI_Core.MODID
                        + ":"
                        + objectiveName;

        DAI_Core.debug(
                "<DAI>: Executing objective '{}' through flattened action '{}'.",
                objectiveId,
                objectiveAction
        );

        DAI_ActionQueue.enqueueAll(
                DAI_ActionResolver.resolve(
                        objectiveAction
                )
        );
    }

    private static Identifier parseObjectiveId(
            String value
    ) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized =
                value.trim();

        /*
         * Objectives without a namespace are assumed to be vanilla
         * advancement/objective identifiers.
         */
        if (!normalized.contains(":")) {

            normalized =
                    "minecraft:"
                            + normalized;
        }

        return Identifier.tryParse(
                normalized
        );
    }
}
