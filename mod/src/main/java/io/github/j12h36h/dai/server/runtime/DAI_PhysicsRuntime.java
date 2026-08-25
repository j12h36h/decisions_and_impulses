package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.physics.DAI_PhysicsProfile;
import io.github.j12h36h.dai.server.creator.DAI_CreatorServerRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Entity-generic dynamic gravity runtime.
 *
 * There is no full-world cost when no dai_physics definitions or Creator test
 * volumes exist. When active, only loaded entities are checked against the
 * authored volumes and entities outside every volume immediately stay on the
 * vanilla path.
 */
public final class DAI_PhysicsRuntime {
    private static final Map<UUID, Boolean> ORIGINAL_NO_GRAVITY = new HashMap<>();
    private static final Map<UUID, String> ACTIVE = new HashMap<>();
    private static final Map<UUID, Vec3> CURRENT_GRAVITY = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_JUMP = new HashMap<>();
    private static boolean initialized;

    private DAI_PhysicsRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.register(DAI_PhysicsRuntime.class);
        DAI_Core.LOGGER.info("<DAI>: Entity-generic dynamic gravity runtime initialized.");
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (DAI_GameCustomizationRegistry.size(DAI_GameCustomizationKind.PHYSICS) == 0
                && !DAI_CreatorServerRuntime.hasPhysicsTests()) {
            if (!ACTIVE.isEmpty()) restoreAll(event);
            return;
        }

        Set<UUID> candidates = new HashSet<>();
        boolean creatorTest = DAI_CreatorServerRuntime.hasPhysicsTests();

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (creatorTest || hasGlobalPhysics(level)) {
                // Creator tests are rare and explicitly opt-in; global physics
                // intentionally targets the entire loaded dimension.
                for (Entity entity : level.getAllEntities()) {
                    if (entity == null || entity.isRemoved()) continue;
                    candidates.add(entity.getUUID());
                    tickEntity(entity);
                }
                continue;
            }

