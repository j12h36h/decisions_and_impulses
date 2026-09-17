package io.github.j12h36h.dai.runtime;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * One-shot client -> integrated-server handoff for the "Minecraft + DAI"
 * creation flow. An explicit empty selection means "create without DAI
 * addons"; null means no custom selection was requested and normal config
 * policy should be used.
 */
public final class DAI_StandaloneLaunchState {

    private static volatile Selection pending;

    private DAI_StandaloneLaunchState() {}

    public static synchronized void prepare(Set<String> addonFileNames) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (addonFileNames != null) {
            for (String value : addonFileNames) {
                if (value == null || value.isBlank()) continue;
                normalized.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        pending = new Selection(Set.copyOf(normalized));
    }

    public static synchronized Selection consume() {
        Selection value = pending;
        pending = null;
        return value;
    }

    public static synchronized void clear() {
        pending = null;
    }

    public static boolean armed() {
        return pending != null;
    }

    /**
     * True only when the client explicitly requested a pure vanilla data stack.
     * This is intentionally non-consuming so pack discovery can query it more
     * than once while Create World and the integrated server build repositories.
     */
    public static synchronized boolean explicitEmptySelection() {
        return pending != null && pending.addonFileNames().isEmpty();
    }

    public record Selection(Set<String> addonFileNames) {
        public Selection {
            addonFileNames = addonFileNames == null ? Set.of() : Set.copyOf(addonFileNames);
        }

        public boolean includes(String fileName) {
            return fileName != null && addonFileNames.contains(fileName.trim().toLowerCase(Locale.ROOT));
        }
    }
}
