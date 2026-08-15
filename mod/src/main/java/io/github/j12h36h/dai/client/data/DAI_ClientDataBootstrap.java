package io.github.j12h36h.dai.client.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.j12h36h.dai.experience.DAI_EarlyJsonRepository;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionLoader;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.creation.DAI_RecipeParser;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogLoader;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogGroupLoader;
import io.github.j12h36h.dai.client.menus.DAI_ScreenProfileLoader;
import io.github.j12h36h.dai.client.logics.creation.DAI_RecipeLoader;
import io.github.j12h36h.dai.client.logics.validation.DAI_ValidationListener;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import io.github.j12h36h.dai.client.logics.creation.DAI_RecipeRegistry;
import io.github.j12h36h.dai.client.menus.DAI_MenuCategory;
import io.github.j12h36h.dai.client.menus.DAI_ScreenProfile;
import io.github.j12h36h.dai.client.menus.DAI_ScreenProfileManager;
import io.github.j12h36h.dai.client.menus.system.DAI_SystemDefinition;
import io.github.j12h36h.dai.client.menus.system.DAI_SystemLoader;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogDefinition;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogGroupDefinition;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecogGroupManager;
import io.github.j12h36h.dai.client.objectives.recognition.DAI_RecognitionLibrary;
import net.minecraft.resources.Identifier;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the client-automation portion of DAI data without relying on a logical
 * server reload event. This is what allows DAI client automation to keep its
 * local action/recognition/menu library while connected to a vanilla server.
 */
public final class DAI_ClientDataBootstrap {

    private static final String INDEX = "META-INF/dai_client_data.index";
    private static volatile boolean initialized;

