package io.github.j12h36h.dai.client.content;

import io.github.j12h36h.dai.content.DAI_ContentDefinition;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.content.DAI_ContentStats;

import io.github.j12h36h.dai.api.DAI_CapabilityStore;
import io.github.j12h36h.dai.attributes.DAI_AttributeModifier;
import io.github.j12h36h.dai.attributes.DAI_AttributeStore;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import io.github.j12h36h.dai.network.DAI_ServerMutationPayload;
import io.github.j12h36h.dai.client.network.DAI_ServerBridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Runtime activation state for reloadable DAI virtual content. */
public final class DAI_ContentRuntime {

    private static final Map<UUID, Map<String, Active>> ACTIVE = new HashMap<>();

    private DAI_ContentRuntime() {}

    public static boolean activate(Entity entity, String contentId, int durationOverride, int amplifier) {
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(contentId);
        if (entity == null || entry == null) return false;

        String id = entry.id().toString();
        DAI_ContentDefinition definition = entry.definition();
        deactivate(entity, id);

        int duration = durationOverride > 0 ? durationOverride : definition.stats().durationTicks();
        int safeAmplifier = Math.max(0, amplifier);
        ACTIVE.computeIfAbsent(entity.getUUID(), ignored -> new HashMap<>())
                .put(id, new Active(id, duration, safeAmplifier));

        double scale = safeAmplifier + 1.0D;
        String modifierId = "content:" + id;
        definition.attributes().forEach((attribute, amount) ->
                DAI_AttributeStore.addModifier(
                        entity,
                        attribute,
                        modifierId,
                        amount * scale,
                        DAI_AttributeModifier.Operation.ADD,
                        0
                )
        );

        if (isLocalPlayer(entity)) {
            String capabilitySource = "content:" + id;
            for (String capability : definition.capabilities()) {
                DAI_CapabilityStore.addFromSource(capability, capabilitySource);
            }
            applyNativeAttributes(entity, id, definition, safeAmplifier, true);
        }

        fire(definition.event("activate"));
        fire(definition.event("start"));
        return true;
    }

    public static boolean deactivate(Entity entity, String contentId) {
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(contentId);
        if (entity == null || entry == null) return false;

        Map<String, Active> entityActive = ACTIVE.get(entity.getUUID());
        boolean existed = entityActive != null && entityActive.remove(entry.id().toString()) != null;
        if (entityActive != null && entityActive.isEmpty()) ACTIVE.remove(entity.getUUID());

        String modifierId = "content:" + entry.id();
        entry.definition().attributes().keySet().forEach(attribute ->
                DAI_AttributeStore.removeModifier(entity, attribute, modifierId)
        );

        if (isLocalPlayer(entity)) {
            String capabilitySource = "content:" + entry.id();
            for (String capability : entry.definition().capabilities()) {
                DAI_CapabilityStore.removeFromSource(capability, capabilitySource);
            }
            applyNativeAttributes(entity, entry.id().toString(), entry.definition(), 0, false);
        }

        if (existed) {
            fire(entry.definition().event("deactivate"));
            fire(entry.definition().event("end"));
        }
        return existed;
    }

    public static boolean isActive(Entity entity, String contentId) {
        if (entity == null || contentId == null) return false;
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(contentId);
        if (entry == null) return false;
        Map<String, Active> values = ACTIVE.get(entity.getUUID());
        return values != null && values.containsKey(entry.id().toString());
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
                    contents.remove(active.id);
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
            String contentId,
            DAI_ContentDefinition definition,
            int amplifier,
            boolean add
    ) {
        if (!isLocalPlayer(entity)) return;

        Map<String, Double> nativeValues = new LinkedHashMap<>(definition.nativeAttributes());
        DAI_ContentStats stats = definition.stats();
        mergeNonZero(nativeValues, "minecraft:attack_damage", stats.attackDamage());
        mergeNonZero(nativeValues, "minecraft:attack_speed", stats.attackSpeed());
        mergeNonZero(nativeValues, "minecraft:entity_interaction_range", stats.attackRange());
        mergeNonZero(nativeValues, "minecraft:armor", stats.armor());
        mergeNonZero(nativeValues, "minecraft:armor_toughness", stats.armorToughness());

        if (nativeValues.isEmpty()) return;

        double scale = Math.max(0, amplifier) + 1.0D;
        String safePath = contentId == null
                ? "unknown"
                : contentId.toLowerCase().replace(':', '_').replace('/', '_');
        String modifierId = "decisions_and_impulses:content_" + safePath;
        for (var entry : nativeValues.entrySet()) {
            DAI_ServerBridge.send(new DAI_ServerMutationPayload(
                    add ? "native_attribute_modifier_add" : "native_attribute_modifier_remove",
                    entity.getId(),
                    entry.getKey(),
                    modifierId,
                    "add_value",
                    entry.getValue() * scale,
                    false,
                    0,
                    0
            ));
        }
    }

    private static void mergeNonZero(
            Map<String, Double> values,
            String attribute,
            double amount
    ) {
        if (!Double.isFinite(amount) || amount == 0.0D) return;
        values.merge(attribute, amount, Double::sum);
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

        private Active(String id, int remaining, int amplifier) {
            this.id = id;
            this.remaining = Math.max(0, remaining);
            this.amplifier = Math.max(0, amplifier);
        }
    }
}
