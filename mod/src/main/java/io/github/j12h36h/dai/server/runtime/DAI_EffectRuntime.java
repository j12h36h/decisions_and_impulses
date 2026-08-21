package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.attributes.DAI_NativeAttributeSupport;
import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative effect lifecycle for dai_effects, including native attributes. */
public final class DAI_EffectRuntime {
    private static final class Active {
        String id; int remaining; int amplifier; final ArrayList<String> nativeAttrs = new ArrayList<>();
        Active(String id, int remaining, int amplifier) { this.id=id; this.remaining=remaining; this.amplifier=amplifier; }
    }
    private static final Map<UUID, Map<String, Active>> ACTIVE = new HashMap<>();
    private static boolean initialized;

    private DAI_EffectRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return; initialized = true; NeoForge.EVENT_BUS.register(DAI_EffectRuntime.class);
    }

    public static boolean apply(LivingEntity entity, String rawId, int durationOverride, int amplifier) {
        var entry = DAI_ContentRegistry.get(rawId);
        if (entity == null || entry == null || entry.kind() != DAI_ContentKind.EFFECT) return false;
        if (entry.definition().registryBacked()
                && (entry.definition().nativeRegistry().isBlank()
                    || entry.definition().nativeRegistry().equals("effect")
                    || entry.definition().nativeRegistry().equals("mob_effect"))) {
            Identifier nativeId = entry.id();
            var holder = BuiltInRegistries.MOB_EFFECT.get(nativeId).orElse(null);
            if (holder != null) {
                int duration = durationOverride > 0 ? durationOverride : Math.max(1, entry.definition().stats().durationTicks());
                return entity.addEffect(new MobEffectInstance(holder, duration, Math.max(0, amplifier)));
            }
        }
        remove(entity, entry.id().toString());
        int duration = durationOverride > 0 ? durationOverride : Math.max(1, entry.definition().stats().durationTicks());
        Active active = new Active(entry.id().toString(), duration, Math.max(0, amplifier));
        double scale = active.amplifier + 1.0D;
        String modifier = "decisions_and_impulses:effect_" + Integer.toUnsignedString(active.id.hashCode(), 36);
        for (var attr : entry.definition().nativeAttributes().entrySet()) {
            if (DAI_NativeAttributeSupport.addModifier(entity, attr.getKey(), modifier, attr.getValue()*scale, "add", false)) active.nativeAttrs.add(attr.getKey());
        }
        ACTIVE.computeIfAbsent(entity.getUUID(), k -> new HashMap<>()).put(active.id, active);
        DAI_RuntimeDispatch.contentEvent(entity, entry, "apply");
        DAI_RuntimeDispatch.contentEvent(entity, entry, "start");
        return true;
    }

    public static boolean remove(LivingEntity entity, String rawId) {
        if (entity == null || rawId == null) return false;
        var definitionEntry = DAI_ContentRegistry.get(rawId);
        if (definitionEntry != null && definitionEntry.kind() == DAI_ContentKind.EFFECT && definitionEntry.definition().registryBacked()) {
            var holder = BuiltInRegistries.MOB_EFFECT.get(definitionEntry.id()).orElse(null);
            if (holder != null && entity.removeEffect(holder)) return true;
        }
        var map = ACTIVE.get(entity.getUUID()); if (map == null) return false;
        String id = definitionEntry != null ? definitionEntry.id().toString() : rawId;
        Active active = map.remove(id); if (active == null) return false;
        var entry = DAI_ContentRegistry.get(id);
        String modifier = "decisions_and_impulses:effect_" + Integer.toUnsignedString(id.hashCode(), 36);
        for (String attr : active.nativeAttrs) DAI_NativeAttributeSupport.removeModifier(entity, attr, modifier);
        if (entry != null) { DAI_RuntimeDispatch.contentEvent(entity, entry, "remove"); DAI_RuntimeDispatch.contentEvent(entity, entry, "end"); }
        if (map.isEmpty()) ACTIVE.remove(entity.getUUID());
        return true;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (var player : event.getServer().getPlayerList().getPlayers()) tickLiving(player);
        for (var uuid : new ArrayList<>(ACTIVE.keySet())) {
            boolean player = event.getServer().getPlayerList().getPlayer(uuid) != null;
            if (player) continue;
            for (var level : event.getServer().getAllLevels()) {
                var e = level.getEntity(uuid); if (e instanceof LivingEntity living) { tickLiving(living); break; }
            }
        }
    }

    private static void tickLiving(LivingEntity entity) {
        var map = ACTIVE.get(entity.getUUID()); if (map == null) return;
        for (Active active : new ArrayList<>(map.values())) {
            var entry = DAI_ContentRegistry.get(active.id);
            if (entry == null) { remove(entity, active.id); continue; }
            int interval = Math.max(1, entry.definition().stats().tickInterval());
            if (entity.tickCount % interval == 0) DAI_RuntimeDispatch.contentEvent(entity, entry, "tick");
            active.remaining--;
            if (active.remaining <= 0) remove(entity, active.id);
        }
    }
}
