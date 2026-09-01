package io.github.j12h36h.dai.client.combat.indicator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Immutable resource-pack-authored damage-indicator skin. */
public record DAI_DamageIndicatorSkin(
        Identifier id,
        int priority,
        Map<String, Category> categories
) {
    public static final String SCHEMA = "dai_damage_indicator_skin_v1";

    public Category category(String category) {
        String key = category == null ? "default" : category.trim().toLowerCase(Locale.ROOT);
        Category value = categories.get(key);
        if (value == null) value = categories.get("default");
        return value;
    }

    public static DAI_DamageIndicatorSkin parse(Identifier id, JsonObject json) {
        if (json == null || !json.has("schema") || !SCHEMA.equals(json.get("schema").getAsString())) return null;
        if (json.has("enabled") && !json.get("enabled").getAsBoolean()) return null;
        int priority = intValue(json, "priority", 0);
        LinkedHashMap<String, Category> categories = new LinkedHashMap<>();
        JsonObject source = object(json, "categories");
        if (source != null) {
            for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                categories.put(entry.getKey().toLowerCase(Locale.ROOT), Category.parse(entry.getValue().getAsJsonObject()));
            }
        }
        if (categories.isEmpty()) return null;
        return new DAI_DamageIndicatorSkin(id, priority, Map.copyOf(categories));
    }

    public record Category(
            Identifier font,
            int color,
            boolean shadow,
            boolean seeThrough,
            float baseScale,
            Phase spawn,
            Phase hold,
            Phase exit
    ) {
        static Category parse(JsonObject json) {
            Identifier font = Identifier.tryParse(stringValue(json, "font", "minecraft:default"));
            if (font == null) font = Identifier.fromNamespaceAndPath("minecraft", "default");
            int color = colorValue(json, "color", 0xFFFF4D64);
            boolean shadow = boolValue(json, "shadow", true);
            boolean seeThrough = boolValue(json, "see_through", true);
            float baseScale = floatValue(json, "base_scale", 1.0F);
            Phase spawn = Phase.parse(object(json, "spawn"), new Phase(5,0,0,0,0.15F,0.3F,1.0F,0,0,0,1,"back_out"));
            Phase hold = Phase.parse(object(json, "hold"), new Phase(18,0,0,0.15F,0.45F,1,1,0,0,1,1,"ease_out"));
            Phase exit = Phase.parse(object(json, "exit"), new Phase(8,0,0,0.45F,0.80F,1,0.75F,0,0,1,0,"ease_in"));
            return new Category(font, color, shadow, seeThrough, baseScale, spawn, hold, exit);
        }

        public int totalDuration() {
            return Math.max(1, spawn.duration()) + Math.max(0, hold.duration()) + Math.max(1, exit.duration());
        }
    }

    public record Phase(
            int duration,
            float xFrom, float xTo,
            float yFrom, float yTo,
            float scaleFrom, float scaleTo,
            float rotationFrom, float rotationTo,
            float alphaFrom, float alphaTo,
            String easing
    ) {
        static Phase parse(JsonObject json, Phase fallback) {
            if (json == null) return fallback;
            return new Phase(
                    intValue(json, "duration", fallback.duration),
                    floatValue(json, "x_from", fallback.xFrom), floatValue(json, "x_to", fallback.xTo),
                    floatValue(json, "y_from", fallback.yFrom), floatValue(json, "y_to", fallback.yTo),
                    floatValue(json, "scale_from", fallback.scaleFrom), floatValue(json, "scale_to", fallback.scaleTo),
                    floatValue(json, "rotation_from", fallback.rotationFrom), floatValue(json, "rotation_to", fallback.rotationTo),
                    floatValue(json, "alpha_from", fallback.alphaFrom), floatValue(json, "alpha_to", fallback.alphaTo),
                    stringValue(json, "easing", fallback.easing)
            );
        }
    }

    private static JsonObject object(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : null;
    }
    private static String stringValue(JsonObject json, String key, String fallback) {
        try { return json != null && json.has(key) ? json.get(key).getAsString() : fallback; } catch (Throwable ignored) { return fallback; }
    }
    private static int intValue(JsonObject json, String key, int fallback) {
        try { return json != null && json.has(key) ? json.get(key).getAsInt() : fallback; } catch (Throwable ignored) { return fallback; }
    }
    private static float floatValue(JsonObject json, String key, float fallback) {
        try { return json != null && json.has(key) ? json.get(key).getAsFloat() : fallback; } catch (Throwable ignored) { return fallback; }
    }
    private static boolean boolValue(JsonObject json, String key, boolean fallback) {
        try { return json != null && json.has(key) ? json.get(key).getAsBoolean() : fallback; } catch (Throwable ignored) { return fallback; }
    }
    private static int colorValue(JsonObject json, String key, int fallback) {
        String value = stringValue(json, key, "").trim();
        if (value.startsWith("#")) value = value.substring(1);
        try {
            if (value.length() == 6) return (int)(0xFF000000L | Long.parseLong(value, 16));
            if (value.length() == 8) return (int)Long.parseLong(value, 16);
        } catch (Throwable ignored) {}
        return fallback;
    }
}
