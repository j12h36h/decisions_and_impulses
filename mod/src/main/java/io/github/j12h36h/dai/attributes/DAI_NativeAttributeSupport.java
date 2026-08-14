package io.github.j12h36h.dai.attributes;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Safe adapter over Minecraft's built-in living-entity attributes. */
public final class DAI_NativeAttributeSupport {

    private DAI_NativeAttributeSupport() {}

    public static Holder<Attribute> resolve(String id) {
        String value = normalize(id);
        if (value.startsWith("minecraft:")) value = value.substring("minecraft:".length());
        return switch (value) {
            case "armor" -> Attributes.ARMOR;
            case "armor_toughness" -> Attributes.ARMOR_TOUGHNESS;
            case "attack_damage" -> Attributes.ATTACK_DAMAGE;
            case "attack_knockback" -> Attributes.ATTACK_KNOCKBACK;
            case "attack_speed" -> Attributes.ATTACK_SPEED;
            case "block_break_speed" -> Attributes.BLOCK_BREAK_SPEED;
            case "block_interaction_range" -> Attributes.BLOCK_INTERACTION_RANGE;
            case "burning_time" -> Attributes.BURNING_TIME;
            case "camera_distance" -> Attributes.CAMERA_DISTANCE;
            case "explosion_knockback_resistance" -> Attributes.EXPLOSION_KNOCKBACK_RESISTANCE;
            case "entity_interaction_range", "attack_range" -> Attributes.ENTITY_INTERACTION_RANGE;
            case "fall_damage_multiplier" -> Attributes.FALL_DAMAGE_MULTIPLIER;
            case "flying_speed" -> Attributes.FLYING_SPEED;
            case "follow_range" -> Attributes.FOLLOW_RANGE;
            case "gravity" -> Attributes.GRAVITY;
            case "jump_strength" -> Attributes.JUMP_STRENGTH;
            case "knockback_resistance" -> Attributes.KNOCKBACK_RESISTANCE;
            case "luck" -> Attributes.LUCK;
            case "max_absorption" -> Attributes.MAX_ABSORPTION;
            case "max_health" -> Attributes.MAX_HEALTH;
            case "mining_efficiency" -> Attributes.MINING_EFFICIENCY;
            case "movement_efficiency" -> Attributes.MOVEMENT_EFFICIENCY;
            case "movement_speed" -> Attributes.MOVEMENT_SPEED;
            case "oxygen_bonus" -> Attributes.OXYGEN_BONUS;
            case "safe_fall_distance" -> Attributes.SAFE_FALL_DISTANCE;
            case "scale" -> Attributes.SCALE;
            case "sneaking_speed" -> Attributes.SNEAKING_SPEED;
            case "step_height" -> Attributes.STEP_HEIGHT;
            case "submerged_mining_speed" -> Attributes.SUBMERGED_MINING_SPEED;
            case "sweeping_damage_ratio" -> Attributes.SWEEPING_DAMAGE_RATIO;
            case "tempt_range" -> Attributes.TEMPT_RANGE;
            case "water_movement_efficiency" -> Attributes.WATER_MOVEMENT_EFFICIENCY;
            case "waypoint_transmit_range" -> Attributes.WAYPOINT_TRANSMIT_RANGE;
            case "waypoint_receive_range" -> Attributes.WAYPOINT_RECEIVE_RANGE;
            default -> null;
        };
    }

    public static double read(LivingEntity entity, String id) {
        Holder<Attribute> attribute = resolve(id);
        if (entity == null || attribute == null || !entity.getAttributes().hasAttribute(attribute)) {
            return Double.NaN;
        }
        return entity.getAttributeValue(attribute);
    }

    public static boolean setBase(LivingEntity entity, String id, double value) {
        AttributeInstance instance = instance(entity, id);
        if (instance == null || !Double.isFinite(value)) return false;
        instance.setBaseValue(value);
        return true;
    }

    public static boolean addModifier(
            LivingEntity entity,
            String attributeId,
            String modifierId,
            double amount,
            String operation,
            boolean persistent
    ) {
        AttributeInstance instance = instance(entity, attributeId);
        Identifier id = parseIdentifier(modifierId);
        if (instance == null || id == null || !Double.isFinite(amount)) return false;

        AttributeModifier modifier = new AttributeModifier(
                id,
                amount,
                nativeOperation(operation)
        );

        if (persistent) {
            instance.addOrReplacePermanentModifier(modifier);
        } else {
            instance.addOrUpdateTransientModifier(modifier);
        }
        return true;
    }

    public static boolean removeModifier(LivingEntity entity, String attributeId, String modifierId) {
        AttributeInstance instance = instance(entity, attributeId);
        Identifier id = parseIdentifier(modifierId);
        if (instance == null || id == null) return false;
        instance.removeModifier(id);
        return true;
    }

    public static boolean hasModifier(LivingEntity entity, String attributeId, String modifierId) {
        AttributeInstance instance = instance(entity, attributeId);
        Identifier id = parseIdentifier(modifierId);
        return instance != null && id != null && instance.hasModifier(id);
    }

    private static AttributeInstance instance(LivingEntity entity, String id) {
        Holder<Attribute> attribute = resolve(id);
        if (entity == null || attribute == null) return null;
        return entity.getAttribute(attribute);
    }

    private static AttributeModifier.Operation nativeOperation(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "add_multiplied_base", "multiply_base", "mul_base" ->
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total", "multiply_total", "mul_total" ->
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }

    private static Identifier parseIdentifier(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) return null;
        if (!normalized.contains(":")) normalized = "decisions_and_impulses:" + normalized;
        return Identifier.tryParse(normalized);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
