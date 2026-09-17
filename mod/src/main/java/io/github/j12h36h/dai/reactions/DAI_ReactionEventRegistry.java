package io.github.j12h36h.dai.reactions;

import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Logical reaction-event registry.
 *
 * Engine event definitions are fallback data, never protected definitions.
 * A datapack may replace a fallback by id, disable it, or bind a different
 * logical event id to the same stable engine hook with a higher priority.
 */
public final class DAI_ReactionEventRegistry {

    public static final String PLAYER_ATTACK_INPUT = "player_attack_input";
    public static final String PLAYER_INPUT_TICK = "player_input_tick";
    public static final String PLAYER_ATTACK_ENTITY = "player_attack_entity";
    public static final String PLAYER_USE_BLOCK = "player_use_block";
    public static final String PLAYER_USE_ITEM = "player_use_item";
    public static final String PLAYER_INTERACT_ENTITY = "player_interact_entity";
    public static final String PLAYER_START_BREAK_BLOCK = "player_start_break_block";

    private static final Map<String, DAI_ReactionEventDefinition> EVENTS = new LinkedHashMap<>();
    private static final Map<String, DAI_ReactionEventDefinition> FALLBACKS = new LinkedHashMap<>();

    private static final Comparator<DAI_ReactionEventDefinition> HOOK_ORDER =
            Comparator.comparingInt(DAI_ReactionEventDefinition::priority)
                    .reversed()
                    .thenComparing(DAI_ReactionEventDefinition::id);

    private DAI_ReactionEventRegistry() {}

    public static synchronized void initialize() {
        FALLBACKS.clear();
        addFallback(PLAYER_ATTACK_INPUT, true, true);
        addFallback(PLAYER_INPUT_TICK, false, false);
        addFallback(PLAYER_ATTACK_ENTITY, true, true);
        addFallback(PLAYER_USE_BLOCK, true, true);
        addFallback(PLAYER_USE_ITEM, true, true);
        addFallback(PLAYER_INTERACT_ENTITY, true, true);
        addFallback(PLAYER_START_BREAK_BLOCK, true, true);
        resetToFallbacks();

        DAI_Core.debug("<DAI>: Registered {} replaceable reaction hook fallback(s).", EVENTS.size());
    }

    public static synchronized void register(DAI_ReactionEventDefinition definition) {
        validateDefinition(definition);
        String id = normalize(definition.id());
        DAI_ReactionEventDefinition normalized = definition.withId(id);
        DAI_ReactionEventDefinition previous = EVENTS.put(id, normalized);
        if (previous == null) {
            DAI_Core.debug("<DAI>: Registered reaction event '{}' hook='{}'.", id, normalized.effectiveHook());
        } else {
            DAI_Core.debug("<DAI>: Replaced reaction event '{}' hook='{}'.", id, normalized.effectiveHook());
        }
    }

    /**
     * Retained for compatibility. "Built in" now means a fallback exists;
     * it does NOT mean the datapack is forbidden from replacing it.
     */
    public static synchronized boolean isBuiltIn(String event) {
        return FALLBACKS.containsKey(normalize(event));
    }

    /** Restores engine fallbacks before applying the active datapack stack. */
    public static synchronized void resetToFallbacks() {
        EVENTS.clear();
        EVENTS.putAll(FALLBACKS);
    }

    /** Compatibility alias for older callers. */
    public static void clearCustom() {
        resetToFallbacks();
    }

    public static synchronized DAI_ReactionEventDefinition get(String event) {
        DAI_ReactionEventDefinition definition = EVENTS.get(normalize(event));
        return definition != null && definition.enabled() ? definition : null;
    }

    /** Resolves the logical event currently bound to a stable Java integration hook. */
    public static synchronized DAI_ReactionEventDefinition resolveHook(String hook) {
        String normalized = normalize(hook);
        if (normalized.isBlank()) return null;
        return EVENTS.values().stream()
                .filter(DAI_ReactionEventDefinition::enabled)
                .filter(definition -> normalized.equals(normalize(definition.effectiveHook())))
                .sorted(HOOK_ORDER)
                .findFirst()
                .orElse(null);
    }

    public static boolean contains(String event) { return get(event) != null; }

    public static synchronized Set<String> ids() { return Set.copyOf(EVENTS.keySet()); }
    public static synchronized int size() { return EVENTS.size(); }
    public static synchronized Map<String, DAI_ReactionEventDefinition> snapshot() { return Map.copyOf(EVENTS); }

    private static void addFallback(String id, boolean cancellable, boolean overrideable) {
        DAI_ReactionEventDefinition definition = DAI_ReactionEventDefinition.fallback(id, cancellable, overrideable);
        FALLBACKS.put(normalize(id), definition);
    }

    private static void validateDefinition(DAI_ReactionEventDefinition definition) {
        if (definition == null || definition.id() == null || definition.id().isBlank()) {
            throw new IllegalArgumentException("Reaction event definition requires a non-blank id.");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
