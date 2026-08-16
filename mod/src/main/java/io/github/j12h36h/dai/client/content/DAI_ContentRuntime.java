package io.github.j12h36h.dai.client.content;

import io.github.j12h36h.dai.api.DAI_CapabilityStore;
import io.github.j12h36h.dai.attributes.DAI_AttributeModifier;
import io.github.j12h36h.dai.attributes.DAI_AttributeStore;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.network.DAI_ServerBridge;
import io.github.j12h36h.dai.content.DAI_ContentDefinition;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.content.DAI_ContentStats;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.network.DAI_ServerMutationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Runtime activation state for reloadable DAI virtual content. */
public final class DAI_ContentRuntime {

    private static final Map<UUID, Map<String, Active>> ACTIVE = new HashMap<>();

    private DAI_ContentRuntime() {}

    public static boolean activate(Entity entity, String contentId, int durationOverride, int amplifier) {
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(contentId);
        if (entity == null || entry == null) return false;

        String id = entry.id().toString();
        deactivate(entity, id);

        DAI_ContentDefinition definition = entry.definition();
        int duration = durationOverride > 0 ? durationOverride : definition.stats().durationTicks();
        int safeAmplifier = Math.max(0, amplifier);
        Active active = new Active(id, duration, safeAmplifier);

        ACTIVE.computeIfAbsent(entity.getUUID(), ignored -> new HashMap<>())
                .put(id, active);

        applyDefinition(entity, active, definition);
        fire(definition.event("activate"));
        fire(definition.event("start"));
        return true;
    }

    /**
     * Deactivates by id even when the definition disappeared during a hot
     * reload. Applied modifiers/capabilities are stored on the active instance
     * specifically so removing JSON cannot leave stale runtime state behind.
     */
    public static boolean deactivate(Entity entity, String contentId) {
        if (entity == null || contentId == null || contentId.isBlank()) return false;

        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(contentId);
        String id = entry == null
                ? normalize(contentId)
                : entry.id().toString();

        Map<String, Active> entityActive = ACTIVE.get(entity.getUUID());
        if (entityActive == null) return false;

        Active active = entityActive.remove(id);
        if (active == null) return false;
        if (entityActive.isEmpty()) ACTIVE.remove(entity.getUUID());

        removeApplied(entity, active);

        if (entry != null) {
            fire(entry.definition().event("deactivate"));
            fire(entry.definition().event("end"));
        }
        return true;
    }

    public static boolean isActive(Entity entity, String contentId) {
        if (entity == null || contentId == null) return false;
        String id = normalize(contentId);
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(contentId);
        if (entry != null) id = entry.id().toString();
        Map<String, Active> values = ACTIVE.get(entity.getUUID());
        return values != null && values.containsKey(id);
    }

    public static boolean emit(Entity entity, String contentId, String eventName) {
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(contentId);
        if (entity == null || entry == null || eventName == null || eventName.isBlank()) return false;
        String reference = entry.definition().event(eventName);
        if (reference.isBlank()) return false;
        fire(reference);
        return true;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || ACTIVE.isEmpty()) return;

