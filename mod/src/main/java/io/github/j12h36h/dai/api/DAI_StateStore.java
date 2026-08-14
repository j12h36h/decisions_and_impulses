package io.github.j12h36h.dai.api;

import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DAI_StateStore {

    private static final Map<String, DAI_StateValue> VALUES =
            new ConcurrentHashMap<>();

    private DAI_StateStore() {
        // Utility class.
    }

    public static void setBoolean(
            String key,
            boolean value
    ) {

        VALUES.put(
                requireKey(key),
                DAI_StateValue.bool(value)
        );
    }

    public static void setNumber(
            String key,
            double value
    ) {

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "State numeric value must be finite."
            );
        }

        VALUES.put(
                requireKey(key),
                DAI_StateValue.number(value)
        );
    }

    public static void setString(
            String key,
            String value
    ) {

        VALUES.put(
                requireKey(key),
                DAI_StateValue.string(value)
        );
    }

    public static DAI_StateValue get(
            String key
    ) {

        String normalized =
                normalizeKey(key);

        if (normalized.isEmpty()) {
            return DAI_StateValue.missing();
        }

        return VALUES.getOrDefault(
                normalized,
                DAI_StateValue.missing()
        );
    }

    public static boolean contains(
            String key
    ) {

        String normalized =
                normalizeKey(key);

        return !normalized.isEmpty()
                && VALUES.containsKey(normalized);
    }

    public static double addNumber(
            String key,
            double delta
    ) {

        if (!Double.isFinite(delta)) {
            throw new IllegalArgumentException(
                    "State numeric delta must be finite."
            );
        }

        String normalized =
                requireKey(key);

        DAI_StateValue updated =
                VALUES.compute(
                        normalized,
                        (ignored, current) -> {

                            double base =
                                    current != null
                                            && current.type()
                                            == DAI_StateValue.Type.NUMBER
                                                    ? current.numberValue()
                                                    : 0.0D;

                            return DAI_StateValue.number(
                                    base + delta
                            );
                        }
                );

        return updated.numberValue();
    }

    public static boolean toggleBoolean(
            String key
    ) {

        String normalized =
                requireKey(key);

        DAI_StateValue updated =
                VALUES.compute(
                        normalized,
                        (ignored, current) -> {

                            boolean base =
                                    current != null
                                            && current.type()
                                            == DAI_StateValue.Type.BOOLEAN
                                            && current.booleanValue();

                            return DAI_StateValue.bool(
                                    !base
                            );
                        }
                );

        return updated.booleanValue();
    }

    public static void remove(
            String key
    ) {

        String normalized =
                normalizeKey(key);

        if (!normalized.isEmpty()) {
            VALUES.remove(normalized);
        }
    }

    public static Map<String, DAI_StateValue> snapshot() {
        return Map.copyOf(VALUES);
    }

    public static void clear() {
        VALUES.clear();
    }

    public static String normalizeKey(
            String key
    ) {

        if (key == null) {
            return "";
        }

        String normalized =
                key.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (normalized.isEmpty()) {
            return "";
        }

        if (!normalized.contains(":")) {

            normalized =
                    DAI_Core.MODID
                            + ":"
                            + normalized;
        }

        return normalized;
    }

    private static String requireKey(
            String key
    ) {

        String normalized =
                normalizeKey(key);

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "State key cannot be null or blank."
            );
        }

        return normalized;
    }
}
