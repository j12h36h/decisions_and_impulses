package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Entity-side physics/behavior overlay for dai_fluids carrier blocks/fluids. */
public final class DAI_FluidRuntime {
    private static final Set<String> INSIDE = new HashSet<>();
    private static final Map<String, Boolean> ORIGINAL_NO_GRAVITY = new HashMap<>();
    private static boolean initialized;

    private DAI_FluidRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.register(DAI_FluidRuntime.class);
        DAI_Core.LOGGER.info("<DAI>: Fluid behavior runtime initialized.");
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        Set<String> now = new HashSet<>();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.isRemoved()) continue;
                String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(entity.blockPosition()).getBlock()).toString();
                Identifier fluidKey = BuiltInRegistries.FLUID.getKey(level.getFluidState(entity.blockPosition()).getType());
                String fluidId = fluidKey == null ? "" : fluidKey.toString();
                for (var entry : DAI_GameCustomizationRegistry.entries(DAI_GameCustomizationKind.FLUID).values()) {
                    var def = entry.definition();
                    String carrier = def.carrier();
                    if (carrier.isBlank() || (!carrier.equals(blockId) && !carrier.equals(fluidId))) continue;
                    String key = entry.id() + "|" + entity.getUUID();
                    now.add(key);
                    if (!INSIDE.contains(key)) {
                        dispatch(entity, def.event("enter"));
                        if (def.flag("no_gravity", false)) {
                            ORIGINAL_NO_GRAVITY.put(key, entity.isNoGravity());
                        }
                    }
                    apply(entity, level, def);
                    if (entity.tickCount % Math.max(1, (int)def.number("tick_interval", 1)) == 0) dispatch(entity, def.event("tick"));
                }
            }
        }
        for (String old : Set.copyOf(INSIDE)) {
            if (now.contains(old)) continue;
            int split = old.lastIndexOf('|');
            if (split <= 0) continue;
            var defEntry = DAI_GameCustomizationRegistry.get(DAI_GameCustomizationKind.FLUID, old.substring(0, split));
            if (defEntry == null) continue;
            try {
                var uuid = java.util.UUID.fromString(old.substring(split + 1));
                for (ServerLevel level : event.getServer().getAllLevels()) {
                    Entity entity = level.getEntity(uuid);
                    if (entity != null) {
                        dispatch(entity, defEntry.definition().event("exit"));
                        if (defEntry.definition().flag("no_gravity", false)) {
                            boolean original = ORIGINAL_NO_GRAVITY.getOrDefault(old, false);
                            entity.setNoGravity(original);
                        }
                    }
                }
            } catch (IllegalArgumentException ignored) {}
            ORIGINAL_NO_GRAVITY.remove(old);
        }
        INSIDE.clear(); INSIDE.addAll(now);
    }

    private static void apply(Entity entity, ServerLevel level, io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition def) {
        double drag = Math.max(0.0, Math.min(1.0, def.number("drag", 0.2)));
        double verticalDrag = Math.max(0.0, Math.min(1.0, def.number("vertical_drag", drag)));
        double buoyancy = def.number("buoyancy", 0.0);
        Vec3 v = entity.getDeltaMovement();
        entity.setDeltaMovement(v.x * (1.0 - drag), v.y * (1.0 - verticalDrag) + buoyancy, v.z * (1.0 - drag));
        double damage = Math.max(0.0, def.number("damage", 0.0));
        int interval = Math.max(1, (int)def.number("damage_interval", 20));
        if (damage > 0 && entity.tickCount % interval == 0 && entity instanceof LivingEntity living) {
            living.hurtServer(level, living.damageSources().generic(), (float)damage);
        }
        if (def.flag("extinguish", false)) entity.clearFire();
        if (def.flag("no_gravity", false)) entity.setNoGravity(true);
    }

    private static void dispatch(Entity entity, String ref) { if (ref != null && !ref.isBlank()) DAI_RuntimeDispatch.dispatch(entity, ref); }
}
