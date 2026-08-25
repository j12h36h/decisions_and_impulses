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
                if ("automation".equals(requestedKind)) {
                    if (!"automation".equals(session.kind) || session.draft == null) {
                        session.kind = "automation";
                        session.id = safeId(payload.id());
                        session.draft = createDraft("automation", session.id, player.position());
                    }
                    session.hologram = false;
                } else if (session.draft == null || "automation".equals(session.kind)) {
                    session.kind = requestedKind;
                    session.id = safeId(payload.id());
                    session.draft = createDraft(session.kind, session.id, player.position());
                    session.hologram = true;
                }
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
                switch (mode) {
                    case "edit" -> {
                        cleanupSimulation(player, session);
                        session.test = false;
                        session.hologram = true;
                    }
                    case "simulate", "live" -> {
                        cleanupSimulation(player, session);
                        session.hologram = true;
                        session.test = true;
                        spawnSimulation(player, session);
                    }
                    default -> {
                        cleanupSimulation(player, session);
                        session.test = false;
                        session.hologram = true;
                    }
                }
                message(player, "Creator mode: " + (mode.isBlank() ? "preview" : mode) + ".");
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
            if (session.open && session.test && "physics".equals(session.kind) && session.draft != null) return true;
        }
        return false;
    }

    public static DAI_PhysicsProfile testPhysics(Entity entity) {
        if (entity == null || SESSIONS.isEmpty()) return null;
        DAI_PhysicsProfile best = null;
        double priority = -Double.MAX_VALUE;
        for (Session session : SESSIONS.values()) {
            if (!session.open || !session.test || !"physics".equals(session.kind) || session.draft == null) continue;
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
            if (session.draft != null && "physics".equals(session.kind) && session.id.equals(id)) {
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
            if (session == null || !session.open || !session.hologram || session.draft == null || "automation".equals(session.kind)) continue;
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
        if ("automation".equals(session.kind)
                && DAI_ActionDefinition.CODEC.parse(JsonOps.INSTANCE, session.draft).result().isEmpty()) {
            message(player, "Automation save blocked: JSON does not match the DAI action codec.");
            return;
        }
        Path path = exportPath(player.level().getServer(), session.kind, session.id);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(session.draft) + System.lineSeparator(), StandardCharsets.UTF_8);
            Path readme = player.level().getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("dai").resolve("creator").resolve("export").resolve("DAI_CREATOR_README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme,
                        "DAI Creator export. Copy the data/ folder into a datapack. Registry-static additions may require a full restart. Automation Creator exports live under logics/definitions/creator.\n",
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

        // Physics drafts get an in-world gravity vector, making ceiling/wall
        // gravity immediately readable before simulation is enabled.
        if ("physics".equals(session.kind)) {
            Vec3 gravity = new Vec3(
                    number(session.draft, "gravity_x", 0.0D),
                    number(session.draft, "gravity_y", -1.0D),
                    number(session.draft, "gravity_z", 0.0D)
            );
            if (gravity.lengthSqr() > 1.0E-8D) {
                Vec3 direction = gravity.normalize();
                double length = Math.min(3.5D, Math.max(1.25D, Math.min(w, Math.min(h, d)) * 0.55D));
                for (int i = 1; i <= 7; i++) {
                    Vec3 point = center.add(direction.scale(length * i / 7.0D));
                    holo(player, point.x, point.y, point.z);
                }
                Vec3 tip = center.add(direction.scale(length));
                Vec3 side = Math.abs(direction.y) < 0.9D
                        ? direction.cross(new Vec3(0, 1, 0)).normalize()
                        : direction.cross(new Vec3(1, 0, 0)).normalize();
                Vec3 wingBase = tip.subtract(direction.scale(0.38D));
                Vec3 wingA = wingBase.add(side.scale(0.24D));
                Vec3 wingB = wingBase.subtract(side.scale(0.24D));
                holo(player, wingA.x, wingA.y, wingA.z);
                holo(player, wingB.x, wingB.y, wingB.z);
            }
        }
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
        if ("physics".equals(session.kind)) return;

        DAI_GameCustomizationDefinition generic = customizationDefinition(session.draft);
        if (generic != null && !generic.event("test").isBlank()) {
            DAI_RuntimeDispatch.dispatch(player, generic.event("test"));
        }

        Vec3 c = center(session.draft, player.position());
        String carrier = string(session.draft, "carrier");
        String entityId = carrier;
        if (entityId.isBlank() && ("entity".equals(session.kind) || "vehicle".equals(session.kind))) entityId = session.id;
        if (("entity".equals(session.kind) || "vehicle".equals(session.kind) || "projectile".equals(session.kind)) && !entityId.isBlank()) {
            String tag = simulationTag(player);
            String command = "command:summon " + entityId + " " + format(c.x) + " " + format(c.y) + " " + format(c.z)
                    + " {Tags:[\"" + tag + "\"],NoGravity:1b,Invulnerable:1b,Glowing:1b,Silent:1b}";
            DAI_RuntimeDispatch.dispatch(player, command);
            return;
        }
        if ("particle".equals(session.kind)) {
            String particle = carrier.isBlank() ? session.id : carrier;
            DAI_RuntimeDispatch.dispatch(player, "command:particle " + particle + " " + format(c.x) + " " + format(c.y) + " " + format(c.z) + " 0 0 0 0 24 force @s");
            return;
        }
        if ("sound".equals(session.kind) || "music".equals(session.kind)) {
            String sound = carrier.isBlank() ? session.id : carrier;
            DAI_RuntimeDispatch.dispatch(player, "command:playsound " + sound + " master @s ~ ~ ~ 1 1");
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
        String kind = safeKind(kindRaw);
        JsonObject root = new JsonObject();
        root.addProperty("display_name", "Creator Draft");
        root.addProperty("description", "Generated by the in-game DAI Creator");

        if ("automation".equals(kind)) {
            root = new JsonObject();
            root.addProperty("type", "sequence");
            root.add("sequence", new com.google.gson.JsonArray());
            return root;
        }

        if (DAI_GameCustomizationKind.parse(kind) != null) {
            root.addProperty("target", format(pos.x) + " " + format(pos.y) + " " + format(pos.z));
            root.add("properties", new JsonObject());
            root.add("numbers", new JsonObject());
            root.add("flags", new JsonObject());
            root.add("events", new JsonObject());
            setNumber(root, "x", pos.x); setNumber(root, "y", pos.y); setNumber(root, "z", pos.z);
            setNumber(root, "width", 4); setNumber(root, "height", 4); setNumber(root, "depth", 4);
            setFlag(root, "enabled", true);
        }

        if ("physics".equals(kind)) {
            setProperty(root, "shape", "box");
            setProperty(root, "affects", "all");
            setNumber(root, "gravity_x", 0); setNumber(root, "gravity_y", -1); setNumber(root, "gravity_z", 0);
            setNumber(root, "gravity_strength", 0.08); setNumber(root, "transition_ticks", 12);
            setNumber(root, "movement_acceleration", 0.035); setNumber(root, "movement_scale", 1.0);
            setNumber(root, "jump_velocity", 0.42); setNumber(root, "terminal_speed", 3.92);
            setNumber(root, "linear_drag", 0.0); setNumber(root, "surface_drag", 0.08);
            setNumber(root, "restitution", 0.0); setNumber(root, "max_speed", 0.0);
            setFlag(root, "align_camera", true); setFlag(root, "align_entity", true);
            setFlag(root, "project_movement", true); setFlag(root, "reset_fall_distance", true);
        } else if ("portal".equals(kind) || "interactive".equals(kind)) {
            setProperty(root, "shape", "box");
            setProperty(root, "affects", "all");
        } else if ("vehicle".equals(kind)) {
            setNumber(root, "acceleration", 0.045); setNumber(root, "max_speed", 0.8);
            setNumber(root, "turn_rate", 6); setNumber(root, "braking", 0.14); setNumber(root, "drag", 0.06);
            setFlag(root, "camera_steering", true); setFlag(root, "gravity", true);
        } else if ("block".equals(kind)) {
            root.addProperty("registry_backed", true);
            root.addProperty("native_registry", "block");
            root.add("block", new JsonObject());
            root.add("events", new JsonObject());
        } else if ("entity".equals(kind)) {
            root.addProperty("registry_backed", true);
            root.addProperty("native_registry", "entity");
            root.add("entity", new JsonObject());
            root.add("events", new JsonObject());
        } else if ("item".equals(kind)) {
            root.addProperty("registry_backed", true);
            root.addProperty("native_registry", "item");
            root.add("components", new JsonObject());
            root.add("events", new JsonObject());
        } else if ("particle".equals(kind)) {
            root.addProperty("registry_backed", true);
            root.addProperty("native_registry", "particle");
            root.add("particle", new JsonObject());
            root.add("events", new JsonObject());
        } else if ("effect".equals(kind)) {
            root.addProperty("registry_backed", true);
            root.addProperty("native_registry", "effect");
            root.add("effect", new JsonObject());
            root.add("events", new JsonObject());
        } else if ("potion".equals(kind)) {
            root.addProperty("registry_backed", true);
            root.addProperty("native_registry", "potion");
            root.add("potion", new JsonObject());
        } else if ("projectile".equals(kind)) {
            root.add("stats", new JsonObject());
            root.add("projectile", new JsonObject());
            root.add("events", new JsonObject());
        } else {
            root.add("events", new JsonObject());
        }
        return root;
    }

    private static void ensureDraft(Session session, ServerPlayer player) {
        if (session.draft != null) return;
        session.kind = "physics";
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
        if (session.kind == null || session.kind.isBlank()) session.kind = "physics";
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
        if ("automation".equals(kind)) return "logics/definitions/creator";
        DAI_GameCustomizationKind customization = DAI_GameCustomizationKind.parse(kind);
        if (customization != null) return customization.folder();
        DAI_ContentKind content = contentKind(kind);
        return content == null ? "dai_" + kind : content.folder();
    }

    private static DAI_ContentKind contentKind(String raw) {
        String kind = safeKind(raw);
        for (DAI_ContentKind value : DAI_ContentKind.values()) if (value.id().equals(kind) || value.folder().equals(kind)) return value;
        return null;
    }

    private static String safeKind(String raw) {
        String value = norm(raw);
        if (value.startsWith("dai_")) value = value.substring(4);
        return value.isBlank() ? "physics" : value;
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
        String kind = "physics";
        String id = "creator:untitled";
        JsonObject draft;
        final Deque<JsonObject> undo = new ArrayDeque<>();
        final Deque<JsonObject> redo = new ArrayDeque<>();
    }
}
