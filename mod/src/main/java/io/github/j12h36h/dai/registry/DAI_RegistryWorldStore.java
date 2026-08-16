package io.github.j12h36h.dai.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes the authoritative per-world DAI registry manifest under world/dai/. */
public final class DAI_RegistryWorldStore {

    private static volatile Path currentRoot;

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private DAI_RegistryWorldStore() {}

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener(DAI_RegistryWorldStore::onServerStarting);
        NeoForge.EVENT_BUS.addListener(DAI_RegistryWorldStore::onServerStopped);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        Path root = event.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("dai")
                .resolve("registry");

        currentRoot = root;
        writeWorldState(root);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        currentRoot = null;
        DAI_RegistryPreflight.resetSession();
    }

    /** Refreshes the currently running world's manifest after a /reload. */
    public static void refreshCurrentWorld() {
        Path root = currentRoot;
        if (root != null) {
            writeWorldState(root);
        }
    }

    public static void writeWorldState(Path root) {
        if (root == null) return;

        try {
            Files.createDirectories(root);

            Map<String, WorldEntry> history = readManifest(root.resolve("manifest.json"));
            for (WorldEntry entry : history.values()) {
                entry.active = false;
            }

            for (DAI_RegistrySpec spec : DAI_RegistryPreflight.desiredSpecs().values()) {
                history.put(spec.key(), new WorldEntry(spec, true));
            }

            writeManifest(root.resolve("manifest.json"), history.values());
            writeSpecList(
                    root.resolve("pending.json"),
                    DAI_RegistryPreflight.pendingSpecs().values(),
                    true
            );
            writeSpecList(
                    root.resolve("removed.json"),
                    DAI_RegistryPreflight.removedSpecs().values(),
                    true
            );
            writeSpecList(
                    root.resolve("registered.json"),
                    DAI_RegistryPreflight.registeredSpecs().values(),
                    false
            );

            DAI_Core.LOGGER.info(
                    "<DAI>: Wrote world registry manifest to '{}'.",
                    root
            );
        } catch (Exception exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Failed to write world registry manifest '{}'.",
                    root,
                    exception
            );
        }
    }

    private static Map<String, WorldEntry> readManifest(Path path) {
        LinkedHashMap<String, WorldEntry> result = new LinkedHashMap<>();
        if (!Files.isRegularFile(path)) return result;

        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return result;
            JsonArray entries = parsed.getAsJsonObject().getAsJsonArray("entries");
            if (entries == null) return result;

            for (JsonElement element : entries) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                DAI_RegistrySpec spec = DAI_RegistryCache.readSpec(object);
                if (spec == null) continue;
                boolean active = object.has("active") && object.get("active").getAsBoolean();
                result.put(spec.key(), new WorldEntry(spec, active));
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not read previous world registry manifest '{}'.",
                    path,
                    exception
            );
        }
        return result;
    }

    private static void writeManifest(Path path, Collection<WorldEntry> entries) throws Exception {
        JsonObject root = baseRoot(false);
        JsonArray array = new JsonArray();
        for (WorldEntry entry : entries) {
            JsonObject object = DAI_RegistryCache.writeSpec(entry.spec);
            object.addProperty("active", entry.active);
            object.addProperty("tombstoned", !entry.active);
            array.add(object);
        }
        root.add("entries", array);
        atomicWrite(path, root);
    }

    private static void writeSpecList(
            Path path,
            Collection<DAI_RegistrySpec> specs,
            boolean restartRequired
    ) throws Exception {
        JsonObject root = baseRoot(restartRequired && !specs.isEmpty());
        JsonArray array = new JsonArray();
        for (DAI_RegistrySpec spec : specs) {
            array.add(DAI_RegistryCache.writeSpec(spec));
        }
        root.add("entries", array);
        atomicWrite(path, root);
    }

    private static JsonObject baseRoot(boolean restartRequired) {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.addProperty("restart_required", restartRequired);
        return root;
    }

    private static void atomicWrite(Path target, JsonObject object) throws Exception {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(object), StandardCharsets.UTF_8);
        try {
            Files.move(
                    temp,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (Exception atomicFailure) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class WorldEntry {
        private final DAI_RegistrySpec spec;
        private boolean active;

        private WorldEntry(DAI_RegistrySpec spec, boolean active) {
            this.spec = spec;
            this.active = active;
        }
    }
}
