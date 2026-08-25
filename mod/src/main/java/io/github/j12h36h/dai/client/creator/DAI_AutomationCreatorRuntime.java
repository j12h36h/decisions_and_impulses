package io.github.j12h36h.dai.client.creator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionResolver;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/** Mutable client-side JSON draft used by the live Automation Creator. */
public final class DAI_AutomationCreatorRuntime {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static String id = "creator:automation_untitled";
    private static JsonObject draft = defaultDraft();

    private DAI_AutomationCreatorRuntime() {}

    public static String id() { return id; }
    public static JsonObject draft() { return draft == null ? defaultDraft() : draft.deepCopy(); }

    public static void create(String requested) {
        id = safeId(requested);
        draft = defaultDraft();
    }

    public static boolean load(String requested) {
        String normalized = safeId(requested);
        Identifier key = Identifier.tryParse(normalized);
        if (key == null) return false;
        DAI_ActionDefinition action = DAI_ActionLibrary.get(key);
        if (action == null) return false;
        JsonElement encoded = DAI_ActionDefinition.CODEC.encodeStart(JsonOps.INSTANCE, action).result().orElse(null);
        if (encoded == null || !encoded.isJsonObject()) return false;
        id = normalized;
        draft = encoded.getAsJsonObject();
        return true;
    }

    public static boolean replaceRaw(String raw) {
        try {
            JsonElement parsed = JsonParser.parseString(raw == null ? "" : raw);
            if (!parsed.isJsonObject()) return false;
            draft = parsed.getAsJsonObject();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void set(String path, String value) {
        if (draft == null) draft = defaultDraft();
        if (path == null || path.isBlank()) return;
        String[] parts = path.trim().split("\\.");
        JsonObject current = draft;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            JsonObject next = current.has(part) && current.get(part).isJsonObject()
                    ? current.getAsJsonObject(part) : new JsonObject();
            current.add(part, next);
            current = next;
        }
        put(current, parts[parts.length - 1], value);
    }

    public static DAI_ActionDefinition validate() {
        if (draft == null) return null;
        return DAI_ActionDefinition.CODEC.parse(JsonOps.INSTANCE, draft).result().orElse(null);
    }

    public static boolean applyLive(String requested) {
        DAI_ActionDefinition action = validate();
        Identifier key = Identifier.tryParse(safeId(requested));
        if (action == null || key == null) return false;
        id = key.toString();
        DAI_ActionLibrary.register(key, action);
        return true;
    }

    public static boolean test(String requested) {
        if (!applyLive(requested)) return false;
        DAI_ActionQueue.interruptAndDispatch(DAI_ActionResolver.resolve(id));
        return true;
    }

    public static String compactJson() {
        return draft == null ? "{}" : draft.toString();
    }

    public static String prettyJson() {
        return draft == null ? "{}" : GSON.toJson(draft);
    }

    private static JsonObject defaultDraft() {
        JsonObject root = new JsonObject();
        root.addProperty("type", "sequence");
        root.add("sequence", new com.google.gson.JsonArray());
        return root;
    }

    private static void put(JsonObject object, String key, String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) {
            object.addProperty(key, Boolean.parseBoolean(raw));
            return;
        }
        try { object.addProperty(key, Double.parseDouble(raw)); }
        catch (RuntimeException ignored) { object.addProperty(key, value == null ? "" : value); }
    }

    private static String safeId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) value = "creator:automation_untitled";
        if (!value.contains(":")) value = "creator:" + value;
        return Identifier.tryParse(value) == null ? "creator:automation_untitled" : value;
    }
}