        for (UUID uuid : new ArrayList<>(ACTIVE.keySet())) {
            Entity entity = findEntity(minecraft, uuid);
            if (entity == null) continue;

            Map<String, Active> contents = ACTIVE.get(uuid);
            if (contents == null) continue;

            for (Active active : new ArrayList<>(contents.values())) {
                DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(active.id);
                if (entry == null) {
                    removeActiveWithoutDefinition(entity, contents, active);
                    continue;
                }

                DAI_ContentDefinition definition = entry.definition();
                active.age++;

                if (definition.stats().tickInterval() > 0 && active.age % definition.stats().tickInterval() == 0) {
                    fire(definition.event("tick"));
                }

                if (active.remaining > 0) {
                    active.remaining--;
                    if (active.remaining <= 0) {
                        deactivate(entity, active.id);
                    }
                }
            }

            if (contents.isEmpty()) ACTIVE.remove(uuid);
        }
    }

    /**
     * Rebinds live virtual content to the freshly reloaded JSON definitions.
     * This is the client-side half of DAI hot reload: values that are runtime
     * data change immediately, while native registry shells remain governed by
     * DAI_RegistryPreflight.
     */
    public static void rebindReloadedDefinitions() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || ACTIVE.isEmpty()) return;

        int rebound = 0;
        int removed = 0;

        for (UUID uuid : new ArrayList<>(ACTIVE.keySet())) {
            Entity entity = findEntity(minecraft, uuid);
            if (entity == null) continue;

            Map<String, Active> contents = ACTIVE.get(uuid);
            if (contents == null) continue;

            for (Active active : new ArrayList<>(contents.values())) {
                removeApplied(entity, active);

                DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(active.id);
                if (entry == null) {
                    contents.remove(active.id);
                    removed++;
                    continue;
                }

                applyDefinition(entity, active, entry.definition());
                rebound++;
            }

            if (contents.isEmpty()) ACTIVE.remove(uuid);
        }

        if (rebound > 0 || removed > 0) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Hot reload rebound {} active content instance(s); {} removed definition instance(s) were cleaned up.",
                    rebound,
                    removed
            );
        }
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.level != null) {
            for (UUID uuid : new ArrayList<>(ACTIVE.keySet())) {
                Entity entity = findEntity(minecraft, uuid);
                Map<String, Active> values = ACTIVE.get(uuid);
                if (entity == null || values == null) continue;
                for (String id : new ArrayList<>(values.keySet())) {
                    deactivate(entity, id);
                }
            }
        }
        ACTIVE.clear();
    }

    private static void applyDefinition(
            Entity entity,
            Active active,
            DAI_ContentDefinition definition
    ) {
        if (entity == null || active == null || definition == null) return;

        double scale = active.amplifier + 1.0D;
        String modifierId = modifierId(active.id);

        for (Map.Entry<String, Double> attribute : definition.attributes().entrySet()) {
            if (DAI_AttributeStore.addModifier(
                    entity,
                    attribute.getKey(),
                    modifierId,
                    attribute.getValue() * scale,
                    DAI_AttributeModifier.Operation.ADD,
                    0
            )) {
                active.appliedAttributes.add(attribute.getKey());
            }
        }

        if (isLocalPlayer(entity)) {
            String capabilitySource = "content:" + active.id;
            for (String capability : definition.capabilities()) {
                DAI_CapabilityStore.addFromSource(capability, capabilitySource);
                active.appliedCapabilities.add(capability);
            }
            applyNativeAttributes(entity, active, definition, true);
        }
    }

    private static void removeApplied(Entity entity, Active active) {
        if (entity == null || active == null) return;

        String modifierId = modifierId(active.id);
        for (String attribute : new ArrayList<>(active.appliedAttributes)) {
            DAI_AttributeStore.removeModifier(entity, attribute, modifierId);
        }
        active.appliedAttributes.clear();

        if (isLocalPlayer(entity)) {
            String capabilitySource = "content:" + active.id;
            for (String capability : new ArrayList<>(active.appliedCapabilities)) {
                DAI_CapabilityStore.removeFromSource(capability, capabilitySource);
            }
            active.appliedCapabilities.clear();
            removeNativeAttributes(entity, active);
        } else {
            active.appliedCapabilities.clear();
            active.appliedNativeAttributes.clear();
        }
    }

    private static void removeActiveWithoutDefinition(
            Entity entity,
            Map<String, Active> contents,
            Active active
    ) {
        removeApplied(entity, active);
        contents.remove(active.id);
        DAI_Core.LOGGER.info(
                "<DAI>: Removed active content '{}' because its definition disappeared during reload.",
                active.id
        );
    }

    private static Entity findEntity(Minecraft minecraft, UUID uuid) {
        if (minecraft.player != null && minecraft.player.getUUID().equals(uuid)) return minecraft.player;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) return entity;
        }
        return null;
    }

    private static boolean isLocalPlayer(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player == entity;
    }

    private static void applyNativeAttributes(
            Entity entity,
            Active active,
            DAI_ContentDefinition definition,
            boolean add
    ) {
        if (!isLocalPlayer(entity) || active == null || definition == null) return;

        Map<String, Double> nativeValues = nativeAttributeValues(definition);
        if (nativeValues.isEmpty()) return;

        double scale = active.amplifier + 1.0D;
        String nativeModifierId = nativeModifierId(active.id);
        for (Map.Entry<String, Double> entry : nativeValues.entrySet()) {
            if (add) active.appliedNativeAttributes.add(entry.getKey());
            DAI_ServerBridge.send(new DAI_ServerMutationPayload(
                    add ? "native_attribute_modifier_add" : "native_attribute_modifier_remove",
                    entity.getId(),
                    entry.getKey(),
                    nativeModifierId,
                    "add_value",
                    entry.getValue() * scale,
                    false,
                    0,
                    0
            ));
        }
    }

    private static void removeNativeAttributes(Entity entity, Active active) {
        if (!isLocalPlayer(entity) || active == null || active.appliedNativeAttributes.isEmpty()) return;

        String nativeModifierId = nativeModifierId(active.id);
        for (String attribute : new ArrayList<>(active.appliedNativeAttributes)) {
            DAI_ServerBridge.send(new DAI_ServerMutationPayload(
                    "native_attribute_modifier_remove",
                    entity.getId(),
                    attribute,
                    nativeModifierId,
                    "add_value",
                    0.0D,
                    false,
                    0,
                    0
            ));
        }
        active.appliedNativeAttributes.clear();
    }

    private static Map<String, Double> nativeAttributeValues(DAI_ContentDefinition definition) {
        Map<String, Double> nativeValues = new LinkedHashMap<>(definition.nativeAttributes());
        DAI_ContentStats stats = definition.stats();
        mergeNonZero(nativeValues, "minecraft:attack_damage", stats.attackDamage());
        mergeNonZero(nativeValues, "minecraft:attack_speed", stats.attackSpeed());
        mergeNonZero(nativeValues, "minecraft:entity_interaction_range", stats.attackRange());
        mergeNonZero(nativeValues, "minecraft:armor", stats.armor());
        mergeNonZero(nativeValues, "minecraft:armor_toughness", stats.armorToughness());
        return nativeValues;
    }

    private static void mergeNonZero(
            Map<String, Double> values,
            String attribute,
            double amount
    ) {
        if (!Double.isFinite(amount) || amount == 0.0D) return;
        values.merge(attribute, amount, Double::sum);
    }

    private static String modifierId(String contentId) {
        return "content:" + normalize(contentId);
    }

    private static String nativeModifierId(String contentId) {
        String safePath = normalize(contentId).replace(':', '_').replace('/', '_');
        if (safePath.isBlank()) safePath = "unknown";
        return "decisions_and_impulses:content_" + safePath;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static void fire(String actionReference) {
        if (actionReference == null || actionReference.isBlank()) return;
        DAI_ActionQueue.enqueueDeferredReference(actionReference.trim());
    }

    private static final class Active {
        private final String id;
        private int remaining;
        private final int amplifier;
        private int age;
        private final Set<String> appliedAttributes = new LinkedHashSet<>();
        private final Set<String> appliedCapabilities = new LinkedHashSet<>();
        private final Set<String> appliedNativeAttributes = new LinkedHashSet<>();

        private Active(String id, int remaining, int amplifier) {
            this.id = normalize(id);
            this.remaining = Math.max(0, remaining);
            this.amplifier = Math.max(0, amplifier);
        }
    }
}
