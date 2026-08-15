package io.github.j12h36h.dai.client.entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Physical-client renderer mappings for DAI vanilla entity templates.
 *
 * Keeping renderer class names in the client package prevents the common and
 * dedicated-server class paths from depending on net.minecraft.client types.
 */
public final class DAI_EntityRendererTemplateRegistry {

    private static final Map<String, List<String>> RENDERERS = new LinkedHashMap<>();

    static {
        register("minecraft:pig", "net.minecraft.client.renderer.entity.PigRenderer");
        register("minecraft:cow", "net.minecraft.client.renderer.entity.CowRenderer");
        register("minecraft:chicken", "net.minecraft.client.renderer.entity.ChickenRenderer");
        register("minecraft:sheep", "net.minecraft.client.renderer.entity.SheepRenderer");
        register("minecraft:wolf", "net.minecraft.client.renderer.entity.WolfRenderer");
        register("minecraft:cat", "net.minecraft.client.renderer.entity.CatRenderer");
        register("minecraft:slime", "net.minecraft.client.renderer.entity.SlimeRenderer");
        register("minecraft:zombie", "net.minecraft.client.renderer.entity.ZombieRenderer");
        register("minecraft:villager", "net.minecraft.client.renderer.entity.VillagerRenderer");
        register("minecraft:creeper", "net.minecraft.client.renderer.entity.CreeperRenderer");
        register("minecraft:skeleton", "net.minecraft.client.renderer.entity.SkeletonRenderer");
        register("minecraft:spider", "net.minecraft.client.renderer.entity.SpiderRenderer");
        register("minecraft:enderman", "net.minecraft.client.renderer.entity.EndermanRenderer", "net.minecraft.client.renderer.entity.EnderManRenderer");
        register("minecraft:iron_golem", "net.minecraft.client.renderer.entity.IronGolemRenderer");
        register("minecraft:horse", "net.minecraft.client.renderer.entity.HorseRenderer");
        register("minecraft:rabbit", "net.minecraft.client.renderer.entity.RabbitRenderer");
        register("minecraft:fox", "net.minecraft.client.renderer.entity.FoxRenderer");
        register("minecraft:bee", "net.minecraft.client.renderer.entity.BeeRenderer");
        register("minecraft:goat", "net.minecraft.client.renderer.entity.GoatRenderer");
        register("minecraft:frog", "net.minecraft.client.renderer.entity.FrogRenderer");
        register("minecraft:allay", "net.minecraft.client.renderer.entity.AllayRenderer");
        register("minecraft:warden", "net.minecraft.client.renderer.entity.WardenRenderer");
        register("minecraft:blaze", "net.minecraft.client.renderer.entity.BlazeRenderer");
        register("minecraft:ghast", "net.minecraft.client.renderer.entity.GhastRenderer");
        register("minecraft:drowned", "net.minecraft.client.renderer.entity.DrownedRenderer");
        register("minecraft:pillager", "net.minecraft.client.renderer.entity.PillagerRenderer");
    }

    private DAI_EntityRendererTemplateRegistry() {}

    public static String rendererClass(String templateId) {
        List<String> names = RENDERERS.get(normalizeId(templateId));
        if (names == null || names.isEmpty()) return "";

        for (String name : names) {
            try {
                Class.forName(name);
                return name;
            } catch (Throwable ignored) {
                // Try the next mapping candidate.
            }
        }
        return "";
    }

    private static void register(String id, String... renderers) {
        RENDERERS.put(normalizeId(id), List.of(renderers));
    }

    private static String normalizeId(String value) {
        String id = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (id.isBlank()) id = "minecraft:pig";
        if (!id.contains(":")) id = "minecraft:" + id;
        return id;
    }
}
