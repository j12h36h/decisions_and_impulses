package io.github.j12h36h.dai.server.creator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.j12h36h.dai.content.DAI_ContentDefinition;
import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import io.github.j12h36h.dai.network.DAI_CreatorActionPayload;
import io.github.j12h36h.dai.physics.DAI_PhysicsProfile;
import io.github.j12h36h.dai.server.runtime.DAI_RuntimeDispatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative backing store for the in-game DAI Creator.
 *
 * The editor intentionally writes ordinary DAI JSON. Creator mode is an
 * authoring frontend, not a second proprietary data format. Drafts are saved
 * under world/dai/creator/export/data/&lt;namespace&gt;/&lt;dai-folder&gt;/... so they can
 * be copied directly into a datapack.
 */
public final class DAI_CreatorServerRuntime {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static boolean initialized;

    private DAI_CreatorServerRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.register(DAI_CreatorServerRuntime.class);
        DAI_Core.LOGGER.info("<DAI>: In-game holographic Creator runtime initialized.");
    }

    public static void handle(ServerPlayer player, DAI_CreatorActionPayload payload) {
        if (player == null || payload == null) return;
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session());
        String op = norm(payload.operation());

        switch (op) {
            case "open" -> {
                session.open = true;
                String requestedKind = safeKind(payload.kind());
                if (session.draft == null || !requestedKind.equals(session.kind)) {
                    session.kind = requestedKind;
                    session.id = safeId(payload.id());
                    session.draft = createDraft(session.kind, session.id, player.position());
                }
                session.hologram = true;
                message(player, "Creator Mode online. Drafts export to world/dai/creator/export.");
            }
            case "close" -> {
                cleanupSimulation(player, session);
                session.open = false;
                session.test = false;
                message(player, "Creator Mode closed.");
            }
            case "create" -> {
                checkpoint(session);
                cleanupSimulation(player, session);
                session.kind = safeKind(payload.kind());
                session.id = safeId(payload.id());
                session.draft = createDraft(session.kind, session.id, player.position());
                session.test = false;
                session.hologram = true;
                message(player, "Created " + session.kind + " draft " + session.id + ".");
            }
            case "load" -> load(player, session, payload.kind(), payload.id());
            case "set" -> {
                ensureDraft(session, player);
                checkpoint(session);
                setPath(session.draft, payload.key(), payload.value());
                normalizeIdentity(session);
                message(player, "Set " + payload.key() + " = " + payload.value());
            }
            case "raw_json" -> {
                try {
                    JsonElement parsed = JsonParser.parseString(payload.value());
                    if (!parsed.isJsonObject()) throw new IllegalArgumentException("Root must be an object");
                    checkpoint(session);
                    session.draft = parsed.getAsJsonObject();
                    normalizeIdentity(session);
                    message(player, "Replaced draft JSON.");
                } catch (RuntimeException exception) {
                    message(player, "Invalid JSON: " + exception.getMessage());
                }
            }
            case "move_here" -> {
                ensureDraft(session, player);
                checkpoint(session);
                setNumber(session.draft, "x", payload.x());
                setNumber(session.draft, "y", payload.y());
                setNumber(session.draft, "z", payload.z());
                session.draft.addProperty("target", format(payload.x()) + " " + format(payload.y()) + " " + format(payload.z()));
            }
            case "nudge" -> {
                ensureDraft(session, player);
                checkpoint(session);
                setNumber(session.draft, "x", number(session.draft, "x", player.getX()) + payload.x());
                setNumber(session.draft, "y", number(session.draft, "y", player.getY()) + payload.y());
                setNumber(session.draft, "z", number(session.draft, "z", player.getZ()) + payload.z());
                syncTarget(session.draft);
            }
            case "resize" -> {
                ensureDraft(session, player);
                checkpoint(session);
                setNumber(session.draft, "width", Math.max(0.05, number(session.draft, "width", 3) + payload.x()));
                setNumber(session.draft, "height", Math.max(0.05, number(session.draft, "height", 3) + payload.y()));
                setNumber(session.draft, "depth", Math.max(0.05, number(session.draft, "depth", 3) + payload.z()));
            }
            case "hologram" -> {
                session.hologram = !session.hologram;
                message(player, "Hologram preview " + (session.hologram ? "enabled" : "disabled") + ".");
            }
            case "mode" -> {
                ensureDraft(session, player);
                String mode = norm(payload.value());
                session.previewAdapter = norm(payload.key());
                cleanupSimulation(player, session);
                session.hologram = true;
                session.test = mode.equals("simulate") || mode.equals("live");
                if (session.test) spawnSimulation(player, session);
                message(player, "Creator mode: " + (mode.isBlank() ? "preview" : mode) + ".");
            }
            case "preview_config" -> {
                session.previewAdapter = norm(payload.key());
                session.previewAction = payload.value() == null ? "" : payload.value().trim();
            }
            case "test" -> {
                ensureDraft(session, player);
                session.test = !session.test;
                if (session.test) spawnSimulation(player, session);
                else cleanupSimulation(player, session);
                message(player, "Live simulation " + (session.test ? "started" : "stopped") + ".");
            }
            case "run_event" -> {
                ensureDraft(session, player);
                DAI_GameCustomizationDefinition def = customizationDefinition(session.draft);
                String ref = def == null ? "" : def.event(payload.key().isBlank() ? "test" : payload.key());
                if (ref.isBlank()) ref = string(session.draft, "command");
                if (!ref.isBlank()) DAI_RuntimeDispatch.dispatch(player, ref);
            }
            case "save" -> save(player, session);
            case "undo" -> undo(player, session);
            case "redo" -> redo(player, session);
            case "duplicate" -> {
                ensureDraft(session, player);
                checkpoint(session);
                session.id = safeId(payload.id().isBlank() ? session.id + "_copy" : payload.id());
                message(player, "Duplicated draft as " + session.id + ".");
            }
            case "delete" -> deleteSaved(player, session);
            default -> message(player, "Unknown Creator operation: " + op);
        }
    }

    /** Live unsaved physics previews are visible to every affected entity. */
    public static boolean hasPhysicsTests() {
        for (Session session : SESSIONS.values()) {
            if (session.open && session.test && "physics".equals(session.previewAdapter) && session.draft != null) return true;
        }
        return false;
    }

    public static DAI_PhysicsProfile testPhysics(Entity entity) {
        if (entity == null || SESSIONS.isEmpty()) return null;
        DAI_PhysicsProfile best = null;
        double priority = -Double.MAX_VALUE;
        for (Session session : SESSIONS.values()) {
            if (!session.open || !session.test || !"physics".equals(session.previewAdapter) || session.draft == null) continue;
            DAI_GameCustomizationDefinition def = customizationDefinition(session.draft);
            if (def == null || !DAI_PhysicsProfile.dimensionMatches(def, entity)
                    || !DAI_PhysicsProfile.requirementsPass(def, entity)
                    || !DAI_PhysicsProfile.contains(def, entity)) continue;
            DAI_PhysicsProfile profile = DAI_PhysicsProfile.from(session.id, def);
            if (profile == null || !profile.affects(entity)) continue;
            double p = def.number("priority", 1_000_000.0D);
            if (best == null || p > priority) {
                best = profile;
                priority = p;
            }
        }
        return best;
    }

    public static DAI_GameCustomizationDefinition definitionOrRegistryPhysics(String id) {
        if (id == null || id.isBlank()) return null;
        for (Session session : SESSIONS.values()) {
            if (session.draft != null && "physics".equals(session.previewAdapter) && session.id.equals(id)) {
                DAI_GameCustomizationDefinition def = customizationDefinition(session.draft);
                if (def != null) return def;
            }
        }
        var entry = DAI_GameCustomizationRegistry.get(DAI_GameCustomizationKind.PHYSICS, id);
        return entry == null ? null : entry.definition();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Session session = SESSIONS.get(player.getUUID());
            if (session == null || !session.open || !session.hologram || session.draft == null) continue;
            if ((player.tickCount & 3) == 0) renderHologram(player, session);
        }
    }

    private static void load(ServerPlayer player, Session session, String kindRaw, String idRaw) {
        String kind = safeKind(kindRaw);
        String id = safeId(idRaw);
        JsonObject loaded = loadFromRegistry(kind, id);
        if (loaded == null) {
            Path path = exportPath(player.level().getServer(), kind, id);
            if (Files.isRegularFile(path)) {
                try {
                    JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                    if (parsed.isJsonObject()) loaded = parsed.getAsJsonObject();
                } catch (IOException | RuntimeException ignored) {
                    // Friendly message below.
                }
            }
        }
        if (loaded == null) {
            message(player, "Could not load " + kind + " " + id + ".");
            return;
        }
        checkpoint(session);
        cleanupSimulation(player, session);
        session.kind = kind;
        session.id = id;
        session.draft = loaded;
        message(player, "Loaded " + kind + " " + id + " into Creator.");
    }

    private static JsonObject loadFromRegistry(String kind, String id) {
        if ("automation".equals(kind)) {
            Identifier key = Identifier.tryParse(id);
            DAI_ActionDefinition action = key == null ? null : DAI_ActionLibrary.get(key);
            if (action == null) return null;
            return DAI_ActionDefinition.CODEC.encodeStart(JsonOps.INSTANCE, action)
                    .result().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(null);
        }
        DAI_GameCustomizationKind customization = DAI_GameCustomizationKind.parse(kind);
        if (customization != null) {
            var entry = DAI_GameCustomizationRegistry.get(customization, id);
            if (entry == null) return null;
            return DAI_GameCustomizationDefinition.CODEC.encodeStart(JsonOps.INSTANCE, entry.definition())
                    .result().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(null);
        }
        DAI_ContentKind content = contentKind(kind);
        if (content != null) {
            DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(id);
            if (entry == null || entry.kind() != content) return null;
            return DAI_ContentDefinition.CODEC.encodeStart(JsonOps.INSTANCE, entry.definition())
                    .result().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(null);
        }
        return null;
    }

    private static void save(ServerPlayer player, Session session) {
        ensureDraft(session, player);
        Path path = exportPath(player.level().getServer(), session.kind, session.id);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(session.draft) + System.lineSeparator(), StandardCharsets.UTF_8);
            Path readme = player.level().getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("dai").resolve("creator").resolve("export").resolve("DAI_CREATOR_README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme,
                        "DAI Creator export. Copy the data/ folder into a datapack. Creator schemas decide each document folder; registry-static additions may require a full restart.\n",
                        StandardCharsets.UTF_8);
            }
            message(player, "Saved " + session.id + " -> " + path.toAbsolutePath());
        } catch (IOException exception) {
            DAI_Core.LOGGER.warn("<DAI>: Creator could not save '{}'.", path, exception);
            message(player, "Creator save failed: " + exception.getMessage());
        }
    }

    private static void deleteSaved(ServerPlayer player, Session session) {
        Path path = exportPath(player.level().getServer(), session.kind, session.id);
        try {
            Files.deleteIfExists(path);
            message(player, "Deleted exported draft " + session.id + ".");
        } catch (IOException exception) {
            message(player, "Delete failed: " + exception.getMessage());
        }
    }

    private static Path exportPath(MinecraftServer server, String kind, String rawId) {
        Identifier id = Identifier.tryParse(safeId(rawId));
        if (id == null) id = Identifier.fromNamespaceAndPath("creator", "untitled");
        String folder = folder(kind);
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("dai").resolve("creator").resolve("export")
                .resolve("data").resolve(id.getNamespace()).resolve(folder)
                .resolve(id.getPath() + ".json");
    }

    private static void renderHologram(ServerPlayer player, Session session) {
        Vec3 center = center(session.draft, player.position());
        double w = Math.max(0.1, number(session.draft, "width", 3.0));
        double h = Math.max(0.1, number(session.draft, "height", 3.0));
        double d = Math.max(0.1, number(session.draft, "depth", w));
        AABB box = new AABB(center.x - w / 2, center.y - h / 2, center.z - d / 2,
                center.x + w / 2, center.y + h / 2, center.z + d / 2);

        // Wireframe box: enough samples to read cleanly as a holographic volume
        // while remaining intentionally cheap (rendered only every four ticks).
        int samples = 4;
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            double x = lerp(box.minX, box.maxX, t);
            double y = lerp(box.minY, box.maxY, t);
            double z = lerp(box.minZ, box.maxZ, t);

            holo(player, x, box.minY, box.minZ); holo(player, x, box.minY, box.maxZ);
            holo(player, x, box.maxY, box.minZ); holo(player, x, box.maxY, box.maxZ);
            holo(player, box.minX, y, box.minZ); holo(player, box.minX, y, box.maxZ);
            holo(player, box.maxX, y, box.minZ); holo(player, box.maxX, y, box.maxZ);
            holo(player, box.minX, box.minY, z); holo(player, box.minX, box.maxY, z);
            holo(player, box.maxX, box.minY, z); holo(player, box.maxX, box.maxY, z);
        }
        holo(player, center.x, center.y, center.z);

    }

    private static void holo(ServerPlayer player, double x, double y, double z) {
        DAI_RuntimeDispatch.dispatch(player,
                "command:particle minecraft:end_rod " + format(x) + " " + format(y) + " " + format(z)
                        + " 0 0 0 0 1 force @s");
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static void spawnSimulation(ServerPlayer player, Session session) {
        cleanupSimulation(player, session);
        /* Preview behavior is schema-driven. A creator schema may provide a
         * DAI action/command reference through preview.action. Physics remains
         * an engine adapter because its live test is consumed by PhysicsRuntime. */
        if (!session.previewAction.isBlank()) {
            DAI_RuntimeDispatch.dispatch(player, session.previewAction);
        }
    }

    private static void cleanupSimulation(ServerPlayer player, Session session) {
        if (player == null) return;
        DAI_RuntimeDispatch.dispatch(player, "command:kill @e[tag=" + simulationTag(player) + "]");
    }

    private static String simulationTag(ServerPlayer player) {
        return "dai_creator_" + player.getUUID().toString().replace("-", "");
    }

    private static JsonObject createDraft(String kindRaw, String id, Vec3 pos) {
        /* The actual document template comes from creator_schemas JSON and is
         * synchronized by the client via raw_json. Server-side Creator state
         * intentionally has no module-specific defaults. */
        return new JsonObject();
    }

    private static void ensureDraft(Session session, ServerPlayer player) {
        if (session.draft != null) return;
        session.kind = "creator_documents";
        session.id = "creator:untitled";
        session.draft = createDraft(session.kind, session.id, player.position());
    }

    private static void checkpoint(Session session) {
        if (session.draft == null) return;
        session.undo.push(session.draft.deepCopy());
        while (session.undo.size() > 64) session.undo.removeLast();
        session.redo.clear();
    }

    private static void undo(ServerPlayer player, Session session) {
        if (session.undo.isEmpty()) { message(player, "Nothing to undo."); return; }
        if (session.draft != null) session.redo.push(session.draft.deepCopy());
        session.draft = session.undo.pop();
        message(player, "Undo.");
    }

    private static void redo(ServerPlayer player, Session session) {
        if (session.redo.isEmpty()) { message(player, "Nothing to redo."); return; }
        if (session.draft != null) session.undo.push(session.draft.deepCopy());
        session.draft = session.redo.pop();
        message(player, "Redo.");
    }

    private static void normalizeIdentity(Session session) {
        if (session.kind == null || session.kind.isBlank()) session.kind = "creator_documents";
        if (session.id == null || session.id.isBlank()) session.id = "creator:untitled";
    }

    private static void setPath(JsonObject root, String rawPath, String value) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isBlank()) return;
        int dot = path.indexOf('.');
        if (dot < 0) {
            if (looksBoolean(value)) root.addProperty(path, Boolean.parseBoolean(value));
            else if (looksNumber(value)) root.addProperty(path, Double.parseDouble(value));
            else root.addProperty(path, value == null ? "" : value);
            return;
        }
        String group = path.substring(0, dot);
        String key = path.substring(dot + 1);
        JsonObject object = root.has(group) && root.get(group).isJsonObject() ? root.getAsJsonObject(group) : new JsonObject();
        root.add(group, object);
        if ("numbers".equals(group) && looksNumber(value)) object.addProperty(key, Double.parseDouble(value));
        else if ("flags".equals(group) && looksBoolean(value)) object.addProperty(key, Boolean.parseBoolean(value));
        else object.addProperty(key, value == null ? "" : value);
    }

    private static DAI_GameCustomizationDefinition customizationDefinition(JsonObject json) {
        if (json == null) return null;
        return DAI_GameCustomizationDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
    }

    private static String folder(String kindRaw) {
        String kind = safeKind(kindRaw);
        DAI_GameCustomizationKind customization = DAI_GameCustomizationKind.parse(kind);
        if (customization != null) return customization.folder();
        DAI_ContentKind content = contentKind(kind);
        if (content != null) return content.folder();
        /* DAI 3.9 Creator schemas send their output folder directly. Keeping
         * the path generic allows future datapack-defined modules to export
         * without another Java switch case. */
        return safeFolder(kind);
    }

    private static DAI_ContentKind contentKind(String raw) {
        String kind = safeKind(raw);
        for (DAI_ContentKind value : DAI_ContentKind.values()) if (value.id().equals(kind) || value.folder().equals(kind)) return value;
        return null;
    }

    private static String safeKind(String raw) {
        String value = norm(raw).replace('\\', '/');
        if (value.startsWith("/") || value.contains("..")) return "creator_documents";
        value = value.replaceAll("[^a-z0-9_./-]", "");
        return value.isBlank() ? "creator_documents" : value;
    }

    private static String safeFolder(String raw) {
        String value = safeKind(raw);
        return value.isBlank() ? "creator_documents" : value;
    }

    private static String runtimeKind(String raw) {
        String value = safeKind(raw);
        if (value.startsWith("dai_")) value = value.substring(4);
        int slash = value.lastIndexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private static String safeId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) return "creator:untitled";
        if (!value.contains(":")) value = "creator:" + value;
        return Identifier.tryParse(value) == null ? "creator:untitled" : value;
    }

    private static Vec3 center(JsonObject root, Vec3 fallback) {
        return new Vec3(number(root, "x", fallback.x), number(root, "y", fallback.y), number(root, "z", fallback.z));
    }

    private static void syncTarget(JsonObject root) {
        root.addProperty("target", format(number(root, "x", 0)) + " " + format(number(root, "y", 0)) + " " + format(number(root, "z", 0)));
    }

    private static double number(JsonObject root, String key, double fallback) {
        if (root == null) return fallback;
        JsonObject numbers = root.has("numbers") && root.get("numbers").isJsonObject() ? root.getAsJsonObject("numbers") : null;
        if (numbers == null || !numbers.has(key)) return fallback;
        try { return numbers.get(key).getAsDouble(); } catch (RuntimeException ignored) { return fallback; }
    }

    private static void setNumber(JsonObject root, String key, double value) {
        JsonObject numbers = root.has("numbers") && root.get("numbers").isJsonObject() ? root.getAsJsonObject("numbers") : new JsonObject();
        root.add("numbers", numbers); numbers.addProperty(key, value);
    }

    private static void setProperty(JsonObject root, String key, String value) {
        JsonObject properties = root.has("properties") && root.get("properties").isJsonObject() ? root.getAsJsonObject("properties") : new JsonObject();
        root.add("properties", properties); properties.addProperty(key, value);
    }

    private static void setFlag(JsonObject root, String key, boolean value) {
        JsonObject flags = root.has("flags") && root.get("flags").isJsonObject() ? root.getAsJsonObject("flags") : new JsonObject();
        root.add("flags", flags); flags.addProperty(key, value);
    }

    private static String string(JsonObject root, String key) {
        if (root == null || !root.has(key)) return "";
        try { return root.get(key).getAsString().trim(); } catch (RuntimeException ignored) { return ""; }
    }

    private static boolean looksNumber(String raw) {
        if (raw == null || raw.isBlank()) return false;
        try { Double.parseDouble(raw.trim()); return true; } catch (NumberFormatException ignored) { return false; }
    }

    private static boolean looksBoolean(String raw) {
        return raw != null && (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false"));
    }

    private static String norm(String raw) { return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT); }
    private static String format(double value) { return String.format(Locale.ROOT, "%.3f", value); }
    private static void message(ServerPlayer player, String text) { player.sendSystemMessage(Component.literal("[DAI CREATOR] " + text)); }

    private static final class Session {
        boolean open;
        boolean hologram = true;
        boolean test;
        String kind = "creator_documents";
        String id = "creator:untitled";
        String previewAdapter = "";
        String previewAction = "";
        JsonObject draft;
        final Deque<JsonObject> undo = new ArrayDeque<>();
        final Deque<JsonObject> redo = new ArrayDeque<>();
    }
}
