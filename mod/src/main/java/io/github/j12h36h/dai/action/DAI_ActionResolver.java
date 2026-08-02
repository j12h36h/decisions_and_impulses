package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.condition.DAI_Condition;
import io.github.j12h36h.dai.condition.DAI_ConditionRegistry;
import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DAI_ActionResolver {

    private static final int MAX_DEPTH = 64;
    private static final int MAX_EXPANDED_ACTIONS = 1024;

    private DAI_ActionResolver() {
        // Utility class.
    }

    public static List<DAI_ActionCore> resolve(DAI_ActionCore action) {
        List<DAI_ActionCore> resolved = new ArrayList<>();
        resolve(action, resolved, new HashSet<>(), 0);
        return List.copyOf(resolved);
    }

    private static void resolve(
            DAI_ActionCore action,
            List<DAI_ActionCore> output,
            Set<Identifier> resolving,
            int depth
    ) {
        if (action == null || depth >= MAX_DEPTH || output.size() >= MAX_EXPANDED_ACTIONS) {
            if (depth >= MAX_DEPTH) {
                DAI_Core.LOGGER.error("<DAI>: Action resolution exceeded depth {}.", MAX_DEPTH);
            }
            if (output.size() >= MAX_EXPANDED_ACTIONS) {
                DAI_Core.LOGGER.error("<DAI>: Action resolution exceeded {} atomic actions.", MAX_EXPANDED_ACTIONS);
            }
            return;
        }

        if (!conditionsPass(action)) {
            return;
        }

        if (!action.sequence().isEmpty() || "sequence".equals(action.type())) {
            for (DAI_ActionCore child : action.sequence()) {
                resolve(child, output, resolving, depth + 1);
            }
            return;
        }

        if (!action.action().isEmpty()) {
            Identifier id = parseReference(action.action());
            if (id == null || !resolving.add(id)) {
                DAI_Core.LOGGER.error("<DAI>: Invalid or circular action reference '{}'.", action.action());
                return;
            }

            try {
                DAI_ActionCore referenced = DAI_ActionManager.get(id);
                if (referenced == null) {
                    DAI_Core.LOGGER.error("<DAI>: Unknown action reference '{}'.", id);
                    return;
                }
                resolve(referenced, output, resolving, depth + 1);
            } finally {
                resolving.remove(id);
            }
            return;
        }

        output.add(action);
    }

    private static boolean conditionsPass(DAI_ActionCore action) {
        for (DAI_Condition condition : action.conditions()) {
            if (!DAI_ConditionRegistry.evaluate(condition)) {
                return false;
            }
        }
        return true;
    }

    public static Identifier parseReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String normalized = reference.trim();
        if (!normalized.contains(":")) {
            normalized = DAI_Core.MODID + ":" + normalized;
        }
        return Identifier.tryParse(normalized);
    }
}
