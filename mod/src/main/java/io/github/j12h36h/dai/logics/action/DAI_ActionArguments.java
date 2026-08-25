package io.github.j12h36h.dai.logics.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import java.util.Locale;

/**
 * Open-ended action payload used by DAI 3.0+.
 *
 * Existing fixed DAI_ActionDefinition fields remain supported. New runtime
 * features should prefer arguments so adding a capability no longer requires
 * widening the action record or packet schema again.
 */
public final class DAI_ActionArguments {

    public static final DAI_ActionArguments EMPTY = new DAI_ActionArguments(new JsonObject());

    public static final Codec<DAI_ActionArguments> CODEC =
            Codec.PASSTHROUGH.xmap(
                    dynamic -> {
                        Object value = dynamic.convert(JsonOps.INSTANCE).getValue();
                        return value instanceof JsonObject object
                                ? new DAI_ActionArguments(object)
                                : EMPTY;
                    },
                    arguments -> new Dynamic<>(JsonOps.INSTANCE, arguments.json())
            );

    private final JsonObject json;

    public DAI_ActionArguments(JsonObject json) {
        this.json = json == null ? new JsonObject() : json.deepCopy();
    }


    public static DAI_ActionArguments fromJson(String raw) {
        if (raw == null || raw.isBlank()) return EMPTY;
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            return parsed.isJsonObject() ? new DAI_ActionArguments(parsed.getAsJsonObject()) : EMPTY;
        } catch (RuntimeException ignored) {
            return EMPTY;
        }
    }

    public JsonObject json() {
        return json.deepCopy();
    }

    public boolean isEmpty() {
        return json.size() == 0;
    }

    public boolean has(String key) {
        return element(key) != null;
    }

    public JsonElement element(String key) {
        if (key == null || key.isBlank()) return null;
        JsonElement value = json.get(key.trim());
        return value == null || value instanceof JsonNull ? null : value;
    }

    public String string(String key, String fallback) {
        JsonElement value = element(key);
        if (value == null) return fallback;
        try {
            return value.isJsonPrimitive() ? value.getAsString() : value.toString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public String normalized(String key, String fallback) {
        String value = string(key, fallback);
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public boolean bool(String key, boolean fallback) {
        JsonElement value = element(key);
        if (value == null) return fallback;
        try {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean();
            String text = value.getAsString().trim().toLowerCase(Locale.ROOT);
            if (text.equals("true") || text.equals("yes") || text.equals("1") || text.equals("on")) return true;
            if (text.equals("false") || text.equals("no") || text.equals("0") || text.equals("off")) return false;
        } catch (RuntimeException ignored) {}
        return fallback;
    }

    public int integer(String key, int fallback) {
        double value = number(key, fallback);
        if (!Double.isFinite(value)) return fallback;
        if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int)Math.round(value);
    }

    public double number(String key, double fallback) {
        JsonElement value = element(key);
        if (value == null) return fallback;
        try {
            double parsed = value.getAsDouble();
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public JsonObject object(String key) {
        JsonElement value = element(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject().deepCopy() : new JsonObject();
    }

    public JsonArray array(String key) {
        JsonElement value = element(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray().deepCopy() : new JsonArray();
    }

    public double[] vector(String key, double x, double y, double z) {
        JsonElement value = element(key);
        if (value == null) return new double[]{x, y, z};
        try {
            if (value.isJsonArray()) {
                JsonArray array = value.getAsJsonArray();
                if (array.size() >= 3) {
                    return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
                }
            }
            if (value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                return new double[]{
                        number(object, "x", x),
                        number(object, "y", y),
                        number(object, "z", z)
                };
            }
        } catch (RuntimeException ignored) {}
        return new double[]{x, y, z};
    }

    private static double number(JsonObject object, String key, double fallback) {
        try {
            if (object != null && object.has(key)) {
                double value = object.get(key).getAsDouble();
                if (Double.isFinite(value)) return value;
            }
        } catch (RuntimeException ignored) {}
        return fallback;
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
