package io.github.j12h36h.dai.client.story;

import io.github.j12h36h.dai.util.DAI_Reflect;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Generic read-only gameplay snapshot used by pack-authored story profiles.
 * Field names are API data; no event significance or narrative is assigned here.
 */
public final class DAI_StoryProbe {
    private DAI_StoryProbe() {}

    public static Snapshot capture(Minecraft minecraft) {
        if (minecraft == null) return null;
        Object player = DAI_Reflect.field(minecraft, "player");
        Object level = DAI_Reflect.field(minecraft, "level");
        if (player == null || level == null) return null;

        String uuid = String.valueOf(DAI_Reflect.invoke(player, "getUUID"));
        if (uuid.isBlank() || uuid.equals("null")) uuid = UUID.nameUUIDFromBytes(String.valueOf(player).getBytes()).toString();
        String context = contextId(minecraft, uuid);

        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("player.uuid", uuid);
        fields.put("player.name", DAI_Reflect.text(DAI_Reflect.invoke(player, "getName"), ""));
        fields.put("player.x", DAI_Reflect.number(DAI_Reflect.invoke(player, "getX"), 0.0D));
        fields.put("player.y", DAI_Reflect.number(DAI_Reflect.invoke(player, "getY"), 0.0D));
        fields.put("player.z", DAI_Reflect.number(DAI_Reflect.invoke(player, "getZ"), 0.0D));
        double health = DAI_Reflect.number(DAI_Reflect.invoke(player, "getHealth"), 20.0D);
        double maxHealth = DAI_Reflect.number(DAI_Reflect.invoke(player, "getMaxHealth"), Math.max(20.0D, health));
        boolean dead = DAI_Reflect.bool(DAI_Reflect.invoke(player, "isDeadOrDying"), health <= 0.0D) || health <= 0.0D;
        fields.put("player.health", health);
        fields.put("player.max_health", maxHealth);
        fields.put("player.health_ratio", maxHealth <= 0.0D ? 0.0D : health / maxHealth);
        fields.put("player.dead", dead);
        fields.put("player.alive", !dead);
        fields.put("player.main_hand", itemId(DAI_Reflect.invoke(player, "getMainHandItem")));
        fields.put("player.inventory", inventoryIds(player));
        fields.put("player.on_fire", DAI_Reflect.bool(DAI_Reflect.invoke(player, "isOnFire"), false));
        fields.put("player.in_water", DAI_Reflect.bool(DAI_Reflect.invoke(player, "isInWater"), false));
        fields.put("player.sprinting", DAI_Reflect.bool(DAI_Reflect.invoke(player, "isSprinting"), false));
        fields.put("player.crouching", DAI_Reflect.bool(DAI_Reflect.invoke(player, "isCrouching"), false));
        fields.put("player.air", DAI_Reflect.number(DAI_Reflect.invoke(player, "getAirSupply"), 0.0D));
        fields.put("player.experience_level", DAI_Reflect.number(DAI_Reflect.field(player, "experienceLevel"), 0.0D));

        Object blockPos = DAI_Reflect.invoke(player, "blockPosition");
        fields.put("world.biome", biomeId(level, blockPos));
        fields.put("world.dimension", id(DAI_Reflect.invoke(level, "dimension"), ""));
        boolean thunder = DAI_Reflect.bool(DAI_Reflect.invoke(level, "isThundering"), false);
        boolean rain = DAI_Reflect.bool(DAI_Reflect.invoke(level, "isRaining"), false);
        long dayTime = DAI_Reflect.longNumber(DAI_Reflect.invoke(level, "getDayTime"), 0L);
        fields.put("world.thundering", thunder);
        fields.put("world.raining", rain);
        fields.put("world.weather", thunder ? "thunder" : rain ? "rain" : "clear");
        fields.put("world.day_time", dayTime);
        fields.put("world.time_of_day", timeOfDay(dayTime));
        fields.put("world.context", context);
        fields.put("runtime.epoch_ms", System.currentTimeMillis());
        fields.put("runtime.game_tick", DAI_Reflect.longNumber(DAI_Reflect.invoke(level, "getGameTime"), 0L));

        if (dead) {
            Object tracker = DAI_Reflect.invoke(player, "getCombatTracker");
            fields.put("player.death_message", DAI_Reflect.text(DAI_Reflect.invoke(tracker, "getDeathMessage"), ""));
        } else fields.put("player.death_message", "");

        return new Snapshot(uuid, context, Map.copyOf(fields));
    }

