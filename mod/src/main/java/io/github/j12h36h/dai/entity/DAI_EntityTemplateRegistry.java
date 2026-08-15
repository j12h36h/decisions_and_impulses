package io.github.j12h36h.dai.entity;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps friendly JSON carrier/model ids onto vanilla mob implementations.
 *
 * DAI intentionally starts with vanilla entity implementations because they
 * already provide stable models, animation state, navigation, sounds and goal
 * AI. A JSON entity receives its own registered EntityType while its factory
 * constructs one of these vanilla mob classes using that custom type.
 */
public final class DAI_EntityTemplateRegistry {

    public record Template(
            String id,
            List<String> entityClasses,
            MobCategory defaultCategory
    ) {}

    private static final Map<String, Template> TEMPLATES = new LinkedHashMap<>();

    static {
        register("minecraft:pig", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.Pig", "net.minecraft.world.entity.animal.pig.Pig"));
        register("minecraft:cow", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.Cow", "net.minecraft.world.entity.animal.cow.Cow"));
        register("minecraft:chicken", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.Chicken", "net.minecraft.world.entity.animal.chicken.Chicken"));
        register("minecraft:sheep", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.Sheep", "net.minecraft.world.entity.animal.sheep.Sheep"));
        register("minecraft:wolf", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.Wolf", "net.minecraft.world.entity.animal.wolf.Wolf"));
        register("minecraft:cat", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.Cat", "net.minecraft.world.entity.animal.cat.Cat"));
        register("minecraft:slime", MobCategory.MONSTER,
                classes("net.minecraft.world.entity.monster.Slime", "net.minecraft.world.entity.monster.slime.Slime"));
        register("minecraft:zombie", MobCategory.MONSTER,
                classes("net.minecraft.world.entity.monster.Zombie", "net.minecraft.world.entity.monster.zombie.Zombie"));
        register("minecraft:villager", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.npc.Villager"));
        register("minecraft:creeper", MobCategory.MONSTER,
                classes("net.minecraft.world.entity.monster.Creeper", "net.minecraft.world.entity.monster.creeper.Creeper"));
        register("minecraft:skeleton", MobCategory.MONSTER,
                classes("net.minecraft.world.entity.monster.Skeleton", "net.minecraft.world.entity.monster.skeleton.Skeleton"));
        register("minecraft:spider", MobCategory.MONSTER,
                classes("net.minecraft.world.entity.monster.Spider", "net.minecraft.world.entity.monster.spider.Spider"));
        register("minecraft:enderman", MobCategory.MONSTER,
                classes("net.minecraft.world.entity.monster.EnderMan", "net.minecraft.world.entity.monster.enderman.EnderMan"));
        register("minecraft:iron_golem", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.IronGolem", "net.minecraft.world.entity.animal.golem.IronGolem"));
        register("minecraft:horse", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.horse.Horse"));
        register("minecraft:rabbit", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.Rabbit", "net.minecraft.world.entity.animal.rabbit.Rabbit"));
        register("minecraft:fox", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.Fox", "net.minecraft.world.entity.animal.fox.Fox"));
        register("minecraft:bee", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.Bee", "net.minecraft.world.entity.animal.bee.Bee"));
        register("minecraft:goat", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.goat.Goat"));
        register("minecraft:frog", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.frog.Frog"));
        register("minecraft:allay", MobCategory.CREATURE,
                classes("net.minecraft.world.entity.animal.allay.Allay"));
        register("minecraft:warden", MobCategory.MONSTER,
                classes("net.minecraft.world.entity.monster.warden.Warden"));
        register("minecraft:blaze", MobCategory.MONSTER,
                classes("net.minecraft.world.entity.monster.Blaze", "net.minecraft.world.entity.monster.blaze.Blaze"));
        register("minecraft:ghast", MobCategory.MONSTER,
                classes("net.minecraft.world.entity.monster.Ghast", "net.minecraft.world.entity.monster.ghast.Ghast"));
        register("minecraft:drowned", MobCategory.MONSTER,
                classes("net.minecraft.world.entity.monster.Drowned", "net.minecraft.world.entity.monster.drowned.Drowned"));
        register("minecraft:pillager", MobCategory.MONSTER,
                classes("net.minecraft.world.entity.monster.Pillager", "net.minecraft.world.entity.monster.illager.Pillager"));
    }

    private DAI_EntityTemplateRegistry() {}

    public static Template get(String raw) {
        String id = normalize(raw);
        if (id.isBlank()) id = "minecraft:pig";
        if (!id.contains(":")) id = "minecraft:" + id;
        return TEMPLATES.get(id);
    }

    public static MobCategory category(String requested, String templateId) {
        String value = normalize(requested);
        return switch (value) {
            case "monster" -> MobCategory.MONSTER;
            case "creature" -> MobCategory.CREATURE;
            case "ambient" -> MobCategory.AMBIENT;
            case "axolotls" -> MobCategory.AXOLOTLS;
            case "underground_water_creature" -> MobCategory.UNDERGROUND_WATER_CREATURE;
            case "water_creature" -> MobCategory.WATER_CREATURE;
            case "water_ambient" -> MobCategory.WATER_AMBIENT;
            case "misc" -> MobCategory.MISC;
            default -> {
                Template template = get(templateId);
                yield template == null ? MobCategory.CREATURE : template.defaultCategory();
            }
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Mob create(EntityType type, Level level, String templateId) {
        Template template = get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Unknown DAI entity template '" + templateId + "'.");
        }

        Throwable last = null;
        for (String className : template.entityClasses()) {
            try {
                Class<?> entityClass = Class.forName(className);
                Constructor<?> ctor = entityClass.getConstructor(EntityType.class, Level.class);
                Object created = ctor.newInstance(type, level);
                if (created instanceof Mob mob) return mob;
            } catch (Throwable exception) {
                last = exception;
            }
        }

        DAI_Core.LOGGER.error(
                "<DAI>: Could not instantiate vanilla entity template '{}' for custom type.",
                template.id(),
                last
        );
        throw new IllegalStateException("Could not instantiate entity template " + template.id(), last);
    }

    /**
     * Reuses the vanilla template's own attribute builder whenever the mapped
     * entity class exposes one. This preserves template-specific attributes
     * (for example follow/tempt ranges) before JSON values override them.
     */
    public static AttributeSupplier.Builder createDefaultAttributes(String templateId) {
        Template template = get(templateId);
        if (template != null) {
            for (String className : template.entityClasses()) {
                try {
                    Class<?> entityClass = Class.forName(className);
                    Method preferred = null;
                    for (Method method : entityClass.getMethods()) {
                        if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) continue;
                        if (!AttributeSupplier.Builder.class.isAssignableFrom(method.getReturnType())) continue;
                        String name = method.getName().toLowerCase(Locale.ROOT);
                        if (!name.contains("attribute")) continue;
                        preferred = method;
                        if (name.equals("createattributes")) break;
                    }
                    if (preferred != null) {
                        Object value = preferred.invoke(null);
                        if (value instanceof AttributeSupplier.Builder builder) return builder;
                    }
                } catch (Throwable ignored) {
                    // Try the next mapped class, then fall back to LivingEntity.
                }
            }
        }
        return LivingEntity.createLivingAttributes();
    }

    public static boolean supports(String templateId) {
        return get(templateId) != null;
    }

    private static void register(
            String id,
            MobCategory category,
            List<String> entityClasses
    ) {
        TEMPLATES.put(normalize(id), new Template(normalize(id), entityClasses, category));
    }

    private static List<String> classes(String... values) {
        return List.of(values);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
