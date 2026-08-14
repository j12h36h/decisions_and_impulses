package io.github.j12h36h.dai.reactions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record DAI_ReactionEventDefinition(
        String id,
        Set<DAI_ReactionPhase> phases,
        boolean cancellable,
        boolean overrideable
) {

    private static final Set<DAI_ReactionPhase> DEFAULT_PHASES =
            Set.of(
                    DAI_ReactionPhase.PRE,
                    DAI_ReactionPhase.DURING,
                    DAI_ReactionPhase.POST
            );

    private static final Codec<Set<DAI_ReactionPhase>> PHASES_CODEC =
            Codec.STRING
                    .xmap(
                            DAI_ReactionPhase::parse,
                            DAI_ReactionPhase::id
                    )
                    .listOf()
                    .xmap(
                            values -> Set.copyOf(values),
                            values -> List.copyOf(values)
                    );

    public static final Codec<DAI_ReactionEventDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING
                                    .optionalFieldOf(
                                            "id",
                                            ""
                                    )
                                    .forGetter(
                                            DAI_ReactionEventDefinition::id
                                    ),

                            PHASES_CODEC
                                    .optionalFieldOf(
                                            "phases",
                                            DEFAULT_PHASES
                                    )
                                    .forGetter(
                                            DAI_ReactionEventDefinition::phases
                                    ),

                            Codec.BOOL
                                    .optionalFieldOf(
                                            "cancellable",
                                            true
                                    )
                                    .forGetter(
                                            DAI_ReactionEventDefinition::cancellable
                                    ),

                            Codec.BOOL
                                    .optionalFieldOf(
                                            "overrideable",
                                            true
                                    )
                                    .forGetter(
                                            DAI_ReactionEventDefinition::overrideable
                                    )
                    ).apply(
                            instance,
                            DAI_ReactionEventDefinition::new
                    )
            );

    public DAI_ReactionEventDefinition {

        id =
                id == null
                        ? ""
                        : id.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        phases =
                phases == null
                        ? DEFAULT_PHASES
                        : Set.copyOf(
                                phases
                        );

        if (phases.isEmpty()) {
            throw new IllegalArgumentException(
                    "Reaction event must support at least one phase."
            );
        }

        if (
                phases.contains(
                        DAI_ReactionPhase.UNKNOWN
                )
        ) {
            throw new IllegalArgumentException(
                    "Reaction event phases must be pre, during, or post."
            );
        }
    }

    public static DAI_ReactionEventDefinition allPhases(
            String id,
            boolean cancellable,
            boolean overrideable
    ) {

        return new DAI_ReactionEventDefinition(
                id,
                DEFAULT_PHASES,
                cancellable,
                overrideable
        );
    }

    public DAI_ReactionEventDefinition withId(
            String newId
    ) {

        return new DAI_ReactionEventDefinition(
                newId,
                phases,
                cancellable,
                overrideable
        );
    }

    public boolean supports(
            DAI_ReactionPhase phase
    ) {

        return phase != null
                && phases.contains(
                phase
        );
    }
}
