package io.github.j12h36h.dai.client.presentation.shell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Data-driven routing definition for the DAI application shell.
 *
 * The engine owns only stable lifecycle stage names. What those stages look
 * like is pack data: DAI built-ins, data screens, vanilla Minecraft, or no
 * screen at all. A pack may also select a fallback policy for stages it does
 * not define, making partial total-conversion shells possible.
 */
public final class DAI_ShellPresentationDefinition {

    public static final String MODE_DEFAULT = "default";
    public static final String MODE_VANILLA = "vanilla";
    public static final String MODE_DATA_SCREEN = "data_screen";
    public static final String MODE_NONE = "none";

    private final String id;
    private final boolean enabled;
    private final int priority;
    private final String experience;
    private final String fallback;
    private final Map<String, Route> routes;
    private final SafeLoadingStyle safeLoading;

    private DAI_ShellPresentationDefinition(
            String id,
            boolean enabled,
            int priority,
            String experience,
            String fallback,
            Map<String, Route> routes,
            SafeLoadingStyle safeLoading
    ) {
        this.id = id;
        this.enabled = enabled;
        this.priority = priority;
        this.experience = experience == null ? "" : experience.trim();
        this.fallback = normalizeMode(fallback, MODE_DEFAULT);
        this.routes = Map.copyOf(routes);
        this.safeLoading = safeLoading;
    }

    public static DAI_ShellPresentationDefinition parse(String id, JsonObject json) {
        JsonObject root = json == null ? new JsonObject() : json;
        LinkedHashMap<String, Route> routes = new LinkedHashMap<>();
        JsonObject stages = object(root, "stages");
        for (Map.Entry<String, JsonElement> entry : stages.entrySet()) {
            String stage = normalizeStage(entry.getKey());
            if (stage.isBlank()) continue;
            JsonElement value = entry.getValue();
            Route route;
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                route = new Route(normalizeMode(value.getAsString(), MODE_DEFAULT), "", "");
            } else if (value != null && value.isJsonObject()) {
                JsonObject routeJson = value.getAsJsonObject();
                route = new Route(
                        normalizeMode(string(routeJson, "mode", MODE_DEFAULT), MODE_DEFAULT),
                        string(routeJson, "screen", string(routeJson, "data_screen", "")).trim(),
                        string(routeJson, "scene", "").trim()
                );
            } else {
                continue;
            }
            routes.put(stage, route);
        }

        JsonObject safe = object(root, "safe_loading");
        SafeLoadingStyle safeStyle = new SafeLoadingStyle(
                nullableColor(safe, "background_top"),
                nullableColor(safe, "background_bottom"),
                nullableColor(safe, "primary"),
                nullableColor(safe, "secondary"),
                nullableColor(safe, "text"),
                bool(safe, "show_percentage", true),
                string(safe, "stage_text", "").trim()
        );

        return new DAI_ShellPresentationDefinition(
                id == null ? "" : id,
                bool(root, "enabled", true),
                integer(root, "priority", 0),
                string(
                        root,
                        "experience",
                        string(root, "owner_experience", string(root, "experience_id", ""))
                ),
                string(root, "fallback", MODE_DEFAULT),
                routes,
                safeStyle
        );
    }

    public static DAI_ShellPresentationDefinition defaultDefinition() {
        return parse("decisions_and_impulses:fallback", new JsonObject());
    }

    public String id() { return id; }
    public boolean enabled() { return enabled; }
    public int priority() { return priority; }
    public String experience() { return experience; }
    public String fallback() { return fallback; }
    public SafeLoadingStyle safeLoading() { return safeLoading; }

    public Route route(String stage) {
        Route explicit = routes.get(normalizeStage(stage));
        return explicit == null ? new Route(fallback, "", "") : explicit;
    }

    public boolean vanilla(String stage) {
        return MODE_VANILLA.equals(route(stage).mode());
    }

    public boolean none(String stage) {
        return MODE_NONE.equals(route(stage).mode());
    }

    public record Route(String mode, String screen, String scene) {
        public Route {
            mode = normalizeMode(mode, MODE_DEFAULT);
            screen = screen == null ? "" : screen.trim();
            scene = scene == null ? "" : scene.trim();
        }
    }

    /** Resource-independent style contract used while registries/resources are unsafe. */
    public record SafeLoadingStyle(
            Integer backgroundTop,
            Integer backgroundBottom,
            Integer primary,
            Integer secondary,
            Integer text,
            boolean showPercentage,
            String stageText
    ) {
        public SafeLoadingStyle {
            stageText = stageText == null ? "" : stageText.trim();
        }
    }

    public static String normalizeStage(String stage) {
        return stage == null ? "" : stage.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String normalizeMode(String mode, String fallback) {
        String value = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "dai", "builtin", "built_in", "engine", "default" -> MODE_DEFAULT;
            case "minecraft", "native", "vanilla" -> MODE_VANILLA;
            case "data", "data_screen", "screen" -> MODE_DATA_SCREEN;
            case "none", "hidden", "disabled" -> MODE_NONE;
            default -> fallback;
        };
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject root, String key, String fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsInt() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static Integer nullableColor(JsonObject root, String key) {
        if (root == null || !root.has(key)) return null;
        try {
            JsonElement element = root.get(key);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) return element.getAsInt();
            String raw = element.getAsString().trim();
            if (raw.isBlank()) return null;
            if (raw.startsWith("#")) raw = raw.substring(1);
            if (raw.startsWith("0x") || raw.startsWith("0X")) raw = raw.substring(2);
            long parsed = Long.parseUnsignedLong(raw, 16);
            if (raw.length() <= 6) parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
