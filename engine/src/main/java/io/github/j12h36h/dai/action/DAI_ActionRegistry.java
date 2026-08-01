package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static io.github.j12h36h.dai.action.DAI_ActionLogic.requestUpdateMenu;

public final class DAI_ActionRegistry {

    private static final Map<String, Consumer<DAI_Action>> ACTIONS =
            new HashMap<>();

    private DAI_ActionRegistry() {
    }

    public static void register(
            String id,
            Consumer<DAI_Action> executor
    ) {
        ACTIONS.put(id, executor);
    }

    public static void execute(DAI_Action action) {

        Consumer<DAI_Action> executor =
                ACTIONS.get(action.type());

        if (executor == null) {
            DAI.LOGGER.warn(
                    "<DAI>: Unknown action type '{}'",
                    action.type()
            );
            return;
        }

        executor.accept(action);
    }
}