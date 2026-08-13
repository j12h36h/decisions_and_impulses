package io.github.j12h36h.dai.reactions;

import java.util.EnumSet;
import java.util.Set;

public record DAI_ReactionEventDefinition(
        String id,
        Set<DAI_ReactionPhase> phases,
        boolean cancellable,
        boolean overrideable
) {

    public DAI_ReactionEventDefinition {

        phases =
                phases == null
                        ? Set.of()
                        : Set.copyOf(
                                phases
                        );
    }

    public static DAI_ReactionEventDefinition allPhases(
            String id,
            boolean cancellable,
            boolean overrideable
    ) {

        return new DAI_ReactionEventDefinition(
                id,
                EnumSet.of(
                        DAI_ReactionPhase.PRE,
                        DAI_ReactionPhase.DURING,
                        DAI_ReactionPhase.POST
                ),
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
