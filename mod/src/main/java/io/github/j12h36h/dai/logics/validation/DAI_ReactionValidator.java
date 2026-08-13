package io.github.j12h36h.dai.logics.validation;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.reactions.DAI_ReactionDefinition;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventDefinition;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventRegistry;
import io.github.j12h36h.dai.reactions.DAI_ReactionLibrary;
import io.github.j12h36h.dai.reactions.DAI_ReactionPhase;
import io.github.j12h36h.dai.reactions.DAI_ReactionType;
import net.minecraft.resources.Identifier;

import java.util.Map;

public final class DAI_ReactionValidator {

    private DAI_ReactionValidator() {
        // Utility class.
    }

    public static void validate() {

        for (
                Map.Entry<Identifier, DAI_ReactionDefinition> entry
                : DAI_ReactionLibrary.reactions()
                .entrySet()
        ) {

            validateReaction(
                    entry.getKey(),
                    entry.getValue()
            );
        }
    }

    private static void validateReaction(
            Identifier id,
            DAI_ReactionDefinition reaction
    ) {

        String source =
                "reaction:"
                        + id;

        if (reaction == null) {

            DAI_ValidationReport.error(
                    source,
                    "Reaction definition is null."
            );

            return;
        }

        DAI_ReactionType type =
                reaction.reactionType();

        DAI_ReactionPhase phase =
                reaction.reactionPhase();

        if (type == DAI_ReactionType.UNKNOWN) {

            DAI_ValidationReport.error(
                    source,
                    "Unknown reaction type '"
                            + reaction.type()
                            + "'. Expected default, override, or cancel."
            );
        }

        if (phase == DAI_ReactionPhase.UNKNOWN) {

            DAI_ValidationReport.error(
                    source,
                    "Unknown reaction phase '"
                            + reaction.phase()
                            + "'. Expected pre, during, or post."
            );
        }

        if (reaction.priority() < 0) {

            DAI_ValidationReport.error(
                    source,
                    "Reaction priority cannot be negative."
            );
        }

        DAI_ReactionEventDefinition event =
                DAI_ReactionEventRegistry.get(
                        reaction.event()
                );

        if (event == null) {

            DAI_ValidationReport.error(
                    source,
                    "Unknown reaction event '"
                            + reaction.event()
                            + "'."
            );

        } else if (
                phase != DAI_ReactionPhase.UNKNOWN
                        && !event.supports(phase)
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Event '"
                            + reaction.event()
                            + "' does not support phase '"
                            + reaction.phase()
                            + "'."
            );
        }

        if (
                phase == DAI_ReactionPhase.POST
                        && (
                        type == DAI_ReactionType.CANCEL
                                || type == DAI_ReactionType.OVERRIDE
                )
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Post reactions cannot cancel or override an event because the underlying event has already completed."
            );
        }

        if (
                event != null
                        && type == DAI_ReactionType.CANCEL
                        && !event.cancellable()
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Event '"
                            + reaction.event()
                            + "' is not cancellable."
            );
        }

        if (
                event != null
                        && type == DAI_ReactionType.OVERRIDE
                        && !event.overrideable()
        ) {

            DAI_ValidationReport.error(
                    source,
                    "Event '"
                            + reaction.event()
                            + "' is not overrideable."
            );
        }

        if (reaction.sequence().isEmpty()) {

            if (type == DAI_ReactionType.DEFAULT) {

                DAI_ValidationReport.warning(
                        source,
                        "Default reaction has an empty sequence and will have no visible effect."
                );
            }
        }

        DAI_ActionValidator.validateInlineConditions(
                source,
                reaction.conditions()
        );

        for (
                int index = 0;
                index < reaction.sequence().size();
                index++
        ) {

            DAI_ActionDefinition action =
                    reaction.sequence()
                            .get(index);

            DAI_ActionValidator.validateInlineAction(
                    source
                            + ".sequence["
                            + index
                            + "]",
                    action
            );
        }
    }
}
