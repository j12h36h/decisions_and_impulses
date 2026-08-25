package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.content.DAI_ProjectileSettings;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.action.DAI_ActionArguments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
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
 * Physical runtime for dai_projectiles. The carrier may be any summonable
 * entity (including a DAI-native entity); DAI owns velocity, collision and
 * lifecycle after spawn.
 */
public final class DAI_ProjectileRuntime {

    private record Active(String contentId, UUID owner, int age, int ricochets, int pierces, Set<UUID> hit) {
        Active ageOne() { return new Active(contentId, owner, age + 1, ricochets, pierces, hit); }
        Active ricochet() { return new Active(contentId, owner, age, ricochets + 1, pierces, hit); }
        Active pierce() { return new Active(contentId, owner, age, ricochets, pierces + 1, hit); }
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static boolean initialized;

    private DAI_ProjectileRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.register(DAI_ProjectileRuntime.class);
        DAI_Core.LOGGER.info("<DAI>: Projectile physics runtime initialized.");
    }

    public static boolean spawn(ServerPlayer owner, String rawId) {
        return spawn((LivingEntity) owner, rawId, DAI_ActionArguments.EMPTY);
    }

    public static boolean spawn(ServerPlayer owner, String rawId, DAI_ActionArguments arguments) {
        return spawn((LivingEntity) owner, rawId, arguments);
    }

    public static boolean spawn(LivingEntity owner, String rawId, DAI_ActionArguments arguments) {
        if (owner == null || !(owner.level() instanceof ServerLevel level)) return false;
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(rawId);
        if (entry == null || entry.kind() != DAI_ContentKind.PROJECTILE) return false;

        String carrier = entry.definition().carrier();
        Identifier carrierId = Identifier.tryParse(carrier);
        if (carrierId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(carrierId)) {
            DAI_Core.LOGGER.warn("<DAI>: Projectile '{}' has unknown entity carrier '{}'.", rawId, carrier);
            return false;
        }

        DAI_ActionArguments args = arguments == null ? DAI_ActionArguments.EMPTY : arguments;
        int count = Math.max(1, Math.min(64, args.integer("count", 1)));
        double spread = Math.max(0.0D, Math.min(180.0D, args.number("spread", 0.0D)));
        double speed = args.number("speed", entry.definition().stats().projectileSpeed());
        if (!(speed > 0.0D)) speed = 1.5D;
        Vec3 origin = resolveOrigin(owner, args);
        Vec3 baseDirection = resolveDirection(owner, origin, args);
        if (baseDirection.lengthSqr() < 0.000001D) baseDirection = owner.getLookAngle();
        baseDirection = baseDirection.normalize();

        boolean any = false;
        for (int i = 0; i < count; i++) {
            Vec3 direction = applySpread(baseDirection, spread, level.getRandom());
            any |= spawnOne(level, owner, entry, carrier, origin, direction, speed, args.bool("inherit_velocity", false));
        }
        return any;
    }

    private static boolean spawnOne(
            ServerLevel level, LivingEntity owner, DAI_ContentRegistry.Entry entry, String carrier,
            Vec3 origin, Vec3 direction, double speed, boolean inheritVelocity
    ) {
        String token = "dai_p_" + UUID.randomUUID().toString().replace("-", "");
        String command = "summon " + carrier + " " + origin.x + " " + origin.y + " " + origin.z
                + " {Tags:[\"dai_projectile\",\"" + token + "\"]}";
        if (!DAI_RuntimeDispatch.dispatch(owner, "command:" + command)) return false;

        Entity projectile = null;
        AABB box = new AABB(origin, origin).inflate(5.0D);
        for (Entity candidate : level.getEntities(owner, box, entity -> entity.tags().toList().contains(token))) {
            projectile = candidate;
            break;
        }
        if (projectile == null) return false;

        projectile.removeTag(token);
        projectile.addTag("dai_projectile");
        Vec3 velocity = direction.normalize().scale(speed);
        if (inheritVelocity) velocity = velocity.add(owner.getDeltaMovement());
        projectile.setDeltaMovement(velocity);
        projectile.setYRot((float)(Math.toDegrees(Math.atan2(-velocity.x, velocity.z))));
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        projectile.setXRot((float)(-Math.toDegrees(Math.atan2(velocity.y, horizontal))));

        ACTIVE.put(projectile.getUUID(), new Active(
                entry.id().toString(), owner.getUUID(), 0, 0, 0, new HashSet<>()
        ));
        DAI_RuntimeDispatch.contentEvent(projectile, entry, "spawn");
        return true;
    }