    private static String biomeId(Object level, Object blockPos) {
        Object holder = DAI_Reflect.invoke(level, "getBiome", blockPos);
        if (holder == null) return "";
        Object key = DAI_Reflect.optionalValue(DAI_Reflect.invoke(holder, "unwrapKey"));
        if (key == null) key = DAI_Reflect.optionalValue(DAI_Reflect.invoke(holder, "getKey"));
        return id(key, "");
    }

    private static String id(Object value, String fallback) {
        if (value == null) return fallback;
        Object identifier = DAI_Reflect.invoke(value, "identifier");
        if (identifier == null) identifier = DAI_Reflect.invoke(value, "location");
        if (identifier == null) identifier = DAI_Reflect.invoke(value, "getLocation");
        String text = String.valueOf(identifier == null ? value : identifier);
        return text == null || text.isBlank() || text.equals("null") ? fallback : text;
    }

    private static String itemId(Object stack) {
        if (stack == null || DAI_Reflect.bool(DAI_Reflect.invoke(stack, "isEmpty"), false)) return "minecraft:air";
        Object item = DAI_Reflect.invoke(stack, "getItem");
        Object registry = DAI_Reflect.staticField("net.minecraft.core.registries.BuiltInRegistries", "ITEM");
        Object key = DAI_Reflect.invoke(registry, "getKey", item);
        String text = String.valueOf(key);
        return text == null || text.isBlank() || text.equals("null") ? "minecraft:air" : text;
    }

    private static Set<String> inventoryIds(Object player) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Object inventory = DAI_Reflect.invoke(player, "getInventory");
        int size = (int)DAI_Reflect.number(DAI_Reflect.invoke(inventory, "getContainerSize"), 46.0D);
        for (int i = 0; i < Math.min(128, Math.max(0, size)); i++) {
            String item = itemId(DAI_Reflect.invoke(inventory, "getItem", i));
            if (!item.equals("minecraft:air")) out.add(item);
        }
        return Set.copyOf(out);
    }

    private static String contextId(Minecraft minecraft, String uuid) {
        Object currentServer = DAI_Reflect.invoke(minecraft, "getCurrentServer");
        if (currentServer == null) currentServer = DAI_Reflect.field(minecraft, "currentServer", "currentServerData");
        if (currentServer != null) {
            Object ip = DAI_Reflect.field(currentServer, "ip", "address");
            Object name = DAI_Reflect.field(currentServer, "name");
            String marker = text(ip) + "|" + text(name);
            if (!marker.equals("|")) return "remote:" + clean(marker);
        }
        Object server = DAI_Reflect.invoke(minecraft, "getSingleplayerServer");
        Object worldData = DAI_Reflect.invoke(server, "getWorldData");
        String levelName = DAI_Reflect.text(DAI_Reflect.invoke(worldData, "getLevelName"), "");
        if (!levelName.isBlank()) return "singleplayer:" + clean(levelName);
        return "local:" + clean(uuid);
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static String clean(String value) {
        String s = value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9._:@|\\-]+", "_");
        return s.length() > 120 ? s.substring(0, 120) : s;
    }
    private static String timeOfDay(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        if (t < 1000L) return "dawn";
        if (t < 11000L) return "day";
        if (t < 13500L) return "dusk";
        if (t < 22500L) return "night";
        return "dawn";
    }

    public record Snapshot(String uuid, String context, Map<String, Object> fields) {}
}
