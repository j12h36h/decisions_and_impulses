package io.github.j12h36h.dai.client.reactions;

import io.github.j12h36h.dai.reactions.*;

public final class DAI_ReactionRuntime {

    private static final ThreadLocal<DAI_ReactionContext> CURRENT =
            new ThreadLocal<>();

    private DAI_ReactionRuntime() {
        // Utility class.
    }

    public static DAI_ReactionContext current() {

        return CURRENT.get();
    }

    public static DAI_ReactionContext enter(
            DAI_ReactionContext context
    ) {

        DAI_ReactionContext previous =
                CURRENT.get();

        CURRENT.set(
                context
        );

        return previous;
    }

    public static void restore(
            DAI_ReactionContext previous
    ) {

        if (previous == null) {
            CURRENT.remove();
            return;
        }

        CURRENT.set(
                previous
        );
    }
}
