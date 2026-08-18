package io.github.j12h36h.dai.server.worldgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generic exploration-time natural generation for DAI structure and feature
 * customization definitions.
 *
 * A pack opts a dai_structures/dai_features definition into this runtime with
 * flags.natural=true. Placement is deterministic from the world seed, rule id,
 * spacing and salt, so a rule has stable candidate chunks without requiring a
 * pack-specific tick function. Successful placements are persisted under the
 * save's dai/ folder and therefore never duplicate when a chunk is revisited.
 *
 * This deliberately complements, rather than replaces, vanilla worldgen. MAIN
 * experiences may still use normal Minecraft presets/structures while DAI adds
 * authored landmarks/features as the player explores.
 */
public final class DAI_NaturalGenerationRuntime {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int PROCESS_INTERVAL_TICKS = 10;
    private static final int SAVE_INTERVAL_TICKS = 200;

    private static final Set<String> GENERATED = new LinkedHashSet<>();
    private static volatile List<Rule> cachedRules = List.of();
    private static volatile boolean rulesDirty = true;
    private static Path stateFile;
    private static boolean stateDirty;
    private static long ticks;

    private DAI_NaturalGenerationRuntime() {}

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener(DAI_NaturalGenerationRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(DAI_NaturalGenerationRuntime::onServerStopping);
        NeoForge.EVENT_BUS.addListener(DAI_NaturalGenerationRuntime::onServerTick);
    }

    /** Called after server datapack/customization reloads. */
    public static void onDefinitionsReloaded() {
        rulesDirty = true;
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ticks = 0L;
        stateDirty = false;
        GENERATED.clear();
        stateFile = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("dai")
                .resolve("natural_generation.json")
                .toAbsolutePath().normalize();
        loadState();
        rulesDirty = true;
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        saveState();
        GENERATED.clear();
        cachedRules = List.of();
        rulesDirty = true;
        stateFile = null;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        ticks++;
        if (ticks % PROCESS_INTERVAL_TICKS != 0L) return;

        if (rulesDirty) rebuildRules();
        if (cachedRules.isEmpty()) return;

        var server = event.getServer();
        if (server == null) return;

        for (var level : server.getAllLevels()) {
            if (level.players().isEmpty()) continue;
            processLevel(server, level, cachedRules);
        }

        if (stateDirty && ticks % SAVE_INTERVAL_TICKS == 0L) saveState();
    }

