package io.github.j12h36h.dai.client.reactions;

import io.github.j12h36h.dai.reactions.*;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;

public final class DAI_ReactionDispatcher {

    private DAI_ReactionDispatcher() {
        // Utility class.
    }

    public static DAI_ReactionDispatchSession begin(
            String event,
            Entity entity
    ) {
        return begin(event, entity, null, "");
    }

    public static DAI_ReactionDispatchSession begin(
            String event,
            Entity entity,
            BlockPos blockPos,
            String itemId
    ) {

        DAI_ReactionEventDefinition eventDefinition =
                DAI_ReactionEventRegistry.get(
                        event
                );

        if (eventDefinition == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot begin unknown reaction event '{}'.",
                    event
            );

            return null;
        }

        return new DAI_ReactionDispatchSession(
                eventDefinition,
                entity,
                blockPos,
                itemId
        );
    }

    public static DAI_ReactionOutcome fire(
            String event,
            DAI_ReactionPhase phase,
            Entity entity
    ) {

        DAI_ReactionDispatchSession session =
                begin(
                        event,
                        entity
                );

        if (session == null) {
            return DAI_ReactionOutcome.PASS;
        }

        DAI_ReactionOutcome outcome =
                session.fire(
                        phase
                );

        session.flush();

        return outcome;
    }
}
