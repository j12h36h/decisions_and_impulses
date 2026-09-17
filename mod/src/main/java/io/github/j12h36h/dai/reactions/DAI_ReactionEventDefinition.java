package io.github.j12h36h.dai.reactions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Datapack-authored definition for one logical reaction event.
 *
 * {@code hook} names the stable low-level engine hook that publishes the event.
 * The Java hook is only an integration point; datapacks remain free to rename,
 * disable, reprioritize and redefine the logical event attached to it.
 */
public record DAI_ReactionEventDefinition(
        String id,
        String hook,
        boolean enabled,
        int priority,
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
                                    .optionalFieldOf("id", "")
                                    .forGetter(DAI_ReactionEventDefinition::id),
                            Codec.STRING
                                    .optionalFieldOf("hook", "")
                                    .forGetter(DAI_ReactionEventDefinition::hook),
                            Codec.BOOL
                                    .optionalFieldOf("enabled", true)
                                    .forGetter(DAI_ReactionEventDefinition::enabled),
                            Codec.INT
                                    .optionalFieldOf("priority", 0)
                                    .forGetter(DAI_ReactionEventDefinition::priority),
                            PHASES_CODEC
                                    .optionalFieldOf("phases", DEFAULT_PHASES)
                                    .forGetter(DAI_ReactionEventDefinition::phases),
                            Codec.BOOL
                                    .optionalFieldOf("cancellable", true)
                                    .forGetter(DAI_ReactionEventDefinition::cancellable),
                            Codec.BOOL
                                    .optionalFieldOf("overrideable", true)
                                    .forGetter(DAI_ReactionEventDefinition::overrideable)
                    ).apply(instance, DAI_ReactionEventDefinition::new)
            );

    public DAI_ReactionEventDefinition {
        id = normalize(id);
        hook = normalize(hook);
        phases = phases == null ? DEFAULT_PHASES : Set.copyOf(phases);

        if (phases.isEmpty()) {
            throw new IllegalArgumentException("Reaction event must support at least one phase.");
        }
        if (phases.contains(DAI_ReactionPhase.UNKNOWN)) {
            throw new IllegalArgumentException("Reaction event phases must be pre, during, or post.");
        }
    }

    /** Low-priority engine fallback. Datapacks can replace or remap it. */
    public static DAI_ReactionEventDefinition fallback(
            String id,
            boolean cancellable,
            boolean overrideable
    ) {
        String normalized = normalize(id);
        return new DAI_ReactionEventDefinition(
                normalized,
                normalized,
                true,
                Integer.MIN_VALUE,
                DEFAULT_PHASES,
                cancellable,
                overrideable
        );
    }

    /** Compatibility alias retained for API callers compiled against the old helper. */
    public static DAI_ReactionEventDefinition allPhases(
            String id,
            boolean cancellable,
            boolean overrideable
    ) {
        String normalized = normalize(id);
        return new DAI_ReactionEventDefinition(
                normalized,
                normalized,
                true,
                0,
                DEFAULT_PHASES,
                cancellable,
                overrideable
        );
    }

    public DAI_ReactionEventDefinition withId(String newId) {
        String normalizedId = normalize(newId);
        return new DAI_ReactionEventDefinition(
                normalizedId,
                hook.isBlank() ? normalizedId : hook,
                enabled,
                priority,
                phases,
                cancellable,
                overrideable
        );
    }

    public boolean supports(DAI_ReactionPhase phase) {
        return enabled && phase != null && phases.contains(phase);
    }

    public String effectiveHook() {
        return hook.isBlank() ? id : hook;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
