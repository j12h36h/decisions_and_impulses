package io.github.j12h36h.dai.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.story.model.DAI_StorySession;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Generic condition evaluation for story profiles; contains no story semantics. */
public final class DAI_StoryConditions {
    private DAI_StoryConditions() {}

    public static boolean all(JsonArray conditions, Map<String, Object> current, Map<String, Object> previous, DAI_StorySession session) {
        if (conditions == null || conditions.isEmpty()) return true;
        for (JsonElement element : conditions) {
            if (!element.isJsonObject()) return false;
            if (!evaluate(element.getAsJsonObject(), current, previous, session)) return false;
        }
        return true;
    }

    public static boolean evaluate(JsonObject condition, Map<String, Object> current, Map<String, Object> previous, DAI_StorySession session) {
        if (condition == null) return false;
        String key = DAI_StoryProfileDefinition.string(condition, "field", DAI_StoryProfileDefinition.string(condition, "value", ""));
        String operator = DAI_StoryProfileDefinition.normalized(DAI_StoryProfileDefinition.string(condition, "operator", "equals"));
        Object actual = value(key, current, session);
        Object prior = value(key, previous, session);
        Object expected = condition.has("compare") ? primitive(condition.get("compare"))
                : condition.has("expected") ? primitive(condition.get("expected")) : null;

        boolean result = switch (operator) {
            case "exists" -> actual != null && !String.valueOf(actual).isBlank();
            case "not_exists", "missing" -> actual == null || String.valueOf(actual).isBlank();
            case "is_true", "true" -> bool(actual, false);
            case "is_false", "false" -> !bool(actual, false);
            case "changed" -> previous != null && !same(actual, prior);
            case "unchanged" -> previous != null && same(actual, prior);
            case "increased" -> previous != null && number(actual, Double.NaN) > number(prior, Double.NaN);
            case "decreased" -> previous != null && number(actual, Double.NaN) < number(prior, Double.NaN);
            case "became_true" -> previous != null && bool(actual, false) && !bool(prior, false);
            case "became_false" -> previous != null && !bool(actual, false) && bool(prior, false);
            case "contains" -> contains(actual, expected);
            case "not_contains" -> !contains(actual, expected);
            case "starts_with" -> text(actual).startsWith(text(expected));
            case "ends_with" -> text(actual).endsWith(text(expected));
            case "equals_ignore_case" -> text(actual).equalsIgnoreCase(text(expected));
            case "not_equals", "not" -> !same(actual, expected);
            case "less_than", "lt" -> number(actual, Double.NaN) < number(expected, Double.NaN);
            case "less_than_or_equal", "lte" -> number(actual, Double.NaN) <= number(expected, Double.NaN);
            case "greater_than", "gt" -> number(actual, Double.NaN) > number(expected, Double.NaN);
            case "greater_than_or_equal", "gte" -> number(actual, Double.NaN) >= number(expected, Double.NaN);
            case "crossed_below" -> previous != null && number(prior, Double.NaN) > number(expected, Double.NaN)
                    && number(actual, Double.NaN) <= number(expected, Double.NaN);
            case "crossed_above" -> previous != null && number(prior, Double.NaN) < number(expected, Double.NaN)
                    && number(actual, Double.NaN) >= number(expected, Double.NaN);
            case "matches" -> text(actual).matches(text(expected));
            default -> same(actual, expected);
        };
        return DAI_StoryProfileDefinition.bool(condition, "negate", false) ? !result : result;
    }

    public static Object value(String key, Map<String, Object> snapshot, DAI_StorySession session) {
        if (key == null || key.isBlank()) return null;
        if (key.startsWith("session.")) {
            String sub = key.substring("session.".length());
            if (session == null) return null;
            return switch (sub) {
                case "number" -> session.number;
                case "completed" -> session.completed;
                case "observed_ticks" -> session.observedTicks;
                case "event_count" -> session.events == null ? 0 : session.events.size();
                case "context" -> session.context;
                case "title" -> session.title;
                default -> session.memory == null ? null : session.memory.get(sub.startsWith("memory.") ? sub.substring(7) : sub);
            };
        }
        return snapshot == null ? null : snapshot.get(key);
    }

    private static Object primitive(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) {
            var p = element.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsDouble();
            return p.getAsString();
        }
        return element.toString();
    }
    private static boolean contains(Object actual, Object expected) {
        if (actual instanceof Collection<?> c) return c.stream().anyMatch(v -> same(v, expected));
        if (actual instanceof Map<?,?> m) return m.containsKey(expected) || m.containsValue(expected);
        return text(actual).contains(text(expected));
    }
    private static boolean same(Object a, Object b) {
        if (a instanceof Number || b instanceof Number) {
            double av = number(a, Double.NaN), bv = number(b, Double.NaN);
            if (Double.isFinite(av) && Double.isFinite(bv)) return Double.compare(av, bv) == 0;
        }
        if (a instanceof Boolean || b instanceof Boolean) return bool(a, false) == bool(b, false);
        return Objects.equals(text(a), text(b));
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static double number(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(text(value)); } catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        String s = text(value).trim().toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("on")) return true;
        if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("off")) return false;
        return fallback;
    }
}