    private static Vec3 resolveOrigin(LivingEntity owner, DAI_ActionArguments args) {
        String mode = args.normalized("origin", "eye");
        Vec3 base = switch (mode) {
            case "feet", "position", "player" -> owner.position();
            case "center" -> owner.getBoundingBox().getCenter();
            default -> owner.getEyePosition();
        };
        double[] offset = args.vector("offset", 0.0D, 0.0D, 0.0D);
        return base.add(offset[0], offset[1], offset[2]);
    }

    private static Vec3 resolveDirection(LivingEntity owner, Vec3 origin, DAI_ActionArguments args) {
        double[] explicit = args.vector("direction", Double.NaN, Double.NaN, Double.NaN);
        if (Double.isFinite(explicit[0]) && Double.isFinite(explicit[1]) && Double.isFinite(explicit[2])) {
            return new Vec3(explicit[0], explicit[1], explicit[2]);
        }
        double[] target = args.vector("target_position", Double.NaN, Double.NaN, Double.NaN);
        if (Double.isFinite(target[0]) && Double.isFinite(target[1]) && Double.isFinite(target[2])) {
            return new Vec3(target[0], target[1], target[2]).subtract(origin);
        }
        int entityId = args.integer("target_entity_id", -1);
        if (entityId >= 0) {
            Entity targetEntity = owner.level().getEntity(entityId);
            if (targetEntity != null) return targetEntity.getBoundingBox().getCenter().subtract(origin);
        }
        return owner.getLookAngle();
    }

