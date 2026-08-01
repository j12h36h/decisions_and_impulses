package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class DAI_ActionManager {

    private static final Map<Identifier, DAI_Action> ACTIONS = new HashMap<>();

    private DAI_ActionManager() {
        // Utility class.
    }

    public static void register(Identifier id, DAI_Action action) {

        DAI.LOGGER.info("<DAI>: Registering action {}", id);

        ACTIONS.put(id, action);

        DAI.LOGGER.info("<DAI>: Action count = {}", ACTIONS.size());
    }

    public static DAI_Action get(Identifier id) {

        DAI.LOGGER.info("<DAI>: Lookup {}", id);
        DAI.LOGGER.info("<DAI>: Keys = {}", ACTIONS.keySet());

        return ACTIONS.get(id);
    }

    public static boolean contains(Identifier id) {
        return ACTIONS.containsKey(id);
    }

    public static void clear() {
        ACTIONS.clear();
    }
}
