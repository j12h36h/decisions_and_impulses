package io.github.j12h36h.dai.attributes;

import net.minecraft.world.entity.Entity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Runtime values for datapack-defined DAI attributes. */
public final class DAI_AttributeStore {

    private static final Map<UUID, Map<String, Entry>> VALUES = new HashMap<>();

    private DAI_AttributeStore() {}

    public static double value(Entity entity, String attributeId) {
        if (entity == null) return Double.NaN;
        DAI_AttributeDefinition definition = DAI_AttributeRegistry.get(attributeId);
        if (definition == null) return Double.NaN;

        Entry entry = entry(entity, attributeId, false);
        double base = entry == null || entry.base == null
                ? definition.defaultValue()
                : definition.clamp(entry.base);

        if (entry == null || entry.modifiers.isEmpty()) {
            return definition.clamp(base);
        }

        DAI_AttributeModifier setter = entry.modifiers.values().stream()
                .filter(modifier -> modifier.operation() == DAI_AttributeModifier.Operation.SET)
                .max(Comparator.comparingInt(DAI_AttributeModifier::priority))
                .orElse(null);
        if (setter != null) base = setter.amount();

        double add = 0.0D;
        double multiplyBase = 0.0D;
        double total = base;
        for (DAI_AttributeModifier modifier : entry.modifiers.values()) {
            switch (modifier.operation()) {
                case ADD -> add += modifier.amount();
                case MULTIPLY_BASE -> multiplyBase += modifier.amount();
                case MULTIPLY_TOTAL, SET -> {}
            }
        }
        total = base + add + base * multiplyBase;
        for (DAI_AttributeModifier modifier : entry.modifiers.values()) {
            if (modifier.operation() == DAI_AttributeModifier.Operation.MULTIPLY_TOTAL) {
                total *= 1.0D + modifier.amount();
            }
        }
        return definition.clamp(total);
    }

    public static boolean set(Entity entity, String attributeId, double value) {
        DAI_AttributeDefinition definition = DAI_AttributeRegistry.get(attributeId);
        if (entity == null || definition == null || !Double.isFinite(value)) return false;
        entry(entity, attributeId, true).base = definition.clamp(value);
        return true;
    }

    public static boolean add(Entity entity, String attributeId, double amount) {
        if (!Double.isFinite(amount)) return false;
        double current = value(entity, attributeId);
        return Double.isFinite(current) && set(entity, attributeId, current + amount);
    }

    public static boolean reset(Entity entity, String attributeId) {
        if (entity == null) return false;
        Map<String, Entry> entityValues = VALUES.get(entity.getUUID());
        if (entityValues == null) return DAI_AttributeRegistry.contains(attributeId);
        entityValues.remove(normalize(attributeId));
        if (entityValues.isEmpty()) VALUES.remove(entity.getUUID());
        return DAI_AttributeRegistry.contains(attributeId);
    }

    public static boolean addModifier(
            Entity entity,
            String attributeId,
            String modifierId,
            double amount,
            DAI_AttributeModifier.Operation operation,
            int priority
    ) {
        if (entity == null
                || !DAI_AttributeRegistry.contains(attributeId)
                || modifierId == null
                || modifierId.isBlank()
                || !Double.isFinite(amount)) return false;
        entry(entity, attributeId, true).modifiers.put(
                normalize(modifierId),
                new DAI_AttributeModifier(normalize(modifierId), amount, operation, priority)
        );
        return true;
    }

    public static boolean removeModifier(Entity entity, String attributeId, String modifierId) {
        Entry entry = entry(entity, attributeId, false);
        return entry != null && entry.modifiers.remove(normalize(modifierId)) != null;
    }

    public static boolean hasModifier(Entity entity, String attributeId, String modifierId) {
        Entry entry = entry(entity, attributeId, false);
        return entry != null && entry.modifiers.containsKey(normalize(modifierId));
    }

    public static void clear() {
        VALUES.clear();
    }

    private static Entry entry(Entity entity, String attributeId, boolean create) {
        if (entity == null || attributeId == null) return null;
        String id = normalize(attributeId);
        if (id.isEmpty()) return null;
        Map<String, Entry> entityValues = create
                ? VALUES.computeIfAbsent(entity.getUUID(), ignored -> new HashMap<>())
                : VALUES.get(entity.getUUID());
        if (entityValues == null) return null;
        return create
                ? entityValues.computeIfAbsent(id, ignored -> new Entry())
                : entityValues.get(id);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static final class Entry {
        private Double base;
        private final Map<String, DAI_AttributeModifier> modifiers = new LinkedHashMap<>();
    }
}
