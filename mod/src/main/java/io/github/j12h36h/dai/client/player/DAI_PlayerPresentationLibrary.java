package io.github.j12h36h.dai.client.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Resource-pack registry for experience-selectable custom player models/skins. */
public final class DAI_PlayerPresentationLibrary
        extends SimplePreparableReloadListener<Map<Identifier, DAI_PlayerPresentationLibrary.Profile>> {

    public static final String DIRECTORY = "dai/player_presentations";
    private static volatile Map<Identifier, Profile> PROFILES = Map.of();

    @Override
    protected Map<Identifier, Profile> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        LinkedHashMap<Identifier, Profile> loaded = new LinkedHashMap<>();
        if (resourceManager == null) return loaded;

        Map<Identifier, Resource> resources = resourceManager.listResources(
                DIRECTORY,
                id -> id.getPath().endsWith(".json")
        );

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier id = toProfileId(entry.getKey());
            if (id == null) continue;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                Profile profile = parse(id, root);
                if (profile.enabled()) loaded.put(id, profile);
            } catch (Throwable exception) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Could not load player presentation '{}' from '{}'.",
                        id,
                        entry.getKey(),
                        exception
                );
            }
        }
        return loaded;
    }

    @Override
    protected void apply(Map<Identifier, Profile> loaded, ResourceManager resourceManager, ProfilerFiller profiler) {
        PROFILES = loaded == null ? Map.of() : Map.copyOf(loaded);
        DAI_Core.LOGGER.info("<DAI>: Loaded {} player presentation profile(s).", PROFILES.size());
    }

    public static Profile get(String reference, String fallbackNamespace) {
        Identifier id = resolve(reference, fallbackNamespace);
        return id == null ? null : PROFILES.get(id);
    }

    public static int size() { return PROFILES.size(); }

    private static Profile parse(Identifier id, JsonObject root) {
        JsonObject third = object(root, "third_person");
        JsonObject first = object(root, "first_person");
        String model = string(third, "model", string(root, "model", ""));
        String texture = string(third, "texture", string(root, "texture", ""));
        return new Profile(
                id,
                bool(root, "enabled", true),
                model.trim(),
                textureId(texture, id.getNamespace()),
                transform(third, root),
                new FirstPerson(
                        bool(first, "enabled", first != null && !string(first, "model", "").isBlank()),
                        string(first, "model", "").trim(),
                        textureId(string(first, "texture", texture), id.getNamespace()),
                        transform(first, null),
                        string(first, "hand", "main").trim().toLowerCase(Locale.ROOT)
                )
        );
    }

    private static Transform transform(JsonObject preferred, JsonObject fallback) {
        JsonObject source = preferred == null ? fallback : preferred;
        float[] offset = vector(source, "offset", 0.0F, 0.0F, 0.0F);
        float[] rotation = vector(source, "rotation", 0.0F, 0.0F, 0.0F);
        float scale = number(source, "scale", fallback == null ? 1.0F : number(fallback, "scale", 1.0F));
        return new Transform(
                offset[0], offset[1], offset[2],
                rotation[0], rotation[1], rotation[2],
                Math.max(0.001F, Math.min(64.0F, scale))
        );
    }

    private static float[] vector(JsonObject root, String key, float x, float y, float z) {
        if (root == null) return new float[]{x, y, z};
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) return new float[]{x, y, z};
        JsonArray array = element.getAsJsonArray();
        return new float[]{
                array.size() > 0 ? safeFloat(array.get(0), x) : x,
                array.size() > 1 ? safeFloat(array.get(1), y) : y,
                array.size() > 2 ? safeFloat(array.get(2), z) : z
        };
    }

    private static float safeFloat(JsonElement value, float fallback) {
        try { return value.getAsFloat(); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static float number(JsonObject root, String key, float fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsFloat() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static JsonObject object(JsonObject root, String key) {
        if (root == null) return null;
        JsonElement value = root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String string(JsonObject root, String key, String fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        try { return root != null && root.has(key) ? root.get(key).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static Identifier textureId(String reference, String fallbackNamespace) {
        if (reference == null || reference.isBlank()) return null;
        String value = reference.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        Identifier parsed = Identifier.tryParse(value);
        if (parsed != null && value.contains(":")) return parsed;
        try { return Identifier.fromNamespaceAndPath(fallbackNamespace, value); }
        catch (Throwable ignored) { return null; }
    }

    private static Identifier resolve(String reference, String fallbackNamespace) {
        if (reference == null || reference.isBlank()) return null;
        String value = reference.trim().toLowerCase(Locale.ROOT);
        if (value.contains(":")) return Identifier.tryParse(value);
        try {
            String namespace = fallbackNamespace == null || fallbackNamespace.isBlank()
                    ? DAI_Core.MODID
                    : fallbackNamespace.trim().toLowerCase(Locale.ROOT);
            return Identifier.fromNamespaceAndPath(namespace, value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Identifier toProfileId(Identifier resourceId) {
        if (resourceId == null) return null;
        String path = resourceId.getPath();
        String prefix = DIRECTORY + "/";
        if (!path.startsWith(prefix) || !path.endsWith(".json")) return null;
        String local = path.substring(prefix.length(), path.length() - 5);
        if (local.isBlank()) return null;
        return Identifier.fromNamespaceAndPath(resourceId.getNamespace(), local);
    }

    public record Profile(
            Identifier id,
            boolean enabled,
            String model,
            Identifier texture,
            Transform thirdPerson,
            FirstPerson firstPerson
    ) {
        public Profile {
            model = model == null ? "" : model.trim();
            thirdPerson = thirdPerson == null ? Transform.IDENTITY : thirdPerson;
            firstPerson = firstPerson == null ? FirstPerson.DISABLED : firstPerson;
        }
    }

    public record FirstPerson(
            boolean enabled,
            String model,
            Identifier texture,
            Transform transform,
            String hand
    ) {
        public static final FirstPerson DISABLED = new FirstPerson(false, "", null, Transform.IDENTITY, "main");
        public FirstPerson {
            model = model == null ? "" : model.trim();
            transform = transform == null ? Transform.IDENTITY : transform;
            hand = hand == null || hand.isBlank() ? "main" : hand.trim().toLowerCase(Locale.ROOT);
        }
    }

    public record Transform(
            float x, float y, float z,
            float pitch, float yaw, float roll,
            float scale
    ) {
        public static final Transform IDENTITY = new Transform(0, 0, 0, 0, 0, 0, 1);
    }
}
