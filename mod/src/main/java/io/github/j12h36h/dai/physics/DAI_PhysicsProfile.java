package io.github.j12h36h.dai.physics;

import io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable, hot-reloadable view of one data/&lt;ns&gt;/dai_physics definition.
 *
 * Physics definitions are intentionally entity-generic. A volume may target
 * players, mobs, living entities, dropped items, projectiles, vehicles, or all
 * entities without adding a Java implementation for each category.
 */
public record DAI_PhysicsProfile(
        String id,
        DAI_GameCustomizationDefinition definition,
        Vec3 gravity,
        double gravityStrength,
        double movementAcceleration,
        double movementScale,
        double jumpVelocity,
        double terminalSpeed,
        double linearDrag,
        double surfaceDrag,
        double restitution,
        double maxSpeed,
        int transitionTicks,
        float cameraRoll,
        float cameraPitchOffset,
        float cameraYawOffset,
        boolean alignCamera,
        boolean alignEntity,
        boolean projectMovement,
        boolean resetFallDistance,
        Set<String> affects
) {
    public static DAI_PhysicsProfile activeFor(Entity entity) {
        if (entity == null) return null;
        DAI_GameCustomizationRegistry.Entry best = null;
        double bestPriority = -Double.MAX_VALUE;
        for (var entry : DAI_GameCustomizationRegistry.entries(DAI_GameCustomizationKind.PHYSICS).values()) {
            var def = entry.definition();
            if (!def.flag("enabled", true)
                    || !dimensionMatches(def, entity)
                    || !requirementsPass(def, entity)
                    || !contains(def, entity)) {
                continue;
            }
            DAI_PhysicsProfile candidate = from(entry.id().toString(), def);
            if (candidate == null || !candidate.affects(entity)) continue;
            double priority = def.number("priority", 0.0D);
            if (best == null || priority > bestPriority) {
                best = entry;
                bestPriority = priority;
            }
        }
        return best == null ? null : from(best.id().toString(), best.definition());
    }

    public static DAI_PhysicsProfile from(String id, DAI_GameCustomizationDefinition def) {
        if (def == null) return null;
        Vec3 gravity = new Vec3(
                def.number("gravity_x", 0.0D),
                def.number("gravity_y", -1.0D),
                def.number("gravity_z", 0.0D)
        );
        if (gravity.lengthSqr() < 1.0E-8D) gravity = new Vec3(0, -1, 0);
        gravity = gravity.normalize();

        double strength = Math.max(0.0D, def.number("gravity_strength", 0.08D));
        double movementAcceleration = Math.max(0.0D, def.number("movement_acceleration", 0.035D));
        double movementScale = Math.max(0.0D, def.number("movement_scale", 1.0D));
        double jump = Math.max(0.0D, def.number("jump_velocity", 0.42D));
        double terminal = Math.max(0.05D, def.number("terminal_speed", 3.92D));
        double linearDrag = clamp01(def.number("linear_drag", 0.0D));
        double surfaceDrag = clamp01(def.number("surface_drag", 0.08D));
        double restitution = clamp01(def.number("restitution", 0.0D));
        double maxSpeed = Math.max(0.0D, def.number("max_speed", 0.0D));
        int transition = Math.max(1, (int)Math.round(def.number("transition_ticks", 12.0D)));

        float roll = (float)def.number("camera_roll", autoRoll(gravity));
        float pitch = (float)def.number("camera_pitch_offset", autoPitch(gravity));
        float yaw = (float)def.number("camera_yaw_offset", 0.0D);

        return new DAI_PhysicsProfile(
                id == null ? "" : id,
                def,
                gravity,
                strength,
                movementAcceleration,
                movementScale,
                jump,
                terminal,
                linearDrag,
                surfaceDrag,
                restitution,
                maxSpeed,
                transition,
                roll,
                pitch,
                yaw,
                def.flag("align_camera", true),
                def.flag("align_entity", true),
                def.flag("project_movement", true),
                def.flag("reset_fall_distance", true),
                parseAffects(def.property("affects"))
        );
    }

    /** True when this profile disables directional gravity entirely. */
    public boolean zeroGravity() {
        return gravityStrength <= 1.0E-8D;
    }

    /**
     * Zero-G defaults to camera-relative free-flight controls, but packs may
     * disable that and keep pure inertial/no-gravity behavior instead.
     */
    public boolean freeFlight() {
        return definition.flag("free_flight", zeroGravity());
    }

    public boolean affects(Entity entity) {
        if (entity == null) return false;
        if (affects.isEmpty() || affects.contains("all") || affects.contains("entities")) return true;
        if (entity instanceof Player && affects.contains("players")) return true;
        if (entity instanceof Mob && affects.contains("mobs")) return true;
        if (entity instanceof LivingEntity && affects.contains("living")) return true;
        if (entity instanceof ItemEntity && (affects.contains("items") || affects.contains("dropped_items"))) return true;
        if (entity instanceof Projectile && affects.contains("projectiles")) return true;
        if (isVehicleLike(entity) && (affects.contains("vehicles") || affects.contains("mounts"))) return true;
        if (!(entity instanceof Player) && affects.contains("non_players")) return true;
        String type = entity.getType().toString().toLowerCase(Locale.ROOT);
        return affects.stream().anyMatch(value -> value.startsWith("type:") && type.contains(value.substring(5)));
    }

    public static Vec3 center(DAI_GameCustomizationDefinition def) {
        String raw = def.target();
        if (raw != null && !raw.isBlank()) {
            String[] p = raw.trim().replace(',', ' ').split("\\s+");
            if (p.length >= 3) {
                try {
                    return new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                } catch (NumberFormatException ignored) {
                    // Fall through to explicit numeric fields.
                }
            }
        }
        return new Vec3(def.number("x", 0), def.number("y", 0), def.number("z", 0));
    }

    public static AABB box(DAI_GameCustomizationDefinition def) {
        Vec3 c = center(def);
        double radius = Math.max(0.05, def.number("radius", 1.5));
        double width = Math.max(0.05, def.number("width", radius * 2.0));
        double height = Math.max(0.05, def.number("height", radius * 2.0));
        double depth = Math.max(0.05, def.number("depth", width));
        return new AABB(
                c.x - width / 2, c.y - height / 2, c.z - depth / 2,
                c.x + width / 2, c.y + height / 2, c.z + depth / 2
        );
    }

    public static boolean contains(DAI_GameCustomizationDefinition def, Entity entity) {
        if (entity == null) return false;
        String shape = def.property("shape");
        if (shape.equalsIgnoreCase("global") || shape.equalsIgnoreCase("world")
                || shape.equalsIgnoreCase("dimension")) return true;
        if (shape.equalsIgnoreCase("sphere") || shape.equalsIgnoreCase("radius")) {
            Vec3 c = center(def);
            double r = Math.max(0.05, def.number("radius", 1.5));
            return entity.position().distanceToSqr(c) <= r * r;
        }
        return box(def).intersects(entity.getBoundingBox());
    }

    public static boolean dimensionMatches(DAI_GameCustomizationDefinition def, Entity entity) {
        String dimension = def.property("dimension");
        if (dimension.isBlank() || dimension.equalsIgnoreCase("any") || dimension.equals("*")) return true;
        return entity.level().dimension().identifier().toString().equalsIgnoreCase(dimension);
    }

    public static boolean requirementsPass(DAI_GameCustomizationDefinition def, Entity entity) {
        String required = def.property("required_tag");
        if (!required.isBlank() && !entity.tags().toList().contains(required)) return false;
        String forbidden = def.property("forbidden_tag");
        return forbidden.isBlank() || !entity.tags().toList().contains(forbidden);
    }


    private static boolean isVehicleLike(Entity entity) {
        if (entity == null) return false;
        if (entity.isVehicle() || entity.isPassenger()) return true;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key == null) return false;
        String id = key.toString();
        for (var entry : DAI_GameCustomizationRegistry.entries(DAI_GameCustomizationKind.VEHICLE).values()) {
            if (id.equals(entry.definition().carrier())) return true;
        }
        return false;
    }

    private static Set<String> parseAffects(String raw) {
        if (raw == null || raw.isBlank()) return Set.of("all");
        return Arrays.stream(raw.split("[,;| ]+"))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static float autoRoll(Vec3 gravity) {
        if (gravity.y > 0.707D) return 180.0F;
        if (gravity.x > 0.707D) return 90.0F;
        if (gravity.x < -0.707D) return -90.0F;
        return 0.0F;
    }

    private static float autoPitch(Vec3 gravity) {
        if (gravity.z > 0.707D) return -90.0F;
        if (gravity.z < -0.707D) return 90.0F;
        return 0.0F;
    }
}
