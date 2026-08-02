package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
public final class DAI_ActionManager {

    private static final Map<Identifier, DAI_ActionCore> ACTIONS =
            new HashMap<>();

    private DAI_ActionManager() {
        // Utility class.
    }

    public static void register(
            Identifier id,
            DAI_ActionCore action
    ) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Action identifier cannot be null."
            );
        }

        if (action == null) {
            throw new IllegalArgumentException(
                    "Action cannot be null."
            );
        }

        DAI_ActionCore previous =
                ACTIONS.put(id, action);

        if (previous == null) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Registered action '{}'.",
                    id
            );

        } else {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Replaced existing action '{}'.",
                    id
            );
        }
    }

    public static DAI_ActionCore get(
            Identifier id
    ) {

        if (id == null) {
            return null;
        }

        return ACTIONS.get(id);
    }

    public static boolean contains(
            Identifier id
    ) {

        return id != null
                && ACTIONS.containsKey(id);
    }

    public static int size() {
        return ACTIONS.size();
    }

    public static void clear() {

        int removed = ACTIONS.size();

        ACTIONS.clear();

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared {} registered action(s).",
                removed
        );
    }
}