package io.github.j12h36h.dai.client.comiclife;

import io.github.j12h36h.dai.comiclife.util.Reflect;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class GameProbe {
    private GameProbe() {}

    public static Snapshot capture(Minecraft minecraft) {
        if (minecraft == null) return null;
        Object player = Reflect.field(minecraft, "player");
        Object level = Reflect.field(minecraft, "level");
        if (player == null || level == null) return null;

        String uuid = String.valueOf(Reflect.invoke(player, "getUUID"));
        if (uuid.isBlank() || uuid.equals("null")) uuid = UUID.nameUUIDFromBytes(String.valueOf(player).getBytes()).toString();
        String playerName = Reflect.text(Reflect.invoke(player, "getName"), "Player");
        double x = Reflect.number(Reflect.invoke(player, "getX"), 0.0D);
        double y = Reflect.number(Reflect.invoke(player, "getY"), 0.0D);
        double z = Reflect.number(Reflect.invoke(player, "getZ"), 0.0D);
        float health = (float) Reflect.number(Reflect.invoke(player, "getHealth"), 20.0D);
        float maxHealth = (float) Reflect.number(Reflect.invoke(player, "getMaxHealth"), Math.max(20.0D, health));
        boolean dead = Reflect.bool(Reflect.invoke(player, "isDeadOrDying"), health <= 0.0F) || health <= 0.0F;

        Object blockPos = Reflect.invoke(player, "blockPosition");
        String biome = biomeId(level, blockPos);
        String dimension = id(Reflect.invoke(level, "dimension"), "minecraft:overworld");
        boolean thunder = Reflect.bool(Reflect.invoke(level, "isThundering"), false);
        boolean rain = Reflect.bool(Reflect.invoke(level, "isRaining"), false);
        long dayTime = Reflect.longNumber(Reflect.invoke(level, "getDayTime"), 6000L);
        String weather = thunder ? "thunder" : rain ? "rain" : "clear";
        String timeOfDay = timeOfDay(dayTime);
        String mainHand = itemId(Reflect.invoke(player, "getMainHandItem"));
        Set<String> inventory = inventoryIds(player);
        String context = contextId(minecraft, uuid);
        String deathMessage = dead ? Reflect.text(Reflect.invoke(Reflect.invoke(player, "getCombatTracker"), "getDeathMessage"), "Player died") : "";

        return new Snapshot(uuid, playerName, context, x, y, z, health, maxHealth, dead,
                biome, dimension, weather, timeOfDay, mainHand, inventory, deathMessage);
    }

    private static String biomeId(Object level, Object blockPos) {
        Object holder = Reflect.invoke(level, "getBiome", blockPos);
        if (holder == null) return "unknown";
        Object key = Reflect.optionalValue(Reflect.invoke(holder, "unwrapKey"));
        if (key == null) key = Reflect.optionalValue(Reflect.invoke(holder, "getKey"));
        return id(key, "unknown");
    }

    private static String id(Object value, String fallback) {
        if (value == null) return fallback;
        Object identifier = Reflect.invoke(value, "identifier");
        if (identifier == null) identifier = Reflect.invoke(value, "location");
        if (identifier == null) identifier = Reflect.invoke(value, "getLocation");
        String text = String.valueOf(identifier == null ? value : identifier);
        if (text == null || text.isBlank() || text.equals("null")) return fallback;
        return text;
    }

    private static String itemId(Object stack) {
        if (stack == null) return "minecraft:air";
        if (Reflect.bool(Reflect.invoke(stack, "isEmpty"), false)) return "minecraft:air";
        Object item = Reflect.invoke(stack, "getItem");
        if (item == null) return "minecraft:air";
        Object registry = Reflect.staticField("net.minecraft.core.registries.BuiltInRegistries", "ITEM");
        Object key = Reflect.invoke(registry, "getKey", item);
        String id = String.valueOf(key);
        return id == null || id.isBlank() || id.equals("null") ? "unknown" : id;
    }

    private static Set<String> inventoryIds(Object player) {
        Set<String> out = new LinkedHashSet<>();
        Object inventory = Reflect.invoke(player, "getInventory");
        int size = (int) Reflect.number(Reflect.invoke(inventory, "getContainerSize"), 0.0D);
        if (size <= 0) size = 46;
        for (int i = 0; i < Math.min(128, size); i++) {
            Object stack = Reflect.invoke(inventory, "getItem", i);
            String id = itemId(stack);
            if (!id.equals("minecraft:air") && !id.equals("unknown")) out.add(id);
        }
        return out;
    }

    private static String contextId(Minecraft minecraft, String uuid) {
        Object currentServer = Reflect.invoke(minecraft, "getCurrentServer");
        if (currentServer == null) currentServer = Reflect.field(minecraft, "currentServer", "currentServerData");
        if (currentServer != null) {
            Object ip = Reflect.field(currentServer, "ip", "address");
            if (ip == null) ip = Reflect.invoke(currentServer, "ip");
            Object name = Reflect.field(currentServer, "name");
            if (name == null) name = Reflect.invoke(currentServer, "name");
            String marker = text(ip) + "|" + text(name);
            if (!marker.equals("|")) return "remote:" + clean(marker);
        }

        Object server = Reflect.invoke(minecraft, "getSingleplayerServer");
        Object worldData = Reflect.invoke(server, "getWorldData");
        String levelName = Reflect.text(Reflect.invoke(worldData, "getLevelName"), "");
        if (!levelName.isBlank()) return "singleplayer:" + clean(levelName);

        Object connection = Reflect.invoke(minecraft, "getConnection");
        Object rawConnection = Reflect.invoke(connection, "getConnection");
        Object remoteAddress = Reflect.invoke(rawConnection, "getRemoteAddress");
        if (remoteAddress != null) return "connection:" + clean(String.valueOf(remoteAddress));

        return "local:" + clean(uuid);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String clean(String value) {
        String s = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
        s = s.replaceAll("[^a-z0-9._:@|\\-]+", "_");
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

    public record Snapshot(
            String uuid,
            String playerName,
            String context,
            double x,
            double y,
            double z,
            float health,
            float maxHealth,
            boolean dead,
            String biome,
            String dimension,
            String weather,
            String timeOfDay,
            String mainHand,
            Set<String> inventoryIds,
            String deathMessage
    ) {}
}
