package io.github.j12h36h.dai.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global, startup-readable mirror of registry objects discovered in worlds.
 *
 * Native registries are populated before a world is selected, so the world
 * manifest cannot be the only copy. This cache is append-only by registry id:
 * removed datapack definitions stay registered as tombstones to keep old save
 * data readable.
 */
public final class DAI_RegistryCache {

    private static final int FORMAT = 8;

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private DAI_RegistryCache() {}

    public static Path path() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(DAI_Core.MODID)
                .resolve("registry_cache")
                .resolve("registry_entries.json");
    }

    public static Map<String, DAI_RegistrySpec> load() {
        LinkedHashMap<String, DAI_RegistrySpec> result = new LinkedHashMap<>();
        Path path = path();

        if (!Files.isRegularFile(path)) {
            return result;
        }

        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return result;

            JsonArray entries = parsed.getAsJsonObject().getAsJsonArray("entries");
            if (entries == null) return result;

            for (JsonElement element : entries) {
                DAI_RegistrySpec spec = readSpec(element);
                if (spec != null) result.put(spec.key(), spec);
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Failed to read registry startup cache '{}'.",
                    path,
                    exception
            );
        }

        return result;
    }

    /**
     * Adds or refreshes active definitions while retaining removed ids.
     */
    public static void merge(Collection<DAI_RegistrySpec> specs) {
        Map<String, DAI_RegistrySpec> merged = load();
        if (specs != null) {
            for (DAI_RegistrySpec spec : specs) {
                if (spec == null) continue;

                // A Minecraft registry id can only have one DAI native shape at
                // startup. Replacing an active spec with the same id removes a
                // stale item-vs-block variant while retaining unrelated
                // tombstones from worlds that are not currently loaded.
                merged.entrySet().removeIf(entry ->
                        entry.getValue() != null
                                && entry.getValue().id().equals(spec.id())
                );
                merged.put(spec.key(), spec);
            }
        }
        write(merged.values());
    }

    private static void write(Collection<DAI_RegistrySpec> specs) {
        Path target = path();
        Path parent = target.getParent();

        try {
            Files.createDirectories(parent);

            JsonObject root = new JsonObject();
            root.addProperty("format", FORMAT);
            root.addProperty(
                    "note",
                    "DAI startup registry cache. Entries are retained as tombstones for save compatibility."
            );

            JsonArray entries = new JsonArray();
            if (specs != null) {
                for (DAI_RegistrySpec spec : specs) {
                    if (spec != null) entries.add(writeSpec(spec));
                }
            }
            root.add("entries", entries);

            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (IOException atomicFailure) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Failed to write registry startup cache '{}'.",
                    target,
                    exception
            );
        }
    }

    public static JsonObject writeSpec(DAI_RegistrySpec spec) {
        JsonObject object = new JsonObject();
        object.addProperty("id", spec.id());
        object.addProperty("registry", spec.nativeRegistry().name().toLowerCase());
        object.addProperty("content_kind", spec.contentKind());
        object.addProperty("display_name", spec.displayName());
        object.addProperty("model", spec.model());
        object.addProperty("carrier", spec.carrier());
        object.addProperty("stack_size", spec.stackSize());
        object.addProperty("durability", spec.durability());

        JsonObject block = new JsonObject();
        block.addProperty("hardness", spec.block().hardness());
        block.addProperty("explosion_resistance", spec.block().explosionResistance());
        block.addProperty("sound", spec.block().sound());
        block.addProperty("luminance", spec.block().luminance());
        block.addProperty("friction", spec.block().friction());
        block.addProperty("speed_factor", spec.block().speedFactor());
        block.addProperty("jump_factor", spec.block().jumpFactor());
        block.addProperty("requires_correct_tool", spec.block().requiresCorrectTool());
        block.addProperty("no_occlusion", spec.block().noOcclusion());
        block.addProperty("no_collision", spec.block().noCollision());
        block.addProperty("replaceable", spec.block().replaceable());
        block.addProperty("random_ticks", spec.block().randomTicks());
        block.addProperty("ignited_by_lava", spec.block().ignitedByLava());
        block.addProperty("emissive_rendering", spec.block().emissiveRendering());
        block.addProperty("map_color", spec.block().mapColor());
        block.addProperty("push_reaction", spec.block().pushReaction());
        com.google.gson.JsonArray states = new com.google.gson.JsonArray();
        spec.block().states().forEach(states::add);
        block.add("states", states);
        com.google.gson.JsonArray outline = new com.google.gson.JsonArray();
        spec.block().outlineShape().forEach(outline::add);
        block.add("outline_shape", outline);
        com.google.gson.JsonArray collision = new com.google.gson.JsonArray();
        spec.block().collisionShape().forEach(collision::add);
        block.add("collision_shape", collision);
        block.addProperty("redstone_signal", spec.block().redstoneSignal());
        block.addProperty("climbable", spec.block().climbable());
        block.addProperty("redstone_state", spec.block().redstoneState());
        block.addProperty("use_toggle_state", spec.block().useToggleState());
        block.addProperty("scheduled_tick_delay", spec.block().scheduledTickDelay());
        object.add("block", block);

        JsonObject effect = new JsonObject();
        effect.addProperty("category", spec.effect().category());
        effect.addProperty("color", spec.effect().color());
        effect.addProperty("tick_interval", spec.effect().tickInterval());
        object.add("effect", effect);
        JsonObject potion = new JsonObject();
        JsonArray potionEffects = new JsonArray();
        spec.potion().effects().forEach(potionEffects::add);
        potion.add("effects", potionEffects);
        object.add("potion", potion);
        JsonObject particle = new JsonObject();
        particle.addProperty("texture", spec.particle().texture());
        object.add("particle", particle);

        object.addProperty("entity_category", spec.entityCategory());
        object.addProperty("entity_width", spec.entityWidth());
        object.addProperty("entity_height", spec.entityHeight());
        object.addProperty("entity_tracking_range", spec.entityTrackingRange());
        object.addProperty("entity_update_interval", spec.entityUpdateInterval());
        object.addProperty("entity_fire_immune", spec.entityFireImmune());
        object.addProperty("entity_summonable", spec.entitySummonable());
        object.addProperty("entity_saveable", spec.entitySaveable());

        JsonObject attributes = new JsonObject();
        spec.nativeAttributes().forEach(attributes::addProperty);
        object.add("native_attributes", attributes);

        JsonObject components = new JsonObject();
        spec.nativeComponents().forEach((id, json) -> {
            try {
                components.add(id, JsonParser.parseString(json));
            } catch (Exception ignored) {
                // Cache only validated/parseable component entries.
            }
        });
        object.add("native_components", components);
        return object;
    }

    public static DAI_RegistrySpec readSpec(JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;

        try {
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "id");
            String registry = string(object, "registry");
            String contentKind = string(object, "content_kind");
            String displayName = string(object, "display_name");
            String model = string(object, "model");
            String carrier = string(object, "carrier");
            int stackSize = integer(object, "stack_size", 1);
            int durability = integer(object, "durability", 0);
            io.github.j12h36h.dai.content.DAI_BlockSettings block = readBlockSettings(object);
            io.github.j12h36h.dai.content.DAI_EffectSettings effect = readEffectSettings(object);
            io.github.j12h36h.dai.content.DAI_PotionSettings potion = readPotionSettings(object);
            io.github.j12h36h.dai.content.DAI_ParticleSettings particle = readParticleSettings(object);
            String entityCategory = string(object, "entity_category");
            float entityWidth = decimal(object, "entity_width", 0.6F);
            float entityHeight = decimal(object, "entity_height", 1.0F);
            int entityTrackingRange = integer(object, "entity_tracking_range", 8);
            int entityUpdateInterval = integer(object, "entity_update_interval", 3);
            boolean entityFireImmune = bool(object, "entity_fire_immune", false);
            boolean entitySummonable = bool(object, "entity_summonable", true);
            boolean entitySaveable = bool(object, "entity_saveable", true);
            java.util.LinkedHashMap<String, Double> nativeAttributes = new java.util.LinkedHashMap<>();
            JsonObject attrs = object.getAsJsonObject("native_attributes");
            if (attrs != null) {
                for (var attr : attrs.entrySet()) {
                    try { nativeAttributes.put(attr.getKey(), attr.getValue().getAsDouble()); } catch (Exception ignored) {}
                }
            }

            java.util.LinkedHashMap<String, String> nativeComponents = new java.util.LinkedHashMap<>();
            JsonObject components = object.getAsJsonObject("native_components");
            if (components != null) {
                for (var component : components.entrySet()) {
                    try { nativeComponents.put(component.getKey(), component.getValue().toString()); } catch (Exception ignored) {}
                }
            }

            DAI_RegistrySpec.NativeRegistry nativeRegistry = switch (registry) {
                case "item" -> DAI_RegistrySpec.NativeRegistry.ITEM;
                case "block" -> DAI_RegistrySpec.NativeRegistry.BLOCK;
                case "entity" -> DAI_RegistrySpec.NativeRegistry.ENTITY;
                case "effect" -> DAI_RegistrySpec.NativeRegistry.EFFECT;
                case "potion" -> DAI_RegistrySpec.NativeRegistry.POTION;
                case "particle" -> DAI_RegistrySpec.NativeRegistry.PARTICLE;
                default -> null;
            };

            if (id.isBlank() || nativeRegistry == null) return null;

            return new DAI_RegistrySpec(
                    id,
                    nativeRegistry,
                    contentKind,
                    displayName,
                    model,
                    carrier,
                    stackSize,
                    durability,
                    block,
                    effect,
                    potion,
                    particle,
                    entityCategory,
                    entityWidth,
                    entityHeight,
                    entityTrackingRange,
                    entityUpdateInterval,
                    entityFireImmune,
                    entitySummonable,
                    entitySaveable,
                    nativeAttributes,
                    nativeComponents
            );
        } catch (Exception ignored) {
            return null;
        }
    }



    private static io.github.j12h36h.dai.content.DAI_ParticleSettings readParticleSettings(JsonObject object) {
        JsonObject p = object.getAsJsonObject("particle");
        if (p == null) return io.github.j12h36h.dai.content.DAI_ParticleSettings.DEFAULT;
        var d = io.github.j12h36h.dai.content.DAI_ParticleSettings.DEFAULT;
        return new io.github.j12h36h.dai.content.DAI_ParticleSettings(
                d.shape(), d.count(), d.spreadX(), d.spreadY(), d.spreadZ(), d.speed(), d.radius(), d.force(),
                stringOr(p, "texture", d.texture()), d.lifetime(), d.gravity(), d.friction(), d.scale(),
                d.color(), d.alpha(), d.fullBright(), d.collision());
    }

    private static io.github.j12h36h.dai.content.DAI_EffectSettings readEffectSettings(JsonObject object) {
        JsonObject e = object.getAsJsonObject("effect");
        if (e == null) return io.github.j12h36h.dai.content.DAI_EffectSettings.DEFAULT;
        return new io.github.j12h36h.dai.content.DAI_EffectSettings(
                stringOr(e, "category", "neutral"), integer(e, "color", 0xFFFFFF), integer(e, "tick_interval", 1));
    }

    private static io.github.j12h36h.dai.content.DAI_PotionSettings readPotionSettings(JsonObject object) {
        JsonObject p = object.getAsJsonObject("potion");
        if (p == null) return io.github.j12h36h.dai.content.DAI_PotionSettings.DEFAULT;
        return new io.github.j12h36h.dai.content.DAI_PotionSettings(stringList(p, "effects"));
    }

    private static io.github.j12h36h.dai.content.DAI_BlockSettings readBlockSettings(JsonObject object) {
        JsonObject block = object.getAsJsonObject("block");
        if (block == null) return io.github.j12h36h.dai.content.DAI_BlockSettings.DEFAULT;

        return new io.github.j12h36h.dai.content.DAI_BlockSettings(
                decimal(block, "hardness", 1.5F),
                decimal(block, "explosion_resistance", 6.0F),
                stringOr(block, "sound", "stone"),
                integer(block, "luminance", 0),
                decimal(block, "friction", 0.6F),
                decimal(block, "speed_factor", 1.0F),
                decimal(block, "jump_factor", 1.0F),
                bool(block, "requires_correct_tool", false),
                bool(block, "no_occlusion", false),
                bool(block, "no_collision", false),
                bool(block, "replaceable", false),
                bool(block, "random_ticks", false),
                bool(block, "ignited_by_lava", false),
                bool(block, "emissive_rendering", false),
                string(block, "map_color"),
                stringOr(block, "push_reaction", "normal"),
                stringList(block, "states"),
                doubleList(block, "outline_shape"),
                doubleList(block, "collision_shape"),
                integer(block, "redstone_signal", 0),
                bool(block, "climbable", false),
                string(block, "redstone_state"),
                string(block, "use_toggle_state"),
                integer(block, "scheduled_tick_delay", 0)
        );
    }

    private static java.util.List<String> stringList(JsonObject object, String key) {
        JsonArray array = object.getAsJsonArray(key);
        if (array == null) return java.util.List.of();
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (JsonElement element : array) {
            try { out.add(element.getAsString()); } catch (Exception ignored) {}
        }
        return java.util.List.copyOf(out);
    }

    private static java.util.List<Double> doubleList(JsonObject object, String key) {
        JsonArray array = object.getAsJsonArray(key);
        if (array == null) return java.util.List.of();
        java.util.ArrayList<Double> out = new java.util.ArrayList<>();
        for (JsonElement element : array) {
            try { out.add(element.getAsDouble()); } catch (Exception ignored) {}
        }
        return java.util.List.copyOf(out);
    }

    private static String stringOr(JsonObject object, String key, String fallback) {
        String value = string(object, key);
        return value.isBlank() ? fallback : value;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull()
                ? ""
                : element.getAsString();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull()
                ? fallback
                : element.getAsInt();
    }
    private static float decimal(JsonObject object, String key, float fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull()
                ? fallback
                : element.getAsFloat();
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull()
                ? fallback
                : element.getAsBoolean();
    }

}
