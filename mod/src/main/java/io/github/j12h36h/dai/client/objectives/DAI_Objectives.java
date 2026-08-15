package io.github.j12h36h.dai.client.objectives;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionResolver;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Public objective facade. Objectives are high-level datapack definitions
 * resolved through the existing, proven action execution runtime.
 */
public final class DAI_Objectives {

    private DAI_Objectives() {
        // Utility class.
    }

    public static boolean exists(Identifier id) {
        return id != null && DAI_ActionLibrary.contains(id);
    }

    public static DAI_ActionDefinition get(Identifier id) {
        return id == null ? null : DAI_ActionLibrary.get(id);
    }

    public static boolean start(String objectiveId) {
        if (objectiveId == null || objectiveId.isBlank()) {
            return false;
        }

        List<DAI_ActionDefinition> resolved =
                DAI_ActionResolver.resolve(objectiveId.trim());

        if (resolved.isEmpty()) {
            return false;
        }

        DAI_ActionQueue.enqueueAll(resolved);
        return true;
    }
}
