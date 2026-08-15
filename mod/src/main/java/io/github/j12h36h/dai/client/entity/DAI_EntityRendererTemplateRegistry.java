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
        return names.getFirst();
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
