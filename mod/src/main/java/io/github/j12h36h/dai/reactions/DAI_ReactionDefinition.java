package io.github.j12h36h.dai.reactions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionDefinition;

import java.util.List;
import java.util.Locale;

public record DAI_ReactionDefinition(
        String type,
        String event,
        String phase,
        int priority,
        List<DAI_ConditionDefinition> conditions,
        List<DAI_ActionDefinition> sequence
) {

    public static final Codec<DAI_ReactionDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING
                                    .optionalFieldOf(
                                            "type",
                                            "default"
                                    )
                                    .forGetter(
                                            DAI_ReactionDefinition::type
                                    ),

                            Codec.STRING
                                    .fieldOf(
                                            "event"
                                    )
                                    .forGetter(
                                            DAI_ReactionDefinition::event
                                    ),

                            Codec.STRING
                                    .optionalFieldOf(
                                            "phase",
                                            "during"
                                    )
                                    .forGetter(
                                            DAI_ReactionDefinition::phase
                                    ),

                            Codec.INT
                                    .optionalFieldOf(
                                            "priority",
                                            0
                                    )
                                    .forGetter(
                                            DAI_ReactionDefinition::priority
                                    ),

                            DAI_ConditionDefinition.CODEC
                                    .listOf()
                                    .optionalFieldOf(
                                            "conditions",
                                            List.of()
                                    )
                                    .forGetter(
                                            DAI_ReactionDefinition::conditions
                                    ),

                            DAI_ActionDefinition.CODEC
                                    .listOf()
                                    .optionalFieldOf(
                                            "sequence",
                                            List.of()
                                    )
                                    .forGetter(
                                            DAI_ReactionDefinition::sequence
                                    )
                    ).apply(
                            instance,
                            DAI_ReactionDefinition::new
                    )
            );

    public DAI_ReactionDefinition {

        type = normalize(
                type,
                "default"
        );

        event = normalize(
                event,
                ""
        );

        phase = normalize(
                phase,
                "during"
        );

        conditions =
                conditions == null
                        ? List.of()
                        : List.copyOf(
                                conditions
                        );

        sequence =
                sequence == null
                        ? List.of()
                        : List.copyOf(
                                sequence
                        );

        if (
                conditions.stream()
                        .anyMatch(condition ->
                                condition == null
                        )
        ) {

            throw new IllegalArgumentException(
                    "Reaction conditions cannot contain null entries."
            );
        }

        if (
                sequence.stream()
                        .anyMatch(action ->
                                action == null
                        )
        ) {

            throw new IllegalArgumentException(
                    "Reaction sequence cannot contain null entries."
            );
        }
    }

    public DAI_ReactionType reactionType() {

        return DAI_ReactionType.parse(
                type
        );
    }

    public DAI_ReactionPhase reactionPhase() {

        return DAI_ReactionPhase.parse(
                phase
        );
    }

    private static String normalize(
            String value,
            String fallback
    ) {

        if (value == null) {
            return fallback;
        }

        String normalized =
                value.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return normalized.isEmpty()
                ? fallback
                : normalized;
    }
}
