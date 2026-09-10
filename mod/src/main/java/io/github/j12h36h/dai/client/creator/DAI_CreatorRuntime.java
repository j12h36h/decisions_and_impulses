package io.github.j12h36h.dai.client.creator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.j12h36h.dai.creator.DAI_CreatorSchemaDefinition;
import io.github.j12h36h.dai.creator.DAI_CreatorSchemaRegistry;
import io.github.j12h36h.dai.experience.DAI_EarlyJsonRepository;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition;
import io.github.j12h36h.dai.physics.DAI_PhysicsProfile;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client-side Creator document model.
 *
 * No authorable feature is encoded here. Creator schemas provide folders,
 * templates, fields, variations, previews and editor metadata through JSON.
 */
public final class DAI_CreatorRuntime {
    public enum EditorMode { CREATE, BUILD, CODE, PREVIEW, SIMULATE }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean open;
    private static boolean test;
    private static EditorMode mode = EditorMode.CREATE;
    private static String schemaId = "";
    private static String id = "creator:untitled";
    private static JsonObject draft = new JsonObject();

    private DAI_CreatorRuntime() {}

    public static void open(Entity player) {
        open = true;
        ensureSchema();
        if (draft == null) draft = new JsonObject();
    }

    public static void close() {
        open = false;
        test = false;
        mode = EditorMode.CREATE;
    }

    public static boolean isOpen() { return open; }
    public static boolean isTesting() { return test; }
    public static EditorMode mode() { return mode; }
    public static void setMode(EditorMode next) {
        mode = next == null ? EditorMode.CREATE : next;
        test = mode == EditorMode.SIMULATE;
    }

    /** Compatibility name: kind is now the selected schema's output folder. */
    public static String kind() { return folder(); }
    public static String id() { return id; }
    public static String schemaId() { ensureSchema(); return schemaId; }
    public static JsonObject draft() { return draft == null ? new JsonObject() : draft.deepCopy(); }
    public static String rawJson() { return GSON.toJson(draft()); }

    public static DAI_CreatorSchemaDefinition schema() {
        ensureSchema();
        return DAI_CreatorSchemaRegistry.get(schemaId);
    }

    public static String folder() {
        DAI_CreatorSchemaDefinition schema = schema();
        if (schema == null) return "creator_documents";
        String folder = sanitizeFolder(schema.folder());
        return folder.isBlank() ? "creator_documents" : folder;
    }

    public static void selectSchema(String requested) {
        DAI_CreatorSchemaDefinition resolved = DAI_CreatorSchemaRegistry.get(requested);
        if (resolved == null) {
            for (DAI_CreatorSchemaRegistry.Entry entry : DAI_CreatorSchemaRegistry.entries()) {
                if (entry.definition().folder().equals(requested)) {
                    schemaId = entry.id().toString();
                    return;
                }
            }
            return;
        }
        schemaId = requested;
    }

    /** Legacy signature retained; newKind may be a schema id or an output folder. */
    public static void create(String newKind, String newId, Vec3 pos) {
        selectSchema(newKind);
        createSelected(newId, pos);
    }

    public static void createSelected(String newId, Vec3 pos) {
        ensureSchema();
        id = safeId(newId);
        DAI_CreatorSchemaDefinition schema = schema();
        draft = schema == null ? new JsonObject() : schema.template();
        applyCreateContext(schema, pos == null ? Vec3.ZERO : pos);
        test = false;
        mode = EditorMode.CREATE;
    }

    /** Loads a definition generically from the selected schema's datapack folder. */
    public static boolean load(String newKind, String newId) {
        selectSchema(newKind);
        return loadSelected(newId);
    }

    public static boolean loadSelected(String newId) {
        ensureSchema();
        String requestedId = safeId(newId);
        String folder = folder();
        Map<String, JsonObject> scanned = DAI_EarlyJsonRepository.scanClientData(folder, folder);
        JsonObject loaded = scanned.get(requestedId);
        if (loaded == null) return false;
        id = requestedId;
        draft = loaded.deepCopy();
        test = false;
        mode = EditorMode.BUILD;
        return true;
    }

    public static void set(String path, String value) {
        if (draft == null) draft = new JsonObject();
        setPath(draft, path, parseValue(value));
    }

    public static String get(String path, String fallback) {
        JsonElement value = getPath(draft, path);
        if (value == null || value.isJsonNull()) return fallback;
        try {
            if (value.isJsonPrimitive()) return value.getAsString();
            return GSON.toJson(value);
        } catch (RuntimeException ignored) { return fallback; }
    }

