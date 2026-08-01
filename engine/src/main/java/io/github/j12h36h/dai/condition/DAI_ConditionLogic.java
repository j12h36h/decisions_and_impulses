package io.github.j12h36h.dai.condition;

import io.github.j12h36h.dai.core.DAI;

public final class DAI_ConditionLogic {

    private DAI_ConditionLogic() {
    }

    public static boolean evaluate(DAI_Condition condition) {

        return switch (condition.type()) {

            case "always" -> true;

            case "never" -> false;

            default -> {
                DAI.LOGGER.warn(
                        "<DAI>: Unknown condition '{}'",
                        condition.type()
                );

                yield false;
            }
        };
    }
}