package io.github.j12h36h.dai.worldgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.experience.DAI_EarlyJsonRepository;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Compiles high-level DAI world/dimension JSON into vanilla dynamic-registry
 * files and exposes the result as a required hidden SERVER_DATA pack.
 *
 * Building this before Minecraft creates/loads a world is what makes DAI
 * dimensions and generated world presets real registry content rather than a
 * post-start command simulation.
 */
public final class DAI_GeneratedWorldDataPack {

    private static final String PACK_ID = DAI_Core.MODID + ":generated_world_data";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DAI_GeneratedWorldDataPack() {}

    public static void initialize(IEventBus modBus) {
        rebuild();
        modBus.addListener(DAI_GeneratedWorldDataPack::addPackFinder);
    }

    public static Path root() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(DAI_Core.MODID)
                .resolve("generated_world_data")
                .toAbsolutePath().normalize();
    }

    public static synchronized void rebuild() {
        Path root = root();
        try {
            clearDirectory(root);
            Files.createDirectories(root.resolve("data"));

            int biomes = compileBiomes(root);
            int dimensionTypes = compileDimensionTypes(root);
            int timelines = compileTimelines(root);
            int dimensions = compileDimensions(root);
            int presets = compileWorldPresets(root);
            int bridged = DAI_VanillaDataBridge.compile(root, (path, json) -> {
                try {
                    writeJson(path, json);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });

            DAI_Core.LOGGER.info(
                    "<DAI>: Prepared generated world/data pack: {} biome(s), {} dimension type(s), {} timeline(s), {} dimension(s), {} world preset(s), {} bridged Mojang registry entry(s).",
                    biomes,
                    dimensionTypes,
                    timelines,
                    dimensions,
                    presets,
                    bridged
            );
        } catch (Exception exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Failed to prepare generated world-data pack at '{}'.",
                    root,
                    exception
            );
        }
    }


    private static int compileBiomes(Path root) throws IOException {
        int count = 0;
        Map<String, JsonObject> definitions =
                DAI_EarlyJsonRepository.scan("dai_biomes", "biomes");

        for (Map.Entry<String, JsonObject> entry : definitions.entrySet()) {
            String id = normalizeId(entry.getKey());
            IdParts parts = split(id);
            if (parts == null) continue;

            Path target = root.resolve("data")
                    .resolve(parts.namespace())
                    .resolve("worldgen")
                    .resolve("biome")
                    .resolve(parts.path() + ".json");
            writeJson(target, DAI_BiomeCompiler.compile(entry.getValue()));
            count++;
        }
        return count;
    }

    private static int compileDimensionTypes(Path root) throws IOException {
        int count = 0;
        Map<String, JsonObject> definitions =
                DAI_EarlyJsonRepository.scan("dai_dimension_types", "dimension_types");

        for (Map.Entry<String, JsonObject> entry : definitions.entrySet()) {
            String id = normalizeId(entry.getKey());
            IdParts parts = split(id);
            if (parts == null) continue;

            Path target = root.resolve("data")
                    .resolve(parts.namespace())
                    .resolve("dimension_type")
                    .resolve(parts.path() + ".json");
            writeJson(target, DAI_DimensionTypeCompiler.compile(entry.getValue()));
            count++;
        }
        return count;
    }

    private static int compileTimelines(Path root) throws IOException {
        int count = 0;
        Map<String, JsonObject> definitions =
                DAI_EarlyJsonRepository.scan("dai_timelines", "timelines");

        for (Map.Entry<String, JsonObject> entry : definitions.entrySet()) {
            String id = normalizeId(entry.getKey());
            IdParts parts = split(id);
            if (parts == null) continue;

            Path target = root.resolve("data")
                    .resolve(parts.namespace())
                    .resolve("timeline")
                    .resolve(parts.path() + ".json");
            writeJson(target, DAI_TimelineCompiler.compile(entry.getValue()));
            count++;
        }
        return count;
    }

    private static int compileDimensions(Path root) throws IOException {
        int count = 0;
        Map<String, JsonObject> definitions =
                DAI_EarlyJsonRepository.scan("dai_dimensions", "dimensions");

        for (Map.Entry<String, JsonObject> entry : definitions.entrySet()) {
            String id = normalizeId(entry.getKey());
            IdParts parts = split(id);
            if (parts == null) continue;

            JsonElement generation = entry.getValue().get("generation");
            if (generation == null) generation = entry.getValue().get("world_type");
            if (generation == null || generation.isJsonNull()) continue;

            DAI_WorldTypeDefinition type = DAI_WorldTypeDefinition.parse(generation);
            Path target = root.resolve("data")
                    .resolve(parts.namespace())
                    .resolve("dimension")
                    .resolve(parts.path() + ".json");
            writeJson(target, type.dimensionJson());
            count++;
        }
        return count;
    }

    private static int compileWorldPresets(Path root) throws IOException {
        int count = 0;
        JsonArray normalPresetIds = new JsonArray();
        Map<String, JsonObject> definitions =
                DAI_EarlyJsonRepository.scan(DAI_WorldgenRepository.DIRECTORY, "worldgen");

        for (Map.Entry<String, JsonObject> entry : definitions.entrySet()) {
            String id = normalizeId(entry.getKey());
            IdParts parts = split(id);
            if (parts == null) continue;

            JsonElement worldTypeElement = entry.getValue().get("world_type");
            if (worldTypeElement == null || worldTypeElement.isJsonNull()) continue;

            DAI_WorldTypeDefinition type = DAI_WorldTypeDefinition.parse(worldTypeElement);
            if (!type.requiresGeneratedPreset()) continue;

            Path target = root.resolve("data")
                    .resolve(parts.namespace())
                    .resolve("worldgen")
                    .resolve("world_preset")
                    .resolve(parts.path() + ".json");
            writeJson(target, type.worldPresetJson());
            normalPresetIds.add(id);
            count++;
        }

        if (normalPresetIds.size() > 0) {
            JsonObject tag = new JsonObject();
            tag.addProperty("replace", false);
            tag.add("values", normalPresetIds);
            writeJson(
                    root.resolve("data")
                            .resolve("minecraft")
                            .resolve("tags")
                            .resolve("worldgen")
                            .resolve("world_preset")
                            .resolve("normal.json"),
                    tag
            );
        }
        return count;
    }

    private static void addPackFinder(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;

        Path root = root();
        if (!Files.isDirectory(root.resolve("data"))) return;

        PackLocationInfo location = new PackLocationInfo(
                PACK_ID,
                Component.literal("D.A.I. Generated World Data"),
                PackSource.BUILT_IN,
                Optional.empty()
        );

        Pack.ResourcesSupplier resources =
                new PathPackResources.PathResourcesSupplier(root);

        Pack.Metadata metadata = new Pack.Metadata(
                Component.literal("Generated Mojang registry/world data compiled from D.A.I. JSON"),
                PackCompatibility.COMPATIBLE,
                FeatureFlagSet.of(),
                List.of(),
                true
        );

        PackSelectionConfig selection = new PackSelectionConfig(
                true,
                Pack.Position.BOTTOM,
                true
        );

        Pack pack = new Pack(location, resources, metadata, selection);
        event.addRepositorySource(output -> output.accept(pack));

        DAI_Core.LOGGER.info(
                "<DAI>: Added required generated server-data pack '{}'.",
                PACK_ID
        );
    }

    private static void writeJson(Path target, JsonObject json) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, GSON.toJson(json), StandardCharsets.UTF_8);
    }

    private static void clearDirectory(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(root))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException io) throw io;
            throw exception;
        }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static IdParts split(String id) {
        if (id == null) return null;
        int colon = id.indexOf(':');
        if (colon <= 0 || colon >= id.length() - 1) return null;
        return new IdParts(id.substring(0, colon), id.substring(colon + 1));
    }

    private record IdParts(String namespace, String path) {}
}
