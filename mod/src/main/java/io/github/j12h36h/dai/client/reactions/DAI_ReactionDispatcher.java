package io.github.j12h36h.dai.client.reactions;

import io.github.j12h36h.dai.reactions.*;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;

public final class DAI_ReactionDispatcher {

    private DAI_ReactionDispatcher() {
        // Utility class.
    }

    /**
     * Starts the logical event currently bound to a stable engine hook.
     * Datapacks may remap the hook to another event id or disable it entirely.
     */
    public static DAI_ReactionDispatchSession beginHook(
            String hook,
            Entity entity,
            BlockPos blockPos,
            String itemId,
            String itemModel
    ) {
        DAI_ReactionEventDefinition definition = DAI_ReactionEventRegistry.resolveHook(hook);
        if (definition == null) return null;
        return new DAI_ReactionDispatchSession(definition, entity, blockPos, itemId, itemModel);
    }

    public static DAI_ReactionDispatchSession beginHook(
            String hook,
            Entity entity,
            BlockPos blockPos,
            String itemId
    ) {
        return beginHook(hook, entity, blockPos, itemId, "");
    }

    public static DAI_ReactionDispatchSession beginHook(
            String hook,
            Entity entity
    ) {
        return beginHook(hook, entity, null, "", "");
    }

    public static DAI_ReactionDispatchSession begin(
            String event,
            Entity entity
    ) {
        return begin(event, entity, null, "", "");
    }

    public static DAI_ReactionDispatchSession begin(
            String event,
            Entity entity,
            BlockPos blockPos,
            String itemId
    ) {
        return begin(event, entity, blockPos, itemId, "");
    }

    public static DAI_ReactionDispatchSession begin(
            String event,
            Entity entity,
            BlockPos blockPos,
            String itemId,
            String itemModel
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
                itemId,
                itemModel
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
