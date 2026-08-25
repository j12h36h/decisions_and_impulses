package io.github.j12h36h.dai.server.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.api.DAI_StateStore;
import io.github.j12h36h.dai.api.DAI_StateValue;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.network.DAI_StateSyncPayload;
import io.github.j12h36h.dai.state.DAI_StateDefinition;
import io.github.j12h36h.dai.state.DAI_StateRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative scoped DAI variable store with JSON persistence. */
public final class DAI_ServerStateRuntime {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, DAI_StateValue> VALUES = new LinkedHashMap<>();
    private static Path stateFile;
    private static boolean initialized;

    private DAI_ServerStateRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener(DAI_ServerStateRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(DAI_ServerStateRuntime::onServerStopping);
        NeoForge.EVENT_BUS.addListener(DAI_ServerStateRuntime::onPlayerLoggedIn);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        stateFile = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("dai_state.json");
        load();
    }

    private static void onServerStopping(ServerStoppingEvent event) { save(); }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncAllVisible(player);
    }

    public static boolean mutate(ServerPlayer actor, String key, String operation, DAI_StateValue incoming, Entity scopedEntity) {
        if (actor == null) return false;
        String normalized = DAI_StateStore.normalizeKey(key);
        DAI_StateDefinition definition = DAI_StateRegistry.get(normalized);
        if (definition == null || !definition.serverOwned()) return false;
        if (!definition.accepts(incoming) && !operation.equals("clear")) return false;

        String storageKey = storageKey(actor, scopedEntity, normalized, definition);
        DAI_StateValue current = VALUES.getOrDefault(storageKey, definition.defaultValue());
        DAI_StateValue next = switch (operation) {
            case "set" -> incoming;
            case "add" -> DAI_StateValue.number((current.type() == DAI_StateValue.Type.NUMBER ? current.numberValue() : 0.0D) + incoming.numberValue());
            case "toggle" -> DAI_StateValue.bool(!(current.type() == DAI_StateValue.Type.BOOLEAN && current.booleanValue()));
            case "clear" -> null;
            default -> null;
        };
        if (!operation.equals("clear") && next == null) return false;
        if (next == null) VALUES.remove(storageKey); else VALUES.put(storageKey, next);
        if (definition.persistent()) save();
        syncVisible(actor, normalized, definition, next == null ? definition.defaultValue() : next, next != null);
        return true;
    }

    public static DAI_StateValue get(ServerPlayer actor, Entity scopedEntity, String key) {
        DAI_StateDefinition definition = DAI_StateRegistry.get(key);
        if (actor == null || definition == null || !definition.serverOwned()) return DAI_StateValue.missing();
        return VALUES.getOrDefault(storageKey(actor, scopedEntity, DAI_StateStore.normalizeKey(key), definition), definition.defaultValue());
    }

    public static void syncAllVisible(ServerPlayer player) {
        if (player == null) return;
        for (Map.Entry<String, DAI_StateDefinition> entry : DAI_StateRegistry.snapshot().entrySet()) {
            DAI_StateDefinition definition = entry.getValue();
            if (!definition.serverOwned() || !definition.sync()) continue;
            DAI_StateValue value = get(player, player, entry.getKey());
            syncVisible(player, entry.getKey(), definition, value, true);
        }
    }

    private static void syncVisible(ServerPlayer player, String key, DAI_StateDefinition definition, DAI_StateValue value, boolean present) {
        if (player == null || definition == null || !definition.sync()) return;
        DAI_StateValue safe = value == null ? definition.defaultValue() : value;
        PacketDistributor.sendToPlayer(player, new DAI_StateSyncPayload(
                key, definition.type(), definition.scope(), definition.defaultBoolean(), definition.defaultNumber(),
                definition.defaultString(), definition.persistent(), definition.sync(), definition.clientWritable(), present,
                safe.type() == DAI_StateValue.Type.BOOLEAN && safe.booleanValue(),
                safe.type() == DAI_StateValue.Type.NUMBER ? safe.numberValue() : 0.0D,
                safe.type() == DAI_StateValue.Type.STRING ? safe.stringValue() : ""
        ));
    }

    private static String storageKey(ServerPlayer actor, Entity scopedEntity, String key, DAI_StateDefinition definition) {
        String scopeId = switch (definition.scope()) {
            case "player" -> actor.getUUID().toString();
            case "entity" -> (scopedEntity == null ? actor : scopedEntity).getUUID().toString();
            case "dimension" -> actor.level().dimension().identifier().toString();
            case "world" -> "world";
            case "server" -> "server";
            default -> actor.getUUID().toString();
        };
        return definition.scope() + "|" + scopeId + "|" + key;
    }

    private static synchronized void load() {
        VALUES.clear();
        if (stateFile == null || !Files.isRegularFile(stateFile)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(stateFile, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject values = root.has("values") && root.get("values").isJsonObject() ? root.getAsJsonObject("values") : new JsonObject();
            values.entrySet().forEach(entry -> {
                if (!entry.getValue().isJsonObject()) return;
                JsonObject object = entry.getValue().getAsJsonObject();
                String type = object.has("type") ? object.get("type").getAsString() : "";
                try {
                    DAI_StateValue value = switch (type) {
                        case "boolean" -> DAI_StateValue.bool(object.get("value").getAsBoolean());
                        case "number" -> DAI_StateValue.number(object.get("value").getAsDouble());
                        case "string" -> DAI_StateValue.string(object.get("value").getAsString());
                        default -> null;
                    };
                    if (value != null) VALUES.put(entry.getKey(), value);
                } catch (RuntimeException ignored) {}
            });
            DAI_Core.LOGGER.info("<DAI>: Loaded {} persistent scoped state value(s).", VALUES.size());
        } catch (Exception exception) {
            DAI_Core.LOGGER.error("<DAI>: Could not load persistent scoped state.", exception);
        }
    }

    private static synchronized void save() {
        if (stateFile == null) return;
        try {
            JsonObject root = new JsonObject();
            JsonObject values = new JsonObject();
            for (Map.Entry<String, DAI_StateValue> entry : VALUES.entrySet()) {
                String declaredKey = entry.getKey().substring(entry.getKey().lastIndexOf('|') + 1);
                DAI_StateDefinition definition = DAI_StateRegistry.get(declaredKey);
                if (definition == null || !definition.persistent()) continue;
                JsonObject object = new JsonObject();
                DAI_StateValue value = entry.getValue();
                switch (value.type()) {
                    case BOOLEAN -> { object.addProperty("type", "boolean"); object.addProperty("value", value.booleanValue()); }
                    case NUMBER -> { object.addProperty("type", "number"); object.addProperty("value", value.numberValue()); }
                    case STRING -> { object.addProperty("type", "string"); object.addProperty("value", value.stringValue()); }
                    default -> { continue; }
                }
                values.add(entry.getKey(), object);
            }
            root.add("values", values);
            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            DAI_Core.LOGGER.error("<DAI>: Could not save persistent scoped state.", exception);
        }
    }
}
