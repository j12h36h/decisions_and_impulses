package io.github.j12h36h.dai.client.creator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.content.DAI_ContentDefinition;
import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.physics.DAI_PhysicsProfile;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Client mirror used only for zero-latency Creator preview/camera prediction. */
public final class DAI_CreatorRuntime {
    public enum EditorMode { EDIT, PREVIEW, SIMULATE }

    private static boolean open;
    private static boolean test;
    private static EditorMode mode = EditorMode.PREVIEW;
    private static String kind = "physics";
    private static String id = "creator:untitled";
    private static JsonObject draft;

    private DAI_CreatorRuntime() {}

    public static void open(Entity player) {
        open = true;
        if (draft == null) draft = defaultPhysics(player == null ? Vec3.ZERO : player.position());
    }

    public static void close() { open = false; test = false; mode = EditorMode.PREVIEW; }
    public static boolean isOpen() { return open; }
    public static boolean isTesting() { return test; }
    public static EditorMode mode() { return mode; }
    public static void setMode(EditorMode next) {
        mode = next == null ? EditorMode.PREVIEW : next;
        test = mode == EditorMode.SIMULATE;
    }
    public static String kind() { return kind; }
    public static String id() { return id; }

    public static void create(String newKind, String newId, Vec3 pos) {
        kind = normalize(newKind).isBlank() ? "physics" : normalize(newKind);
        id = newId == null || newId.isBlank() ? "creator:untitled" : newId.trim().toLowerCase(Locale.ROOT);
        draft = "physics".equals(kind) ? defaultPhysics(pos) : new JsonObject();
        test = false;
        mode = EditorMode.PREVIEW;
    }


    public static boolean load(String newKind, String newId) {
        String requestedKind = normalize(newKind);
        String requestedId = newId == null ? "" : newId.trim().toLowerCase(Locale.ROOT);
        JsonObject loaded = null;

        DAI_GameCustomizationKind customization = DAI_GameCustomizationKind.parse(requestedKind);
        if (customization != null) {
            var entry = DAI_GameCustomizationRegistry.get(customization, requestedId);
            if (entry != null) {
                loaded = DAI_GameCustomizationDefinition.CODEC.encodeStart(JsonOps.INSTANCE, entry.definition())
                        .result().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(null);
            }
        } else {
            DAI_ContentKind contentKind = contentKind(requestedKind);
            DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(requestedId);
            if (entry != null && contentKind != null && entry.kind() == contentKind) {
                loaded = DAI_ContentDefinition.CODEC.encodeStart(JsonOps.INSTANCE, entry.definition())
                        .result().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(null);
            }
        }

        if (loaded == null) return false;
        kind = requestedKind;
        id = requestedId;
        draft = loaded;
        test = false;
        mode = EditorMode.PREVIEW;
        return true;
    }

    public static void set(String path, String value) {
        if (draft == null) draft = defaultPhysics(Vec3.ZERO);
        if (path == null || path.isBlank()) return;
        String[] parts = path.split("\\.", 2);
        if (parts.length == 1) {
            put(draft, parts[0], value);
            return;
        }
        JsonObject child = draft.has(parts[0]) && draft.get(parts[0]).isJsonObject() ? draft.getAsJsonObject(parts[0]) : new JsonObject();
        draft.add(parts[0], child);
        put(child, parts[1], value);
    }

    public static void move(Vec3 pos) {
        set("numbers.x", Double.toString(pos.x));
        set("numbers.y", Double.toString(pos.y));
        set("numbers.z", Double.toString(pos.z));
        if (draft != null) draft.addProperty("target", pos.x + " " + pos.y + " " + pos.z);
    }

    public static void toggleTest() {
        test = !test;
        mode = test ? EditorMode.SIMULATE : EditorMode.PREVIEW;
    }

    public static DAI_PhysicsProfile testPhysics(Entity entity) {
        if (!open || !test || !"physics".equals(kind) || draft == null || entity == null) return null;
        DAI_GameCustomizationDefinition def = DAI_GameCustomizationDefinition.CODEC.parse(JsonOps.INSTANCE, draft).result().orElse(null);
        if (def == null || !DAI_PhysicsProfile.contains(def, entity) || !DAI_PhysicsProfile.dimensionMatches(def, entity)
                || !DAI_PhysicsProfile.requirementsPass(def, entity)) return null;
        DAI_PhysicsProfile profile = DAI_PhysicsProfile.from(id, def);
        return profile != null && profile.affects(entity) ? profile : null;
    }

    private static JsonObject defaultPhysics(Vec3 pos) {
        JsonObject root = new JsonObject();
        root.addProperty("display_name", "Creator Physics Draft");
        root.addProperty("target", pos.x + " " + pos.y + " " + pos.z);
        JsonObject properties = new JsonObject();
        properties.addProperty("shape", "box");
        properties.addProperty("affects", "all");
        root.add("properties", properties);
        JsonObject numbers = new JsonObject();
        numbers.addProperty("x", pos.x); numbers.addProperty("y", pos.y); numbers.addProperty("z", pos.z);
        numbers.addProperty("width", 4); numbers.addProperty("height", 4); numbers.addProperty("depth", 4);
        numbers.addProperty("gravity_x", 0); numbers.addProperty("gravity_y", -1); numbers.addProperty("gravity_z", 0);
        numbers.addProperty("gravity_strength", 0.08); numbers.addProperty("transition_ticks", 12);
        numbers.addProperty("movement_acceleration", 0.035); numbers.addProperty("movement_scale", 1.0);
        numbers.addProperty("jump_velocity", 0.42); numbers.addProperty("terminal_speed", 3.92);
        numbers.addProperty("linear_drag", 0.0); numbers.addProperty("surface_drag", 0.08);
        numbers.addProperty("restitution", 0.0); numbers.addProperty("max_speed", 0.0);
        root.add("numbers", numbers);
        JsonObject flags = new JsonObject();
        flags.addProperty("enabled", true); flags.addProperty("align_camera", true); flags.addProperty("align_entity", true);
        flags.addProperty("project_movement", true); flags.addProperty("reset_fall_distance", true);
        root.add("flags", flags);
        root.add("events", new JsonObject());
        return root;
    }

    private static void put(JsonObject target, String key, String value) {
        if (value != null && (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))) {
            target.addProperty(key, Boolean.parseBoolean(value));
            return;
        }
        try {
            target.addProperty(key, Double.parseDouble(value));
        } catch (Exception ignored) {
            target.addProperty(key, value == null ? "" : value);
        }
    }

    private static DAI_ContentKind contentKind(String raw) {
        String value = normalize(raw);
        for (DAI_ContentKind kind : DAI_ContentKind.values()) {
            if (kind.id().equals(value) || kind.folder().equals(value)) return kind;
        }
        return null;
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