            Map<UUID, Entity> local = new HashMap<>();
            for (var entry : DAI_GameCustomizationRegistry.entries(DAI_GameCustomizationKind.PHYSICS).values()) {
                var def = entry.definition();
                if (!def.flag("enabled", true)) continue;
                String dimension = def.property("dimension");
                if (!dimension.isBlank() && !dimension.equals("*") && !dimension.equalsIgnoreCase("any")
                        && !dimension.equalsIgnoreCase(level.dimension().identifier().toString())) continue;

                var box = DAI_PhysicsProfile.box(def).inflate(0.25D);
                for (Entity entity : level.getEntities((Entity)null, box, candidate -> !candidate.isRemoved())) {
                    local.put(entity.getUUID(), entity);
                }
            }
            for (Entity entity : local.values()) {
                candidates.add(entity.getUUID());
                tickEntity(entity);
            }
        }

        // Anything that was previously governed but is no longer inside a
        // candidate volume must immediately return to its original gravity.
        for (UUID id : Set.copyOf(ACTIVE.keySet())) {
            if (candidates.contains(id)) continue;
            Entity entity = null;
            for (ServerLevel level : event.getServer().getAllLevels()) {
                entity = level.getEntity(id);
                if (entity != null) break;
            }
            if (entity != null) restore(entity);
            else clearState(id);
        }
    }


    private static boolean hasGlobalPhysics(ServerLevel level) {
        String dimensionId = level.dimension().identifier().toString();
        for (var entry : DAI_GameCustomizationRegistry.entries(DAI_GameCustomizationKind.PHYSICS).values()) {
            var def = entry.definition();
            if (!def.flag("enabled", true)) continue;
            String shape = def.property("shape");
            if (!(shape.equalsIgnoreCase("global") || shape.equalsIgnoreCase("world") || shape.equalsIgnoreCase("dimension"))) continue;
            String dimension = def.property("dimension");
            if (dimension.isBlank() || dimension.equals("*") || dimension.equalsIgnoreCase("any")
                    || dimension.equalsIgnoreCase(dimensionId)) return true;
        }
        return false;
    }

    private static void clearState(UUID id) {
        ACTIVE.remove(id);
        ORIGINAL_NO_GRAVITY.remove(id);
        CURRENT_GRAVITY.remove(id);
        LAST_JUMP.remove(id);
    }

    private static void tickEntity(Entity entity) {
        DAI_PhysicsProfile profile = DAI_CreatorServerRuntime.testPhysics(entity);
        if (profile == null) profile = DAI_PhysicsProfile.activeFor(entity);
        UUID id = entity.getUUID();
        if (profile == null) {
            restore(entity);
            LAST_JUMP.remove(id);
            CURRENT_GRAVITY.remove(id);
            return;
        }

        ORIGINAL_NO_GRAVITY.putIfAbsent(id, entity.isNoGravity());
        String previous = ACTIVE.put(id, profile.id());
        if (!profile.id().equals(previous)) {
            if (previous != null) {
                var old = DAI_CreatorServerRuntime.definitionOrRegistryPhysics(previous);
                if (old != null && !old.event("exit").isBlank()) DAI_RuntimeDispatch.dispatch(entity, old.event("exit"));
            }
            if (!profile.definition().event("enter").isBlank()) DAI_RuntimeDispatch.dispatch(entity, profile.definition().event("enter"));
        }

        Vec3 current = CURRENT_GRAVITY.getOrDefault(id, defaultGravityDirection());
        Vec3 gravity = smoothDirection(current, profile.gravity(), 1.0D / Math.max(1, profile.transitionTicks()));
        CURRENT_GRAVITY.put(id, gravity);

        entity.setNoGravity(true);
        Vec3 velocity = entity.getDeltaMovement();
        if (profile.linearDrag() > 0.0D) velocity = velocity.scale(1.0D - profile.linearDrag());
        double along = velocity.dot(gravity);
        if (along < profile.terminalSpeed()) {
            velocity = velocity.add(gravity.scale(profile.gravityStrength()));
        }

        boolean zeroGravity = profile.zeroGravity();
        boolean grounded = !zeroGravity
                && !entity.level().noCollision(entity, entity.getBoundingBox().move(gravity.scale(0.075D)));
        if (grounded && velocity.dot(gravity) > 0.0D) {
            double impact = velocity.dot(gravity);
            velocity = velocity.subtract(gravity.scale(impact));
            if (profile.restitution() > 0.0D && impact > 0.08D) {
                velocity = velocity.subtract(gravity.scale(impact * profile.restitution()));
                grounded = false;
            }
        }
        if (grounded && profile.surfaceDrag() > 0.0D) {
            double normal = velocity.dot(gravity);
            Vec3 tangent = velocity.subtract(gravity.scale(normal)).scale(1.0D - profile.surfaceDrag());
            velocity = tangent.add(gravity.scale(normal));
        }

        if (entity instanceof ServerPlayer player) {
            velocity = zeroGravity && profile.freeFlight()
                    ? applyZeroGravityPlayerMovement(player, profile, velocity)
                    : applyPlayerMovement(player, profile, gravity, velocity, grounded);
        } else if (profile.projectMovement() && !isVanillaDown(gravity)) {
            // Keep existing AI/projectile movement tangent to the active gravity
            // surface instead of letting Y-centric vanilla movement fight the
            // authored gravity direction.
            double gravityComponent = velocity.dot(gravity);
            Vec3 tangent = velocity.subtract(gravity.scale(gravityComponent));
            velocity = tangent.add(gravity.scale(gravityComponent));
        }

        if (profile.maxSpeed() > 0.0D && velocity.lengthSqr() > profile.maxSpeed() * profile.maxSpeed()) {
            velocity = velocity.normalize().scale(profile.maxSpeed());
        }
        entity.setDeltaMovement(velocity);
        entity.setOnGround(grounded);
        if (profile.resetFallDistance()) entity.resetFallDistance();

        String tick = profile.definition().event("tick");
        int interval = Math.max(1, (int)Math.round(profile.definition().number("tick_interval", 1.0D)));
        if (!tick.isBlank() && entity.tickCount % interval == 0) DAI_RuntimeDispatch.dispatch(entity, tick);
    }

    private static Vec3 applyZeroGravityPlayerMovement(
            ServerPlayer player,
            DAI_PhysicsProfile profile,
            Vec3 velocity
    ) {
        DAI_VehicleRuntime.Input input = DAI_VehicleRuntime.inputFor(player);
        Vec3 forward = lookForward(input.yaw(), input.pitch());
        Vec3 referenceUp = Math.abs(forward.y) > 0.98D ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 right = forward.cross(referenceUp);
        if (right.lengthSqr() < 1.0E-8D) right = new Vec3(-1, 0, 0);
        else right = right.normalize();
        Vec3 cameraUp = right.cross(forward);
        if (cameraUp.lengthSqr() < 1.0E-8D) cameraUp = new Vec3(0, 1, 0);
        else cameraUp = cameraUp.normalize();

        double vertical = (input.jump() ? 1.0D : 0.0D) - (input.sneak() ? 1.0D : 0.0D);
        Vec3 drive = forward.scale(input.forward())
                .add(right.scale(input.strafe()))
                .add(cameraUp.scale(vertical));
        if (drive.lengthSqr() > 1.0D) drive = drive.normalize();
        if (drive.lengthSqr() > 1.0E-8D) {
            double sprint = input.sprint() ? 1.6D : 1.0D;
            velocity = velocity.add(drive.scale(profile.movementAcceleration() * profile.movementScale() * sprint));
        }
        LAST_JUMP.put(player.getUUID(), input.jump());
        return velocity;
    }

    private static Vec3 applyPlayerMovement(
            ServerPlayer player,
            DAI_PhysicsProfile profile,
            Vec3 gravity,
            Vec3 velocity,
            boolean grounded
    ) {
        DAI_VehicleRuntime.Input input = DAI_VehicleRuntime.inputFor(player);
        Vec3 tangentForward = tangentForward(input.yaw(), gravity);
        Vec3 up = gravity.scale(-1.0D);
        Vec3 right = tangentForward.cross(up);
        if (right.lengthSqr() > 1.0E-8D) right = right.normalize();
        Vec3 drive = tangentForward.scale(input.forward()).add(right.scale(input.strafe()));
        if (drive.lengthSqr() > 1.0D) drive = drive.normalize();
        if (drive.lengthSqr() > 1.0E-8D) {
            velocity = velocity.add(drive.scale(profile.movementAcceleration() * profile.movementScale()));
        }

        boolean jump = input.jump();
        UUID id = player.getUUID();
        if (grounded && jump && !LAST_JUMP.getOrDefault(id, false)) {
            velocity = velocity.add(up.scale(profile.jumpVelocity()));
        }
        LAST_JUMP.put(id, jump);
        return velocity;
    }

    private static void restoreAll(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (ACTIVE.containsKey(entity.getUUID())) restore(entity);
            }
        }
        ACTIVE.clear();
        CURRENT_GRAVITY.clear();
        LAST_JUMP.clear();
        ORIGINAL_NO_GRAVITY.clear();
    }

    private static void restore(Entity entity) {
        UUID id = entity.getUUID();
        String old = ACTIVE.remove(id);
        if (old != null) {
            var def = DAI_CreatorServerRuntime.definitionOrRegistryPhysics(old);
            if (def != null && !def.event("exit").isBlank()) DAI_RuntimeDispatch.dispatch(entity, def.event("exit"));
        }
        Boolean original = ORIGINAL_NO_GRAVITY.remove(id);
        if (original != null) entity.setNoGravity(original);
    }

    private static Vec3 defaultGravityDirection() {
        return new Vec3(0, -1, 0);
    }

    private static boolean isVanillaDown(Vec3 gravity) {
        return gravity.distanceToSqr(defaultGravityDirection()) < 1.0E-6D;
    }

    /** Smooth unit-vector interpolation, including exact 180-degree flips. */
    public static Vec3 smoothDirection(Vec3 from, Vec3 to, double factor) {
        Vec3 a = from == null || from.lengthSqr() < 1.0E-8D ? defaultGravityDirection() : from.normalize();
        Vec3 b = to == null || to.lengthSqr() < 1.0E-8D ? defaultGravityDirection() : to.normalize();
        double t = Math.max(0.0D, Math.min(1.0D, factor));
        double dot = Math.max(-1.0D, Math.min(1.0D, a.dot(b)));
        if (dot > 0.9995D) return a.scale(1.0D - t).add(b.scale(t)).normalize();
        if (dot < -0.9995D) {
            Vec3 basis = Math.abs(a.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            Vec3 orthogonal = a.cross(basis).normalize();
            double angle = Math.PI * t;
            return a.scale(Math.cos(angle)).add(orthogonal.scale(Math.sin(angle))).normalize();
        }
        double theta = Math.acos(dot) * t;
        Vec3 relative = b.subtract(a.scale(dot)).normalize();
        return a.scale(Math.cos(theta)).add(relative.scale(Math.sin(theta))).normalize();
    }

    private static Vec3 lookForward(float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        return new Vec3(
                -Math.sin(yaw) * cosPitch,
                -Math.sin(pitch),
                Math.cos(yaw) * cosPitch
        ).normalize();
    }

    private static Vec3 tangentForward(float yawDegrees, Vec3 gravity) {
        double radians = Math.toRadians(yawDegrees);
        Vec3 raw = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        Vec3 projected = raw.subtract(gravity.scale(raw.dot(gravity)));
        if (projected.lengthSqr() < 1.0E-8D) {
            Vec3 fallback = Math.abs(gravity.y) < 0.9D ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
            projected = fallback.subtract(gravity.scale(fallback.dot(gravity)));
        }
        return projected.normalize();
    }
}
