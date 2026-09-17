package io.github.j12h36h.dai.gamerules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.flag.FeatureFlags;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DAI-owned offline game-rule editor state for one save.
 *
 * <p>Minecraft 26.1 moved game rules out of level.dat into the SavedData file
 * {@code data/minecraft/game_rules.dat}. DAI deliberately does not rewrite that
 * binary SavedData while the world is closed. Instead, Modify World stores only
 * explicit user overrides in {@code dai/game_rules.json}. The next integrated
 * server start applies those values through Minecraft's public GameRules API,
 * allowing vanilla to own validation, callbacks, networking and native save
 * persistence.</p>
 *
 * <p>The catalog itself is built from {@link GameRules#availableRules()}, so
 * vanilla defaults and rules registered by other mods automatically appear in
 * the DAI menu without maintaining a second hard-coded rule list.</p>
 */
public final class DAI_WorldGameRuleOverrides {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "game_rules.json";

    private DAI_WorldGameRuleOverrides() {}

    public static List<RuleEntry> snapshot(Path saveRoot) {
        Map<String, String> overrides = read(saveRoot);
        List<RuleEntry> result = new ArrayList<>();

        for (GameRule<?> rule : ruleCatalog().availableRules().toList()) {
            if (rule == null) continue;
            String id = id(rule);
            if (id.isBlank()) continue;

            String defaultValue = serializeDefault(rule);
            String value = overrides.getOrDefault(id, defaultValue);
            String category = "misc";
            try {
                if (rule.category() != null && rule.category().id() != null) {
                    category = rule.category().id().getPath();
                }
            } catch (RuntimeException ignored) {}

            ValueType type = rule.valueClass() == Boolean.class
                    ? ValueType.BOOLEAN
                    : rule.valueClass() == Integer.class
                    ? ValueType.INTEGER
                    : ValueType.STRING;

            result.add(new RuleEntry(
                    id,
                    rule.getDescriptionId(),
                    normalizeCategory(category),
                    value,
                    defaultValue,
                    type,
                    overrides.containsKey(id)
            ));
        }

        result.sort(Comparator
                .comparing(RuleEntry::category, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RuleEntry::id, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public static boolean set(Path saveRoot, String ruleId, String rawValue) {
        GameRule<?> rule = findRule(ruleId);
        if (saveRoot == null || rule == null) return false;
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isBlank() || !valid(rule, value)) return false;

        Map<String, String> values = new LinkedHashMap<>(read(saveRoot));
        values.put(id(rule), value);
        return write(saveRoot, values);
    }

    public static boolean reset(Path saveRoot, String ruleId) {
        if (saveRoot == null || ruleId == null || ruleId.isBlank()) return false;
        Map<String, String> values = new LinkedHashMap<>(read(saveRoot));
        String canonical = canonicalId(ruleId);
        if (values.remove(canonical) == null) return true;
        return write(saveRoot, values);
    }

    public static int applyToServer(MinecraftServer server) {
        if (server == null) return 0;
        Path root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Map<String, String> values = read(root);
        if (values.isEmpty()) return 0;

        GameRules gameRules = server.getGameRules();
        int applied = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            GameRule<?> rule = findRule(entry.getKey());
            if (rule == null) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: World game-rule override '{}' is not registered; leaving it dormant.",
                        entry.getKey()
                );
                continue;
            }
            if (apply(gameRules, rule, entry.getValue(), server)) applied++;
        }

        if (applied > 0) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Applied {} DAI world game-rule override(s) for '{}'.",
                    applied,
                    root
            );
        }
        return applied;
    }

    public static Map<String, String> overrides(Path saveRoot) {
        return Map.copyOf(read(saveRoot));
    }

    private static Map<String, String> read(Path saveRoot) {
        if (saveRoot == null) return Map.of();
        Path path = path(saveRoot);
        if (!Files.isRegularFile(path)) return Map.of();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject rules = root.has("rules") && root.get("rules").isJsonObject()
                    ? root.getAsJsonObject("rules")
                    : new JsonObject();
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            for (String key : rules.keySet()) {
                try {
                    String value = rules.get(key).getAsString();
                    if (value != null && !value.isBlank()) result.put(canonicalId(key), value.trim());
                } catch (RuntimeException ignored) {}
            }
            return result;
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not read DAI world game-rule overrides from '{}'.", path, exception);
            return Map.of();
        }
    }

    private static boolean write(Path saveRoot, Map<String, String> values) {
        Path path = path(saveRoot);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            JsonObject root = new JsonObject();
            root.addProperty("format", 1);
            JsonObject rules = new JsonObject();
            values.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> rules.addProperty(canonicalId(entry.getKey()), entry.getValue()));
            root.add("rules", rules);

            Files.createDirectories(path.getParent());
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicUnavailable) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception exception) {
            try { Files.deleteIfExists(temporary); } catch (Exception ignored) {}
            DAI_Core.LOGGER.error("<DAI>: Could not save DAI world game-rule overrides to '{}'.", path, exception);
            return false;
        }
    }

    private static Path path(Path saveRoot) {
        return saveRoot.resolve("dai").resolve(FILE_NAME);
    }

    /**
     * Minecraft 26.2 exposes availableRules() on a GameRules instance rather
     * than as a static registry accessor. Build a default catalog with every
     * currently registered feature flag enabled so Modify World can always
     * show the complete vanilla/modded rule set, including rules whose values
     * have never been explicitly saved by this world.
     */
    private static GameRules ruleCatalog() {
        return new GameRules(FeatureFlags.REGISTRY.allFlags());
    }

    private static GameRule<?> findRule(String rawId) {
        String wanted = canonicalId(rawId);
        for (GameRule<?> rule : ruleCatalog().availableRules().toList()) {
            if (rule != null && canonicalId(id(rule)).equals(wanted)) return rule;
        }
        return null;
    }

    private static String id(GameRule<?> rule) {
        try {
            if (rule.getIdentifier() != null) return canonicalId(rule.getIdentifier().toString());
        } catch (RuntimeException ignored) {}
        return canonicalId(rule.id());
    }

    private static String canonicalId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) return "";
        return value.contains(":") ? value : "minecraft:" + value;
    }

    private static String normalizeCategory(String raw) {
        String value = raw == null ? "misc" : raw.trim().toLowerCase(Locale.ROOT);
        return value.isBlank() ? "misc" : value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String serializeDefault(GameRule<?> rule) {
        try {
            GameRule raw = rule;
            return raw.serialize(raw.defaultValue());
        } catch (RuntimeException exception) {
            return String.valueOf(rule.defaultValue());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean valid(GameRule<?> rule, String value) {
        try {
            DataResult parsed = ((GameRule) rule).deserialize(value);
            return parsed != null && parsed.result().isPresent();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean apply(GameRules rules, GameRule<?> rule, String rawValue, MinecraftServer server) {
        try {
            GameRule rawRule = rule;
            DataResult parsed = rawRule.deserialize(rawValue);
            Object value = parsed == null ? null : parsed.result().orElse(null);
            if (value == null) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Invalid saved game-rule value '{}' for '{}'; keeping Minecraft's current value.",
                        rawValue,
                        id(rule)
                );
                return false;
            }
            rules.set(rawRule, value, server);
            return true;
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not apply world game-rule override '{}'='{}'.",
                    id(rule), rawValue, exception
            );
            return false;
        }
    }

    public enum ValueType {
        BOOLEAN,
        INTEGER,
        STRING
    }

    public record RuleEntry(
            String id,
            String descriptionId,
            String category,
            String value,
            String defaultValue,
            ValueType type,
            boolean overridden
    ) {}
}
