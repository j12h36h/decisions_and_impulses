package io.github.j12h36h.dai.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.worldgen.DAI_VanillaDataBridge;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Runtime capability discovery for the open DAI JSON API.
 *
 * Minecraft keeps making more content data-driven. Rather than waiting for a
 * hand-written DAI feature flag, this catalog reflects the registries and
 * built-in DataComponents present in the actual running game and publishes a
 * machine-readable report for creators/tooling.
 */
public final class DAI_RuntimeCapabilities {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Set<String> registries = Set.of();
    private static volatile Set<String> dataComponents = Set.of();

    private DAI_RuntimeCapabilities() {}

    public static synchronized void refresh() {
        registries = discoverRegistries();
        dataComponents = discoverStaticNames("net.minecraft.core.component.DataComponents");
        writeReport();

        DAI_Core.LOGGER.info(
                "<DAI>: Runtime capability catalog: {} Minecraft registry key(s), {} built-in data component(s), {} direct DAI->Mojang bridge folder(s).",
                registries.size(),
                dataComponents.size(),
                DAI_VanillaDataBridge.knownFolders().size()
        );
    }

    public static Set<String> registries() {
        if (registries.isEmpty()) refresh();
        return registries;
    }

    public static Set<String> dataComponents() {
        if (dataComponents.isEmpty()) refresh();
        return dataComponents;
    }

    public static boolean hasRegistry(String id) {
        String normalized = normalize(id);
        if (normalized.isBlank()) return false;
        return registries().contains(normalized)
                || registries().stream().anyMatch(value -> value.endsWith("/" + normalized)
                || value.endsWith(":" + normalized)
                || value.equals(normalized));
    }

    public static boolean hasDataComponent(String idOrField) {
        String normalized = normalize(idOrField).replace(':', '_').replace('/', '_').toUpperCase(Locale.ROOT);
        return dataComponents().contains(normalized);
    }

    public static Map<String, String> bridgedFolders() {
        return DAI_VanillaDataBridge.knownFolders();
    }

    public static Path reportPath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(DAI_Core.MODID)
                .resolve("capabilities.json")
                .toAbsolutePath().normalize();
    }

    private static Set<String> discoverRegistries() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        try {
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
            for (Field field : registriesClass.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                Object value = field.get(null);
                if (value == null) continue;

                String id = resourceKeyLocation(value);
                if (!id.isBlank()) result.add(id);
                else result.add(field.getName().toLowerCase(Locale.ROOT));
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not reflect Minecraft Registries catalog.", exception);
        }
        return Set.copyOf(result);
    }

    private static String resourceKeyLocation(Object key) {
        for (String methodName : new String[]{"location", "identifier"}) {
            try {
                Object value = key.getClass().getMethod(methodName).invoke(key);
                if (value != null) return normalize(value.toString());
            } catch (ReflectiveOperationException ignored) {}
        }
        return "";
    }

    private static Set<String> discoverStaticNames(String className) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        try {
            Class<?> type = Class.forName(className);
            for (Field field : type.getFields()) {
                if (Modifier.isStatic(field.getModifiers())) result.add(field.getName());
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not reflect '{}'.", className, exception);
        }
        return Set.copyOf(result);
    }

    private static void writeReport() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("format", 1);

            JsonArray registryArray = new JsonArray();
            registries.stream().sorted().forEach(registryArray::add);
            root.add("minecraft_registries", registryArray);

            JsonArray componentArray = new JsonArray();
            dataComponents.stream().sorted().forEach(componentArray::add);
            root.add("minecraft_data_components", componentArray);

            JsonObject bridges = new JsonObject();
            DAI_VanillaDataBridge.knownFolders().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> bridges.addProperty(entry.getKey(), entry.getValue()));
            root.add("dai_mojang_registry_bridges", bridges);
            root.addProperty("open_registry_bridge_folder", "dai_registry");
            root.addProperty("open_data_bridge_folder", "dai_data");
            root.addProperty("native_item_component_field", "components");

            Path path = reportPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not write runtime capability catalog.", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