    public static boolean replaceRawJson(String raw) {
        try {
            JsonElement parsed = JsonParser.parseString(raw == null ? "{}" : raw);
            if (!parsed.isJsonObject()) return false;
            draft = parsed.getAsJsonObject();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void applyPatch(JsonObject patch) {
        if (patch == null) return;
        if (draft == null) draft = new JsonObject();
        merge(draft, patch);
    }

    public static void move(Vec3 pos) {
        DAI_CreatorSchemaDefinition schema = schema();
        if (schema == null || pos == null) return;
        JsonObject context = DAI_CreatorSchemaDefinition.object(schema.json(), "create_context");
        setConfigured(context, "x_path", pos.x);
        setConfigured(context, "y_path", pos.y);
        setConfigured(context, "z_path", pos.z);
    }

    public static void toggleTest() {
        test = !test;
        mode = test ? EditorMode.SIMULATE : EditorMode.PREVIEW;
    }

    /**
     * Existing physics runtime integration is exposed only when a schema asks
     * for the physics preview adapter. The schema, not the Creator UI, decides.
     */
    public static DAI_PhysicsProfile testPhysics(Entity entity) {
        DAI_CreatorSchemaDefinition schema = schema();
        if (!open || !test || schema == null || draft == null || entity == null) return null;
        JsonObject preview = DAI_CreatorSchemaDefinition.object(schema.json(), "preview");
        if (!"physics".equals(DAI_CreatorSchemaDefinition.normalized(
                DAI_CreatorSchemaDefinition.string(preview, "adapter", "")))) return null;
        DAI_GameCustomizationDefinition def = DAI_GameCustomizationDefinition.CODEC.parse(JsonOps.INSTANCE, draft)
                .result().orElse(null);
        if (def == null || !DAI_PhysicsProfile.contains(def, entity)
                || !DAI_PhysicsProfile.dimensionMatches(def, entity)
                || !DAI_PhysicsProfile.requirementsPass(def, entity)) return null;
        DAI_PhysicsProfile profile = DAI_PhysicsProfile.from(id, def);
        return profile != null && profile.affects(entity) ? profile : null;
    }

    private static void ensureSchema() {
        if (DAI_CreatorSchemaRegistry.get(schemaId) != null) return;
        List<DAI_CreatorSchemaRegistry.Entry> entries = DAI_CreatorSchemaRegistry.entries();
        schemaId = entries.isEmpty() ? "" : entries.getFirst().id().toString();
    }

    private static void applyCreateContext(DAI_CreatorSchemaDefinition schema, Vec3 pos) {
        if (schema == null) return;
        JsonObject context = DAI_CreatorSchemaDefinition.object(schema.json(), "create_context");
        setConfigured(context, "x_path", pos.x);
        setConfigured(context, "y_path", pos.y);
        setConfigured(context, "z_path", pos.z);
    }

    private static void setConfigured(JsonObject context, String key, double value) {
        String path = DAI_CreatorSchemaDefinition.string(context, key, "");
        if (!path.isBlank()) setPath(draft, path, new com.google.gson.JsonPrimitive(value));
    }

    private static void setPath(JsonObject root, String rawPath, JsonElement value) {
        if (root == null || rawPath == null || rawPath.isBlank()) return;
        String[] parts = rawPath.trim().split("\\.");
        JsonObject cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i].trim();
            if (part.isBlank()) return;
            JsonElement current = cursor.get(part);
            JsonObject child = current != null && current.isJsonObject() ? current.getAsJsonObject() : new JsonObject();
            cursor.add(part, child);
            cursor = child;
        }
        String leaf = parts[parts.length - 1].trim();
        if (!leaf.isBlank()) cursor.add(leaf, value == null ? com.google.gson.JsonNull.INSTANCE : value);
    }

    private static JsonElement getPath(JsonObject root, String rawPath) {
        if (root == null || rawPath == null || rawPath.isBlank()) return null;
        JsonElement cursor = root;
        for (String part : rawPath.trim().split("\\.")) {
            if (!cursor.isJsonObject()) return null;
            cursor = cursor.getAsJsonObject().get(part);
            if (cursor == null) return null;
        }
        return cursor;
    }

    private static JsonElement parseValue(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return new com.google.gson.JsonPrimitive(Boolean.parseBoolean(value));
        }
        try { return new com.google.gson.JsonPrimitive(Double.parseDouble(value)); }
        catch (NumberFormatException ignored) {}
        if ((value.startsWith("{") && value.endsWith("}")) || (value.startsWith("[") && value.endsWith("]"))) {
            try { return JsonParser.parseString(value); }
            catch (RuntimeException ignored) {}
        }
        return new com.google.gson.JsonPrimitive(raw == null ? "" : raw);
    }

    private static void merge(JsonObject target, JsonObject patch) {
        for (Map.Entry<String, JsonElement> entry : patch.entrySet()) {
            JsonElement incoming = entry.getValue();
            JsonElement existing = target.get(entry.getKey());
            if (incoming != null && incoming.isJsonObject() && existing != null && existing.isJsonObject()) {
                merge(existing.getAsJsonObject(), incoming.getAsJsonObject());
            } else {
                target.add(entry.getKey(), incoming == null ? com.google.gson.JsonNull.INSTANCE : incoming.deepCopy());
            }
        }
    }

    private static String safeId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) return "creator:untitled";
        if (!value.contains(":")) value = "creator:" + value;
        return net.minecraft.resources.Identifier.tryParse(value) == null ? "creator:untitled" : value;
    }

    private static String sanitizeFolder(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (value.startsWith("/") || value.contains("..")) return "";
        return value.replaceAll("[^a-z0-9_./-]", "");
    }
}