    private static void rebuildRules() {
        List<Rule> rules = new ArrayList<>();
        collectRules(DAI_GameCustomizationKind.STRUCTURE, rules);
        collectRules(DAI_GameCustomizationKind.FEATURE, rules);
        cachedRules = List.copyOf(rules);
        rulesDirty = false;

        if (!rules.isEmpty()) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Natural generation runtime prepared {} rule(s) from dai_structures/dai_features.",
                    rules.size()
            );
        }
    }

    private static void collectRules(
            DAI_GameCustomizationKind kind,
            List<Rule> output
    ) {
        for (var entry : DAI_GameCustomizationRegistry.entries(kind).values()) {
            DAI_GameCustomizationDefinition definition = entry.definition();
            if (!definition.flag("natural", definition.flag("generate_naturally", false))) continue;

            List<String> carriers = new ArrayList<>();
            String carrier = definition.carrier();
            if (carrier.isBlank()) carrier = definition.property(kind == DAI_GameCustomizationKind.STRUCTURE
                    ? "structure"
                    : "feature");
            if (Identifier.tryParse(carrier) != null) carriers.add(carrier);

            // entries is a deterministic equal-weight pool for natural
            // generation. Repeating an id gives it extra weight without
            // requiring a second pack-specific weighted-table format.
            for (String raw : definition.entries()) {
                if (raw == null || raw.isBlank()) continue;
                String candidate = raw.trim().toLowerCase(Locale.ROOT);
                if (Identifier.tryParse(candidate) != null) carriers.add(candidate);
            }

            if (carriers.isEmpty()) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Natural {} '{}' has no valid carrier/entries resource id; rule skipped.",
                        kind.id(), entry.id()
                );
                continue;
            }

            int defaultSpacing = kind == DAI_GameCustomizationKind.STRUCTURE ? 24 : 6;
            int spacing = clampInt(definition.number("spacing_chunks", defaultSpacing), 1, 1024);
            int separation = clampInt(
                    definition.number("separation_chunks", Math.max(0, spacing / 3)),
                    0,
                    Math.max(0, spacing - 1)
            );
            int generationRadius = clampInt(definition.number("generation_radius_chunks", 2), 0, 8);
            long salt = definition.numbers().containsKey("salt")
                    ? (long) definition.number("salt", 0.0D)
                    : stableSalt(entry.id().toString());

            double frequency = definition.number(
                    "frequency",
                    definition.number("chance", 1.0D)
            );
            if (frequency > 1.0D) frequency /= 100.0D;
            frequency = Math.max(0.0D, Math.min(1.0D, frequency));

            int minY = clampInt(definition.number("min_y", -2048), -4096, 4096);
            int maxY = clampInt(definition.number("max_y", 2048), -4096, 4096);
            if (maxY < minY) maxY = minY;

            output.add(new Rule(
                    kind,
                    entry.id().toString(),
                    List.copyOf(carriers),
                    splitSelectors(definition.property("dimensions"), definition.property("dimension")),
                    biomeSelectors(definition),
                    normalize(definition.property("placement"), "surface"),
                    normalize(definition.property("rotation"), "random"),
                    normalize(definition.property("mirror"), "none"),
                    spacing,
                    separation,
                    generationRadius,
                    salt,
                    frequency,
                    minY,
                    maxY,
                    (int) Math.round(definition.number("y_offset", 0.0D)),
                    (int) Math.round(definition.number("fixed_y", definition.number("y", 64.0D)))
            ));
        }
    }

    private static List<String> biomeSelectors(DAI_GameCustomizationDefinition definition) {
        return splitSelectors(definition.property("biomes"), definition.property("biome"));
    }

    private static void processLevel(
            net.minecraft.server.MinecraftServer server,
            net.minecraft.server.level.ServerLevel level,
            List<Rule> rules
    ) {
        String dimension = level.dimension().identifier().toString();
        Set<String> visitedThisTick = new HashSet<>();

        for (Rule rule : rules) {
            if (!matchesSelector(dimension, rule.dimensions())) continue;

            for (var player : level.players()) {
                if (!player.isAlive() || player.isSpectator()) continue;
                int playerChunkX = player.blockPosition().getX() >> 4;
                int playerChunkZ = player.blockPosition().getZ() >> 4;
                int radius = rule.generationRadiusChunks();

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int chunkX = playerChunkX + dx;
                        int chunkZ = playerChunkZ + dz;
                        Candidate candidate = candidateForChunk(level.getSeed(), rule, chunkX, chunkZ);
                        if (candidate.chunkX() != chunkX || candidate.chunkZ() != chunkZ) continue;

                        String key = dimension + "|" + rule.kind().id() + "|" + rule.id()
                                + "|" + chunkX + "," + chunkZ;
                        if (GENERATED.contains(key) || !visitedThisTick.add(key)) continue;
                        if (!frequencyPasses(level.getSeed(), rule, chunkX, chunkZ)) continue;

                        BlockPos probe = new BlockPos((chunkX << 4) + 8, player.blockPosition().getY(), (chunkZ << 4) + 8);
                        if (!level.hasChunkAt(probe)) continue;

                        BlockPos anchor = resolveAnchor(level, rule, chunkX, chunkZ);
                        if (anchor == null || !matchesBiome(level, anchor, rule.biomes())) continue;

                        if (place(server, level, rule, anchor, candidate.randomBits())) {
                            GENERATED.add(key);
                            stateDirty = true;
                            DAI_Core.LOGGER.info(
                                    "<DAI>: Naturally generated {} '{}' at {} {} {} in {}.",
                                    rule.kind().id(), rule.id(), anchor.getX(), anchor.getY(), anchor.getZ(), dimension
                            );
                        }
                    }
                }
            }
        }
    }

    private static Candidate candidateForChunk(long seed, Rule rule, int chunkX, int chunkZ) {
        int spacing = rule.spacingChunks();
        int cellX = Math.floorDiv(chunkX, spacing);
        int cellZ = Math.floorDiv(chunkZ, spacing);
        long random = mix64(seed ^ rule.salt()
                ^ ((long) cellX * 341873128712L)
                ^ ((long) cellZ * 132897987541L));

        int range = Math.max(1, spacing - rule.separationChunks());
        int offsetX = floorMod((int) random, range);
        int offsetZ = floorMod((int) (random >>> 32), range);
        return new Candidate(
                cellX * spacing + offsetX,
                cellZ * spacing + offsetZ,
                random
        );
    }

    private static boolean frequencyPasses(long seed, Rule rule, int chunkX, int chunkZ) {
        if (rule.frequency() >= 1.0D) return true;
        if (rule.frequency() <= 0.0D) return false;
        long mixed = mix64(seed ^ rule.salt() ^ 0x6A09E667F3BCC909L
                ^ ((long) chunkX * 42317861L)
                ^ ((long) chunkZ * 374761393L));
        double unit = ((mixed >>> 11) * 0x1.0p-53);
        return unit < rule.frequency();
    }

    private static BlockPos resolveAnchor(
            net.minecraft.server.level.ServerLevel level,
            Rule rule,
            int chunkX,
            int chunkZ
    ) {
        long bits = mix64(level.getSeed() ^ rule.salt()
                ^ ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL));
        int x = (chunkX << 4) + 4 + floorMod((int) bits, 8);
        int z = (chunkZ << 4) + 4 + floorMod((int) (bits >>> 32), 8);

        int y;
        switch (rule.placement()) {
            case "ocean_floor", "water_floor" ->
                    y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            case "underground", "cave", "cave_floor" -> {
                BlockPos cave = findCaveFloor(level, x, z, rule, bits);
                if (cave == null) return null;
                return cave.offset(0, rule.yOffset(), 0);
            }
            case "fixed", "fixed_y" -> y = rule.fixedY();
            case "any", "random_y" -> {
                int range = Math.max(1, rule.maxY() - rule.minY() + 1);
                y = rule.minY() + floorMod((int) (bits >>> 16), range);
            }
            default -> y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        }

        y += rule.yOffset();
        if (y < rule.minY() || y > rule.maxY()) return null;
        return new BlockPos(x, y, z);
    }

    private static BlockPos findCaveFloor(
            net.minecraft.server.level.ServerLevel level,
            int x,
            int z,
            Rule rule,
            long bits
    ) {
        int min = Math.max(rule.minY(), level.getMinY() + 1);
        int max = Math.min(rule.maxY(), level.getMaxY() - 2);
        if (max < min) return null;

        int range = max - min + 1;
        int start = min + floorMod((int) (bits >>> 12), range);
        for (int offset = 0; offset < range; offset++) {
            int y = start - offset;
            if (y < min) y = max - (min - y - 1);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir()
                    && !level.getBlockState(pos.below()).isAir()
                    && level.getFluidState(pos).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    private static boolean place(
            net.minecraft.server.MinecraftServer server,
            net.minecraft.server.level.ServerLevel level,
            Rule rule,
            BlockPos pos,
            long randomBits
    ) {
        String dimension = level.dimension().identifier().toString();
        String command;

        String carrier = rule.carriers().get(floorMod((int) (randomBits >>> 16), rule.carriers().size()));

        if (rule.kind() == DAI_GameCustomizationKind.FEATURE) {
            command = "execute in " + dimension + " run place feature " + carrier
                    + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        } else {
            command = "execute in " + dimension + " run place template " + carrier
                    + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    + " " + resolveRotation(rule.rotation(), randomBits)
                    + " " + resolveMirror(rule.mirror(), randomBits);
        }

        return runCommand(server, command);
    }

    private static String resolveRotation(String raw, long bits) {
        String value = normalize(raw, "none");
        if (!value.equals("random")) return switch (value) {
            case "90", "clockwise90", "clockwise_90" -> "clockwise_90";
            case "180", "clockwise180", "clockwise_180" -> "180";
            case "270", "counterclockwise90", "counterclockwise_90" -> "counterclockwise_90";
            default -> "none";
        };

        return switch (floorMod((int) bits, 4)) {
            case 1 -> "clockwise_90";
            case 2 -> "180";
            case 3 -> "counterclockwise_90";
            default -> "none";
        };
    }

    private static String resolveMirror(String raw, long bits) {
        String value = normalize(raw, "none");
        if (value.equals("random")) {
            return switch (floorMod((int) (bits >>> 8), 3)) {
                case 1 -> "left_right";
                case 2 -> "front_back";
                default -> "none";
            };
        }
        return switch (value) {
            case "left_right", "leftright" -> "left_right";
            case "front_back", "frontback" -> "front_back";
            default -> "none";
        };
    }

    private static boolean matchesBiome(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos,
            List<String> selectors
    ) {
        if (selectors == null || selectors.isEmpty()) return true;
        var biome = level.getBiome(pos);
        String id = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("");

        for (String selector : selectors) {
            if (selector == null || selector.isBlank()) continue;
            String value = selector.trim().toLowerCase(Locale.ROOT);
            if (value.startsWith("#")) {
                Identifier tag = Identifier.tryParse(value.substring(1));
                if (tag != null && biome.is(TagKey.create(Registries.BIOME, tag))) return true;
            } else if (id.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesSelector(String value, List<String> selectors) {
        if (selectors == null || selectors.isEmpty()) return true;
        for (String selector : selectors) {
            if (selector != null && value.equals(selector.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static List<String> splitSelectors(String... rawValues) {
        List<String> result = new ArrayList<>();
        if (rawValues == null) return List.of();
        for (String raw : rawValues) {
            if (raw == null || raw.isBlank()) continue;
            for (String part : raw.split("[,;\\s]+")) {
                if (!part.isBlank()) result.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(result);
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static int clampInt(double value, int min, int max) {
        if (!Double.isFinite(value)) return min;
        int rounded = (int) Math.round(value);
        return Math.max(min, Math.min(max, rounded));
    }

    private static int floorMod(int value, int divisor) {
        return Math.floorMod(value, Math.max(1, divisor));
    }

    private static long stableSalt(String value) {
        long hash = 0xcbf29ce484222325L;
        String safe = value == null ? "" : value;
        for (int i = 0; i < safe.length(); i++) {
            hash ^= safe.charAt(i);
            hash *= 0x100000001b3L;
        }
        return mix64(hash);
    }

    private static long mix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static boolean runCommand(net.minecraft.server.MinecraftServer server, String raw) {
        if (server == null || raw == null || raw.isBlank()) return false;
        String command = raw.trim();
        if (command.startsWith("/")) command = command.substring(1);

        try {
            Object commands = server.getCommands();
            Object source = server.createCommandSourceStack();

            for (Method method : commands.getClass().getMethods()) {
                String name = method.getName();
                if (!name.equals("performPrefixedCommand") && !name.equals("performCommand")) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length != 2 || types[1] != String.class) continue;
                if (!types[0].isInstance(source)) continue;
                Object result = method.invoke(commands, source, command);
                return !(result instanceof Number number) || number.intValue() > 0;
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.warn("<DAI>: Natural generation command failed: '{}'.", command, exception);
            return false;
        }

        DAI_Core.LOGGER.warn("<DAI>: No compatible server command executor found for natural generation command '{}'.", command);
        return false;
    }

    private static void loadState() {
        if (stateFile == null || !Files.isRegularFile(stateFile)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(stateFile, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return;
            JsonArray generated = parsed.getAsJsonObject().getAsJsonArray("generated");
            if (generated == null) return;
            for (JsonElement element : generated) {
                if (element != null && element.isJsonPrimitive()) GENERATED.add(element.getAsString());
            }
            DAI_Core.LOGGER.info("<DAI>: Restored {} natural-generation placement marker(s).", GENERATED.size());
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not read natural-generation state '{}'.", stateFile, exception);
        }
    }

    private static void saveState() {
        if (!stateDirty || stateFile == null) return;
        try {
            Files.createDirectories(stateFile.getParent());
            JsonObject root = new JsonObject();
            JsonArray generated = new JsonArray();
            for (String key : GENERATED) generated.add(key);
            root.add("generated", generated);
            Files.writeString(stateFile, GSON.toJson(root), StandardCharsets.UTF_8);
            stateDirty = false;
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not persist natural-generation state '{}'.", stateFile, exception);
        }
    }

    private record Rule(
            DAI_GameCustomizationKind kind,
            String id,
            List<String> carriers,
            List<String> dimensions,
            List<String> biomes,
            String placement,
            String rotation,
            String mirror,
            int spacingChunks,
            int separationChunks,
            int generationRadiusChunks,
            long salt,
            double frequency,
            int minY,
            int maxY,
            int yOffset,
            int fixedY
    ) {}

    private record Candidate(int chunkX, int chunkZ, long randomBits) {}
}