    private static Vec3 applySpread(Vec3 direction, double degrees, net.minecraft.util.RandomSource random) {
        if (!(degrees > 0.0D)) return direction;
        double scale = Math.tan(Math.toRadians(degrees));
        Vec3 jitter = new Vec3(
                (random.nextDouble() * 2.0D - 1.0D) * scale,
                (random.nextDouble() * 2.0D - 1.0D) * scale,
                (random.nextDouble() * 2.0D - 1.0D) * scale
        );
        Vec3 result = direction.add(jitter);
        return result.lengthSqr() < 0.000001D ? direction : result.normalize();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (UUID uuid : Set.copyOf(ACTIVE.keySet())) {
                Entity projectile = level.getEntity(uuid);
                if (projectile == null || projectile.isRemoved()) continue;
                tick(level, projectile, ACTIVE.get(uuid));
            }
        }
        ACTIVE.entrySet().removeIf(e -> {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                Entity entity = level.getEntity(e.getKey());
                if (entity != null && !entity.isRemoved()) return false;
            }
            return true;
        });
    }

    private static void tick(ServerLevel level, Entity projectile, Active active) {
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(active.contentId());
        if (entry == null) {
            projectile.discard();
            ACTIVE.remove(projectile.getUUID());
            return;
        }
        DAI_ProjectileSettings settings = entry.definition().projectile();
        Active nextActive = active.ageOne();
        ACTIVE.put(projectile.getUUID(), nextActive);

        if (nextActive.age() >= settings.lifetime()) {
            expire(projectile, entry);
            return;
        }

        Entity owner = level.getEntity(active.owner());
        Vec3 velocity = projectile.getDeltaMovement();

        if (settings.returnToOwner() && nextActive.age() >= settings.returnAfterTicks() && owner != null) {
            Vec3 desired = owner.getEyePosition().subtract(projectile.position());
            if (desired.lengthSqr() < 2.0D) {
                DAI_RuntimeDispatch.contentEvent(projectile, entry, "return");
                projectile.discard();
                ACTIVE.remove(projectile.getUUID());
                return;
            }
            velocity = steer(velocity, desired, Math.max(0.15D, settings.homingStrength()));
        } else if (settings.homingRadius() > 0.0D && settings.homingStrength() > 0.0D) {
            LivingEntity target = nearestTarget(level, projectile, owner, settings, active.hit());
            if (target != null) velocity = steer(velocity, target.getEyePosition().subtract(projectile.position()), settings.homingStrength());
        }

        double gravity = entry.definition().stats().gravity();
        velocity = new Vec3(velocity.x, velocity.y - gravity, velocity.z)
                .scale(1.0D - settings.drag());

        Collision collision = trace(level, projectile, owner, settings, active.hit(), velocity);
        if (collision.entity != null) {
            Active changed = hitEntity(level, projectile, owner, collision.entity, entry, nextActive, settings);
            if (changed == null) return;
            nextActive = changed;
            ACTIVE.put(projectile.getUUID(), changed);
        }
        if (collision.block && settings.collideBlocks()) {
            DAI_RuntimeDispatch.contentEvent(projectile, entry, "hit_block");
            if (nextActive.ricochets() < settings.ricochets()) {
                velocity = velocity.scale(-0.82D);
                nextActive = nextActive.ricochet();
                ACTIVE.put(projectile.getUUID(), nextActive);
                DAI_RuntimeDispatch.contentEvent(projectile, entry, "ricochet");
            } else {
                expire(projectile, entry);
                return;
            }
        }

        projectile.setDeltaMovement(velocity);
        DAI_RuntimeDispatch.contentEvent(projectile, entry, "tick");
    }

    private static Active hitEntity(
            ServerLevel level, Entity projectile, Entity owner, LivingEntity target,
            DAI_ContentRegistry.Entry entry, Active active, DAI_ProjectileSettings settings
    ) {
        active.hit().add(target.getUUID());
        if (settings.damage() > 0.0D) {
            if (owner instanceof Player player) {
                target.hurtServer(level, player.damageSources().playerAttack(player), (float) settings.damage());
            } else {
                target.hurtServer(level, target.damageSources().generic(), (float) settings.damage());
            }
        }
        if (settings.knockback() > 0.0D) {
            Vec3 push = projectile.getDeltaMovement();
            if (push.lengthSqr() > 0.0001D) {
                push = push.normalize().scale(settings.knockback());
                target.push(push.x, Math.max(0.05D, push.y), push.z);
            }
        }
        DAI_RuntimeDispatch.contentEvent(projectile, entry, "hit_entity");
        if (active.pierces() < settings.pierce()) {
            DAI_RuntimeDispatch.contentEvent(projectile, entry, "pierce");
            return active.pierce();
        }
        expire(projectile, entry);
        return null;
    }

    private static void expire(Entity projectile, DAI_ContentRegistry.Entry entry) {
        DAI_RuntimeDispatch.contentEvent(projectile, entry, "expire");
        projectile.discard();
        ACTIVE.remove(projectile.getUUID());
    }

    private static LivingEntity nearestTarget(
            ServerLevel level, Entity projectile, Entity owner,
            DAI_ProjectileSettings settings, Set<UUID> alreadyHit
    ) {
        AABB area = projectile.getBoundingBox().inflate(settings.homingRadius());
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!validTarget(entity, owner, settings, alreadyHit)) continue;
            double distance = entity.distanceToSqr(projectile);
            if (distance < bestDistance) { bestDistance = distance; best = entity; }
        }
        return best;
    }

    private static Collision trace(
            ServerLevel level, Entity projectile, Entity owner,
            DAI_ProjectileSettings settings, Set<UUID> alreadyHit, Vec3 velocity
    ) {
        double length = velocity.length();
        int samples = Math.max(1, (int)Math.ceil(length / 0.25D));
        Vec3 step = samples == 0 ? Vec3.ZERO : velocity.scale(1.0D / samples);
        Vec3 pos = projectile.position();
        for (int i = 1; i <= samples; i++) {
            Vec3 sample = pos.add(step.scale(i));
            if (settings.collideBlocks()) {
                BlockPos bp = BlockPos.containing(sample);
                if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) return new Collision(true, null);
            }
            if (settings.collideEntities()) {
                AABB box = new AABB(sample, sample).inflate(settings.hitRadius());
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
                    if (validTarget(entity, owner, settings, alreadyHit)) return new Collision(false, entity);
                }
            }
        }
        return new Collision(false, null);
    }

    private static boolean validTarget(LivingEntity entity, Entity owner, DAI_ProjectileSettings settings, Set<UUID> alreadyHit) {
        if (!entity.isAlive() || alreadyHit.contains(entity.getUUID())) return false;
        if (owner != null && entity.getUUID().equals(owner.getUUID()) && !settings.hitOwner()) return false;
        if (!settings.hitAllies() && owner != null && owner.getTeam() != null && owner.getTeam() == entity.getTeam()) return false;
        return true;
    }

    private static Vec3 steer(Vec3 current, Vec3 desired, double strength) {
        if (desired.lengthSqr() < 0.0001D) return current;
        double speed = Math.max(0.05D, current.length());
        Vec3 target = desired.normalize().scale(speed);
        return current.lerp(target, Math.max(0.0D, Math.min(1.0D, strength)));
    }

    private record Collision(boolean block, LivingEntity entity) {}
}
