package io.github.j12h36h.dai.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
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
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Materializes tiny client-resource aliases for registry-backed content and
 * exposes them as a required, hidden resource pack.
 *
 * The generated files never contain custom textures. They simply map DAI's
 * real registry ids to existing vanilla/modded model resources.
 */
public final class DAI_GeneratedAssetsPack {

    private static final String PACK_ID = DAI_Core.MODID + ":generated_registry_assets";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DAI_GeneratedAssetsPack() {}

    public static void initialize(IEventBus modBus) {
        modBus.addListener(DAI_GeneratedAssetsPack::addPackFinder);
    }

    public static Path root() {
        return DAI_RegistryCache.path()
                .getParent()
                .resolve("generated_assets");
    }

    public static void rebuild(Collection<DAI_RegistrySpec> specs) {
        Path root = root();
        try {
            clearDirectory(root);
            Files.createDirectories(root.resolve("assets"));

            int items = 0;
            int blocks = 0;
            if (specs != null) {
                for (DAI_RegistrySpec spec : specs) {
                    if (spec == null || spec.identifier() == null) continue;
                    writeClientItem(spec, root);
                    items++;
                    if (spec.nativeRegistry() == DAI_RegistrySpec.NativeRegistry.BLOCK) {
                        writeBlockState(spec, root);
                        blocks++;
                    }
                }
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Prepared generated client aliases for {} item id(s) and {} block id(s).",
                    items,
                    blocks
            );
        } catch (Exception exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Failed to prepare generated registry client assets in '{}'.",
                    root,
                    exception
            );
        }
    }

    private static void addPackFinder(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        Path root = root();
        if (!Files.isDirectory(root.resolve("assets"))) return;

        PackLocationInfo location = new PackLocationInfo(
                PACK_ID,
                Component.literal("D.A.I. Generated Registry Assets"),
                PackSource.BUILT_IN,
                Optional.empty()
        );

        Pack.ResourcesSupplier resources =
                new PathPackResources.PathResourcesSupplier(root);

        Pack.Metadata metadata = new Pack.Metadata(
                Component.literal("Generated vanilla/model aliases for D.A.I. registry-backed content"),
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
                "<DAI>: Added required generated registry asset pack '{}'.",
                PACK_ID
        );
    }

    private static void writeClientItem(DAI_RegistrySpec spec, Path root) throws IOException {
        IdParts id = split(spec.id());
        if (id == null) return;

        String visual = firstNonBlank(spec.model(), spec.carrier());
        if (visual.isBlank()) return;

        String modelLocation = asModelLocation(visual, "item");

        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", modelLocation);

        JsonObject clientItem = new JsonObject();
        clientItem.add("model", model);

        Path target = root.resolve("assets")
                .resolve(id.namespace)
                .resolve("items")
                .resolve(id.path + ".json");
        writeJson(target, clientItem);
    }

    private static void writeBlockState(DAI_RegistrySpec spec, Path root) throws IOException {
        IdParts id = split(spec.id());
        if (id == null) return;

        String visual = firstNonBlank(spec.model(), spec.carrier());
        if (visual.isBlank()) return;

        String modelLocation = asModelLocation(visual, "block");

        JsonObject variant = new JsonObject();
        variant.addProperty("model", modelLocation);

        JsonObject variants = new JsonObject();
        variants.add("", variant);

        JsonObject state = new JsonObject();
        state.add("variants", variants);

        Path target = root.resolve("assets")
                .resolve(id.namespace)
                .resolve("blockstates")
                .resolve(id.path + ".json");
        writeJson(target, state);
    }

    private static String asModelLocation(String raw, String folder) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        String namespace = colon >= 0 ? value.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? value.substring(colon + 1) : value;

        if (!path.startsWith("item/") && !path.startsWith("block/")) {
            path = folder + "/" + path;
        } else if ("block".equals(folder) && path.startsWith("item/")) {
            path = "block/" + path.substring("item/".length());
        } else if ("item".equals(folder) && path.startsWith("block/")) {
            path = "item/" + path.substring("block/".length());
        }

        return namespace + ":" + path;
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

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second == null ? "" : second;
    }

    private static IdParts split(String id) {
        if (id == null) return null;
        int colon = id.indexOf(':');
        if (colon <= 0 || colon >= id.length() - 1) return null;
        return new IdParts(id.substring(0, colon), id.substring(colon + 1));
    }

    private record IdParts(String namespace, String path) {}
}