    private DAI_ClientDataBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener(DAI_ClientDataBootstrap::registerClientReloadListeners);
        reloadLocalData();
    }

    private static void registerClientReloadListeners(AddServerReloadListenersEvent event) {
        // These definitions affect presentation/perception/automation only.
        // On an integrated server the physical client can consume the same
        // world datapack ResourceManager directly. Dedicated-server clients
        // keep their local library unless a later sync layer supplies data.
        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "client_recognition_groups"),
                new DAI_RecogGroupLoader()
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "client_systems"),
                new DAI_SystemLoader("menus/systems", DAI_MenuCategory.SYSTEM)
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "client_actions"),
                new DAI_SystemLoader("menus/actions", DAI_MenuCategory.ACTION)
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "client_recognition"),
                new DAI_RecogLoader()
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "client_screen_profiles"),
                new DAI_ScreenProfileLoader()
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "client_processing_recipes"),
                new DAI_RecipeLoader()
        );

        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "client_validation"),
                new DAI_ValidationListener()
        );
    }

    public static synchronized void reloadLocalData() {
        Map<String, JsonObject> builtIn = readBuiltInIndex();

        Map<Identifier, DAI_ActionDefinition> objectives =
                decodeFolder(builtIn, "objectives/definitions", DAI_ActionDefinition.CODEC);
        mergeExternal(objectives, "objectives/definitions", DAI_ActionDefinition.CODEC);
        DAI_ActionLoader.applyDefinitions(objectives, true, "client:objectives/definitions");

        Map<Identifier, DAI_ActionDefinition> logics =
                decodeFolder(builtIn, "logics/definitions", DAI_ActionDefinition.CODEC);
        mergeExternal(logics, "logics/definitions", DAI_ActionDefinition.CODEC);
        DAI_ActionLoader.applyDefinitions(logics, false, "client:logics/definitions");

        loadRecognitionGroups(builtIn);
        loadRecognitions(builtIn);
        loadMenus(builtIn);
        loadScreenProfiles(builtIn);
        loadRecipes(builtIn);

        DAI_Core.LOGGER.info(
                "<DAI>: Client-local data bootstrap complete: {} action(s), {} recognition(s), {} group(s), {} processing recipe(s).",
                io.github.j12h36h.dai.logics.action.DAI_ActionLibrary.size(),
                DAI_RecognitionLibrary.size(),
                DAI_RecogGroupManager.size(),
                DAI_RecipeRegistry.size()
        );
    }

    private static void loadRecognitionGroups(Map<String, JsonObject> builtIn) {
        Map<Identifier, DAI_RecogGroupDefinition> definitions =
                decodeFolder(builtIn, "objectives/groups", DAI_RecogGroupDefinition.CODEC);
        mergeExternal(definitions, "objectives/groups", DAI_RecogGroupDefinition.CODEC);

        DAI_RecogGroupManager.clear();
        definitions.forEach(DAI_RecogGroupManager::register);
    }

    private static void loadRecognitions(Map<String, JsonObject> builtIn) {
        Map<Identifier, DAI_RecogDefinition> definitions =
                decodeFolder(builtIn, "objectives/recognition", DAI_RecogDefinition.CODEC);
        mergeExternal(definitions, "objectives/recognition", DAI_RecogDefinition.CODEC);

        DAI_RecognitionLibrary.clear();
        definitions.forEach(DAI_RecognitionLibrary::register);
    }

    private static void loadMenus(Map<String, JsonObject> builtIn) {
        Map<Identifier, DAI_SystemDefinition> systems =
                decodeFolder(builtIn, "menus/systems", DAI_SystemDefinition.CODEC);
        mergeExternal(systems, "menus/systems", DAI_SystemDefinition.CODEC);
        new DAI_SystemLoader("menus/systems", DAI_MenuCategory.SYSTEM).applyDefinitions(systems);

        Map<Identifier, DAI_SystemDefinition> actions =
                decodeFolder(builtIn, "menus/actions", DAI_SystemDefinition.CODEC);
        mergeExternal(actions, "menus/actions", DAI_SystemDefinition.CODEC);
        new DAI_SystemLoader("menus/actions", DAI_MenuCategory.ACTION).applyDefinitions(actions);
    }

    private static void loadScreenProfiles(Map<String, JsonObject> builtIn) {
        Map<Identifier, DAI_ScreenProfile> profiles =
                decodeFolder(builtIn, "screen_profiles", DAI_ScreenProfile.CODEC);
        mergeExternal(profiles, "screen_profiles", DAI_ScreenProfile.CODEC);

        DAI_ScreenProfileManager.clear();
        profiles.forEach((id, profile) -> DAI_ScreenProfileManager.register(id.toString(), profile));
    }

    private static void loadRecipes(Map<String, JsonObject> builtIn) {
        LinkedHashMap<Identifier, JsonObject> recipes = new LinkedHashMap<>();
        collectRawFolder(recipes, builtIn, "dai_recipes");

        Map<String, JsonObject> external =
                DAI_EarlyJsonRepository.scanClientData("dai_recipes", "dai_recipes");
        external.forEach((rawId, json) -> {
            Identifier id = Identifier.tryParse(rawId);
            if (id != null && json != null) recipes.put(id, json);
        });

        DAI_RecipeRegistry.clear();
        recipes.forEach(DAI_RecipeParser::loadJson);
    }

    private static <T> void mergeExternal(
            Map<Identifier, T> destination,
            String folder,
            Codec<T> codec
    ) {
        Map<String, JsonObject> external =
                DAI_EarlyJsonRepository.scanClientData(folder, folder);

        external.forEach((rawId, json) -> {
            Identifier id = Identifier.tryParse(rawId);
            T value = decode(codec, json, rawId);
            if (id != null && value != null) destination.put(id, value);
        });
    }

    private static <T> Map<Identifier, T> decodeFolder(
            Map<String, JsonObject> indexed,
            String folder,
            Codec<T> codec
    ) {
        LinkedHashMap<Identifier, T> output = new LinkedHashMap<>();
        String marker = "/" + folder + "/";

        indexed.forEach((path, json) -> {
            int data = path.indexOf("data/");
            int namespaceStart = data < 0 ? -1 : data + "data/".length();
            int namespaceEnd = namespaceStart < 0 ? -1 : path.indexOf('/', namespaceStart);
            int markerAt = namespaceEnd < 0 ? -1 : path.indexOf(marker, namespaceEnd);
            if (namespaceStart < 0 || namespaceEnd < 0 || markerAt < 0) return;

            String namespace = path.substring(namespaceStart, namespaceEnd);
            String relative = path.substring(markerAt + marker.length());
            if (!relative.endsWith(".json")) return;
            relative = relative.substring(0, relative.length() - 5);

            Identifier id = Identifier.tryParse(namespace + ":" + relative);
            T value = decode(codec, json, path);
            if (id != null && value != null) output.put(id, value);
        });

        return output;
    }

    private static void collectRawFolder(
            Map<Identifier, JsonObject> output,
            Map<String, JsonObject> indexed,
            String folder
    ) {
        String marker = "/" + folder + "/";
        indexed.forEach((path, json) -> {
            int namespaceStart = path.indexOf("data/");
            if (namespaceStart < 0) return;
            namespaceStart += "data/".length();
            int namespaceEnd = path.indexOf('/', namespaceStart);
            int markerAt = namespaceEnd < 0 ? -1 : path.indexOf(marker, namespaceEnd);
            if (namespaceEnd < 0 || markerAt < 0) return;

            String namespace = path.substring(namespaceStart, namespaceEnd);
            String relative = path.substring(markerAt + marker.length());
            if (relative.endsWith(".json")) relative = relative.substring(0, relative.length() - 5);
            Identifier id = Identifier.tryParse(namespace + ":" + relative);
            if (id != null) output.put(id, json);
        });
    }

    private static <T> T decode(Codec<T> codec, JsonObject json, String source) {
        if (codec == null || json == null) return null;
        try {
            return codec.parse(JsonOps.INSTANCE, json).getOrThrow(
                    error -> new IllegalArgumentException(error)
            );
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed client-local data decode '{}'.", source, exception);
            return null;
        }
    }

    private static Map<String, JsonObject> readBuiltInIndex() {
        LinkedHashMap<String, JsonObject> output = new LinkedHashMap<>();
        ClassLoader loader = DAI_ClientDataBootstrap.class.getClassLoader();

        try (InputStream indexStream = loader.getResourceAsStream(INDEX)) {
            if (indexStream == null) {
                DAI_Core.LOGGER.warn("<DAI>: Client data index '{}' is missing.", INDEX);
                return output;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(indexStream, StandardCharsets.UTF_8))) {
                List<String> paths = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isBlank() && !line.startsWith("#"))
                        .toList();

                for (String path : paths) {
                    try (InputStream stream = loader.getResourceAsStream(path)) {
                        if (stream == null) continue;
                        JsonObject json = JsonParser.parseReader(
                                new InputStreamReader(stream, StandardCharsets.UTF_8)
                        ).getAsJsonObject();
                        output.put(path, json);
                    } catch (RuntimeException exception) {
                        DAI_Core.LOGGER.warn("<DAI>: Failed reading indexed client data '{}'.", path, exception);
                    }
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.error("<DAI>: Failed loading built-in client data index.", exception);
        }

        return output;
    }
}
