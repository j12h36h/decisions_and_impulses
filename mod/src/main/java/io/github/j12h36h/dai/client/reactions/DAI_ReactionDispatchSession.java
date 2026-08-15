package io.github.j12h36h.dai.client.reactions;

import io.github.j12h36h.dai.reactions.*;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionResolver;
import io.github.j12h36h.dai.client.logics.condition.DAI_ConditionEvaluator;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public final class DAI_ReactionDispatchSession {

    private final DAI_ReactionEventDefinition eventDefinition;
    private final Entity entity;

    private final List<DAI_ActionDefinition> queuedActions =
            new ArrayList<>();

    private DAI_ReactionOutcome outcome =
            DAI_ReactionOutcome.PASS;

    private boolean controlClaimed;
    private boolean flushed;

    DAI_ReactionDispatchSession(
            DAI_ReactionEventDefinition eventDefinition,
            Entity entity
    ) {

        this.eventDefinition =
                eventDefinition;

        this.entity =
                entity;
    }

    public DAI_ReactionOutcome fire(
            DAI_ReactionPhase phase
    ) {

        if (flushed) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Ignored reaction phase '{}' after event session '{}' was already flushed.",
                    phase == null
                            ? "<null>"
                            : phase.id(),
                    eventDefinition.id()
            );

            return outcome;
        }

        if (!eventDefinition.supports(phase)) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Reaction event '{}' does not support phase '{}'.",
                    eventDefinition.id(),
                    phase == null
                            ? "<null>"
                            : phase.id()
            );

            return outcome;
        }

        List<DAI_ReactionEntry> reactions =
                DAI_ReactionLibrary.matching(
                        eventDefinition.id(),
                        phase
                );

        if (reactions.isEmpty()) {
            return outcome;
        }

        DAI_ReactionContext context =
                new DAI_ReactionContext(
                        eventDefinition.id(),
                        phase,
                        entity
                );

        for (DAI_ReactionEntry entry : reactions) {

            DAI_ReactionDefinition reaction =
                    entry.definition();

            if (!conditionsPass(context, reaction)) {
                continue;
            }

            DAI_ReactionType type =
                    reaction.reactionType();

            if (type == DAI_ReactionType.UNKNOWN) {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Ignored reaction '{}' with unknown type '{}'.",
                        entry.id(),
                        reaction.type()
                );

                continue;
            }

            if (
                    type == DAI_ReactionType.CANCEL
                            || type == DAI_ReactionType.OVERRIDE
            ) {

                if (phase == DAI_ReactionPhase.POST) {

                    DAI_Core.LOGGER.warn(
                            "<DAI>: Ignored post reaction '{}' type='{}'; post reactions cannot control a completed event.",
                            entry.id(),
                            reaction.type()
                    );

                    continue;
                }

                if (controlClaimed) {

                    DAI_Core.debug(
                            "<DAI>: Skipped lower-priority control reaction '{}' for event='{}' phase='{}'.",
                            entry.id(),
                            eventDefinition.id(),
                            phase.id()
                    );

                    continue;
                }

                if (
                        type == DAI_ReactionType.CANCEL
                                && !eventDefinition.cancellable()
                ) {

                    DAI_Core.LOGGER.warn(
                            "<DAI>: Ignored cancel reaction '{}' because event '{}' is not cancellable.",
                            entry.id(),
                            eventDefinition.id()
                    );

                    continue;
                }

                if (
                        type == DAI_ReactionType.OVERRIDE
                                && !eventDefinition.overrideable()
                ) {

                    DAI_Core.LOGGER.warn(
                            "<DAI>: Ignored override reaction '{}' because event '{}' is not overrideable.",
                            entry.id(),
                            eventDefinition.id()
                    );

                    continue;
                }

                controlClaimed =
                        true;

                outcome =
                        type == DAI_ReactionType.CANCEL
                                ? DAI_ReactionOutcome.CANCEL
                                : DAI_ReactionOutcome.OVERRIDE;
            }

            appendResolved(
                    reaction.sequence()
            );

            DAI_Core.debug(
                    "<DAI>: Fired reaction '{}' type='{}' event='{}' phase='{}' priority={}.",
                    entry.id(),
                    reaction.type(),
                    reaction.event(),
                    reaction.phase(),
                    reaction.priority()
            );
        }

        return outcome;
    }

    public DAI_ReactionOutcome outcome() {
        return outcome;
    }

    public void flush() {

        if (flushed) {
            return;
        }

        flushed =
                true;

        if (queuedActions.isEmpty()) {
            return;
        }

        DAI_ActionQueue.enqueueFirstAll(
                queuedActions
        );
    }

    private boolean conditionsPass(
            DAI_ReactionContext context,
            DAI_ReactionDefinition reaction
    ) {

        DAI_ReactionContext previous =
                DAI_ReactionRuntime.enter(
                        context
                );

        try {

            return DAI_ConditionEvaluator.evaluateAll(
                    reaction.conditions()
            );

        } finally {

            DAI_ReactionRuntime.restore(
                    previous
            );
        }
    }

    private void appendResolved(
            List<DAI_ActionDefinition> sequence
    ) {

        if (sequence == null || sequence.isEmpty()) {
            return;
        }

        for (DAI_ActionDefinition action : sequence) {

            queuedActions.addAll(
                    DAI_ActionResolver.resolve(
                            action
                    )
            );
        }
    }
}
