package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Standalone datapack-authored portal volumes (data/<ns>/dai_portals/*.json). */
public final class DAI_PortalRuntime {
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    private static boolean initialized;
    private static long ticks;

    private DAI_PortalRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.register(DAI_PortalRuntime.class);
        DAI_Core.LOGGER.info("<DAI>: Standalone portal-volume runtime initialized.");
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        ticks++;
        if ((ticks & 1L) != 0L) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (var entry : DAI_GameCustomizationRegistry.entries(DAI_GameCustomizationKind.PORTAL).values()) {
                DAI_GameCustomizationDefinition def = entry.definition();
                if (!def.flag("enabled", true)) continue;
                String sourceDim = def.property("dimension");
                if (!sourceDim.isBlank() && !sourceDim.equals("*") && !sourceDim.equalsIgnoreCase(level.dimension().identifier().toString())) continue;

                AABB box = DAI_VolumeUtil.box(def);
                for (Entity entity : level.getEntities((Entity)null, box, e -> affects(def, e) && DAI_VolumeUtil.contains(def, e))) {
                    if (entity instanceof ServerPlayer p && p.isSpectator()) continue;
                    if (!DAI_VolumeUtil.requirementsPass(def, entity)) continue;
                    String key = entry.id() + "|" + entity.getUUID();
                    if (COOLDOWNS.getOrDefault(key, 0L) > ticks) continue;
                    if (teleport(entity, def)) {
                        COOLDOWNS.put(key, ticks + Math.max(1, (long)def.number("cooldown_ticks", 40)));
                    }
                }
            }
        }
        if ((ticks % 1200L) == 0L) COOLDOWNS.entrySet().removeIf(e -> e.getValue() < ticks - 1200L);
    }

    private static boolean teleport(Entity entity, DAI_GameCustomizationDefinition def) {
        String destination = def.property("destination");
        if (destination.isBlank()) destination = def.carrier();
        if (destination.isBlank()) return false;

        String target = def.property("destination_target");
        if (target.isBlank()) target = def.property("to");
        if (target.isBlank()) {
            target = def.number("to_x", entity.getX()) + " "
                    + def.number("to_y", entity.getY()) + " "
                    + def.number("to_z", entity.getZ());
        }

        Vec3 velocity = entity.getDeltaMovement();
        float yaw = def.flag("preserve_rotation", true) ? entity.getYRot() : (float)def.number("yaw", 0);
        float pitch = def.flag("preserve_rotation", true) ? entity.getXRot() : (float)def.number("pitch", 0);

        String enter = def.event("enter");
        if (!enter.isBlank()) DAI_RuntimeDispatch.dispatch(entity, enter);
        boolean ok = DAI_RuntimeDispatch.dispatch(entity,
                "command:execute in " + destination + " run tp @s " + target + " " + yaw + " " + pitch);
        if (!ok) return false;
        if (def.flag("preserve_velocity", true)) entity.setDeltaMovement(velocity);
        String exit = def.event("exit");
        if (!exit.isBlank()) DAI_RuntimeDispatch.dispatch(entity, exit);
        return true;
    }

    private static boolean affects(DAI_GameCustomizationDefinition def, Entity entity) {
        String raw = def.property("affects");
        if (raw.isBlank() && !def.entries().isEmpty()) raw = String.join(",", def.entries());
        if (raw.isBlank()) raw = "players";
        String set = "," + raw.toLowerCase().replace(' ', ',') + ",";
        if (set.contains(",all,") || set.contains(",any,")) return true;
        if (entity instanceof Player && set.contains(",players,")) return true;
        if (entity instanceof Mob && set.contains(",mobs,")) return true;
        if (entity instanceof ItemEntity && set.contains(",items,")) return true;
        if (entity instanceof Projectile && set.contains(",projectiles,")) return true;
        if (entity.isVehicle() && set.contains(",vehicles,")) return true;
        return !(entity instanceof Player) && set.contains(",entities,");
    }
}
