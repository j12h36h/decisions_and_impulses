package io.github.j12h36h.dai.server.entity;

import io.github.j12h36h.dai.entity.DAI_EntitySettings;
import io.github.j12h36h.dai.entity.DAI_EntitySpawnSettings;
import io.github.j12h36h.dai.entity.DAI_EntityMovementSettings;
import io.github.j12h36h.dai.entity.DAI_EntityPortalSettings;
import io.github.j12h36h.dai.entity.DAI_EntityTemplateRegistry;

import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import io.github.j12h36h.dai.logics.action.DAI_ActionReference;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionDefinition;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import io.github.j12h36h.dai.server.runtime.DAI_RuntimeDispatch;
import io.github.j12h36h.dai.server.runtime.DAI_ProjectileRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_ParticleRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_EffectRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_PotionRuntime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Runtime bridge between JSON entity definitions and Minecraft mobs.
 *
 * Native DAI entities use a generic physical mob with JSON-owned AI. Legacy
 * carrier-backed entities can still retain vanilla AI when requested. A DAI
 * behavior_sequence supplies actor-safe movement/look/target/combat decisions,
 * and natural spawning is evaluated from JSON without generated mob Java.
 */
public final class DAI_EntityRuntime {

    private static long serverTicks;
    private static final Map<UUID, BehaviorState> BEHAVIOR_STATE = new HashMap<>();
    private static final Map<UUID, MovementState> MOVEMENT_STATE = new HashMap<>();
    private static final Map<UUID, Long> PORTAL_COOLDOWNS = new HashMap<>();
    private static final Set<UUID> INITIALIZED_GAMEPLAY = new HashSet<>();
    private static final Map<UUID, GameplayState> GAMEPLAY_STATE = new HashMap<>();

    private DAI_EntityRuntime() {}

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener(DAI_EntityRuntime::onServerTick);
        NeoForge.EVENT_BUS.addListener(DAI_EntityRuntime::onDamage);
        NeoForge.EVENT_BUS.addListener(DAI_EntityRuntime::onDeath);
        NeoForge.EVENT_BUS.addListener(DAI_EntityRuntime::onAttack);
        NeoForge.EVENT_BUS.addListener(DAI_EntityRuntime::onInteract);
        NeoForge.EVENT_BUS.addListener(DAI_EntityRuntime::onJump);
        NeoForge.EVENT_BUS.addListener(DAI_EntityRuntime::onFall);
        NeoForge.EVENT_BUS.addListener(DAI_EntityRuntime::onMount);
    }

    /**
     * Called after DAI datapack definitions are hot-reloaded. Behavior
     * sequences and spawn rules are looked up dynamically, so clearing only
     * their scheduling cursors is enough to make the new graph take effect on
     * the next server tick. One-time mutations of an already-existing mob
     * (such as equipment already placed on it) are intentionally not replayed.
     */
    public static void onDefinitionsReloaded() {
        BEHAVIOR_STATE.clear();
        MOVEMENT_STATE.clear();
        GAMEPLAY_STATE.clear();
        DAI_Core.LOGGER.info(
                "<DAI>: Custom-entity runtime adopted reloaded behavior/spawn definitions."
        );
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof Mob victim) {
            DAI_EntitySettings settings = settings(victim);
            if (settings != null) runGameplayEvent(victim, settings, "hurt");
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof Mob attacker) {
            DAI_EntitySettings settings = settings(attacker);
            if (settings != null) runGameplayEvent(attacker, settings, "damage_dealt");
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof Mob victim) {
            DAI_EntitySettings settings = settings(victim);
            if (settings != null) {
                Entity directKiller = event.getSource().getEntity();
                net.minecraft.server.level.ServerPlayer playerKiller = directKiller instanceof net.minecraft.server.level.ServerPlayer player
                        ? player : null;
                if (playerKiller != null) playerKiller.addTag("dai_entity_killer_context");
                try {
                    runGameplayEvent(victim, settings, "death");
                } finally {
                    if (playerKiller != null) playerKiller.removeTag("dai_entity_killer_context");
                }
                String loot = settings.gameplay().loot();
                if (!loot.isBlank()) DAI_RuntimeDispatch.dispatch(victim, "command:loot spawn ~ ~ ~ loot " + loot);
            }
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof Mob killer) {
            DAI_EntitySettings settings = settings(killer);
            if (settings != null) runGameplayEvent(killer, settings, "kill");
        }
    }

    private static void onAttack(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getTarget() instanceof Mob target) {
            DAI_EntitySettings settings = settings(target);
            if (settings != null) runGameplayEvent(target, settings, "attacked");
        }
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getTarget() instanceof Mob mob)) return;
        DAI_EntitySettings settings = settings(mob);
        if (settings == null) return;
        runGameplayEvent(mob, settings, "interact");

        String dialogueId = settings.gameplay().dialogue();
        if (dialogueId.isBlank()) return;
        DAI_GameCustomizationRegistry.Entry dialogue =
                DAI_GameCustomizationRegistry.get(DAI_GameCustomizationKind.DIALOGUE, dialogueId);
        if (dialogue == null) return;
        String reference = dialogue.definition().event("open");
        if (reference.isBlank()) reference = dialogue.definition().event("run");
        if (reference.isBlank()) reference = dialogue.definition().command();
        if (!reference.isBlank()) DAI_RuntimeDispatch.dispatch(event.getEntity(), reference);
    }

    private static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        DAI_EntitySettings settings = settings(mob);
        if (settings != null) runGameplayEvent(mob, settings, "jump");
    }

    private static void onFall(LivingFallEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        DAI_EntitySettings settings = settings(mob);
        if (settings != null) runGameplayEvent(mob, settings, "fall");
    }

    private static void onMount(EntityMountEvent event) {
        if (event.getEntityMounting().level().isClientSide()) return;
        if (event.getEntityBeingMounted() instanceof Mob vehicle) {
            DAI_EntitySettings settings = settings(vehicle);
            if (settings != null) runGameplayEvent(vehicle, settings, event.isMounting() ? "passenger_added" : "passenger_removed");
        }
        if (event.getEntityMounting() instanceof Mob rider) {
            DAI_EntitySettings settings = settings(rider);
            if (settings != null) runGameplayEvent(rider, settings, event.isMounting() ? "mounted" : "dismounted");
        }
    }

    private static DAI_EntitySettings settings(Mob mob) {
        if (mob == null) return null;
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (id == null) return null;
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(id.toString());
        return entry != null && entry.kind() == DAI_ContentKind.ENTITY ? entry.definition().entity() : null;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server == null) return;
        serverTicks++;

        for (var level : server.getAllLevels()) {
            tickBehaviors(level);
            tickNaturalSpawns(level);
        }
    }

    private static void tickBehaviors(net.minecraft.server.level.ServerLevel level) {
        Set<UUID> visited = new HashSet<>();

        for (var player : level.players()) {
            AABB box = player.getBoundingBox().inflate(64.0D);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, box, value -> value.isAlive())) {
                if (!visited.add(mob.getUUID())) continue;

                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
                if (id == null) continue;
                DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(id.toString());
                if (entry == null || entry.kind() != DAI_ContentKind.ENTITY) continue;

                DAI_EntitySettings settings = entry.definition().entity();
                initializeGameplay(mob, settings);
                tickGameplayEvents(mob, settings);
                tickMovement(mob, settings.movement());
                tickPortal(mob, settings.portal());
                if (settings.behaviorSequence().isBlank()) continue;

                BehaviorState state = BEHAVIOR_STATE.computeIfAbsent(
                        mob.getUUID(),
                        ignored -> new BehaviorState()
                );
                if (serverTicks < state.nextTick) continue;

                runNextBehaviorAction(mob, settings, state);
            }
        }
    }

    private static void initializeGameplay(
            Mob mob,
            DAI_EntitySettings settings
    ) {
        if (mob == null || settings == null || !INITIALIZED_GAMEPLAY.add(mob.getUUID())) return;

        if (!settings.vanillaAi()) {
            disableVanillaGoals(mob);
        }

        DAI_EntityMovementSettings movement = settings.movement();
        mob.setNoGravity(movement.noGravity());
        setNoPhysics(mob, movement.noCollision());

        for (String raw : settings.gameplay().equipment()) {
            if (raw == null || raw.isBlank()) continue;
            String[] pair = raw.trim().split("=", 2);
            if (pair.length != 2) {
                DAI_Core.LOGGER.warn("<DAI>: Invalid custom-entity equipment entry '{}'.", raw);
                continue;
            }

            EquipmentSlot slot = equipmentSlot(pair[0]);
            Identifier itemId = Identifier.tryParse(pair[1].trim());
            Item item = itemId == null ? null : BuiltInRegistries.ITEM.getValue(itemId);
            if (slot == null || item == null) {
                DAI_Core.LOGGER.warn("<DAI>: Could not resolve custom-entity equipment '{}'.", raw);
                continue;
            }
            mob.setItemSlot(slot, new ItemStack(item));
        }

        applyGameplayFaction(mob, settings);
        runGameplayEvent(mob, settings, "spawn");
    }

    private static void applyGameplayFaction(Mob mob, DAI_EntitySettings settings) {
        String factionId = settings.gameplay().faction();
        if (factionId.isBlank()) return;

        DAI_GameCustomizationRegistry.Entry faction =
                DAI_GameCustomizationRegistry.get(DAI_GameCustomizationKind.FACTION, factionId);
        if (faction == null) return;

        String primaryTag = faction.definition().property("tag");
        if (!primaryTag.isBlank()) mob.addTag(primaryTag);
        for (String tag : faction.definition().tags()) {
            if (tag != null && !tag.isBlank()) mob.addTag(tag.trim());
        }
    }

    private static void tickGameplayEvents(Mob mob, DAI_EntitySettings settings) {
        if (mob == null || settings == null) return;
        GameplayState state = GAMEPLAY_STATE.computeIfAbsent(mob.getUUID(), ignored -> new GameplayState());

        if (serverTicks >= state.nextTick) {
            runGameplayEvent(mob, settings, "tick");
            state.nextTick = serverTicks + Math.max(1, settings.behaviorInterval());
        }

        UUID target = mob.getTarget() == null ? null : mob.getTarget().getUUID();
        if (!java.util.Objects.equals(target, state.target)) {
            if (target != null) runGameplayEvent(mob, settings, "target_acquired");
            else if (state.target != null) runGameplayEvent(mob, settings, "target_lost");
            state.target = target;
        }

        boolean mounted = mob.isVehicle();
        if (mounted != state.mounted) {
            runGameplayEvent(mob, settings, mounted ? "mount" : "dismount");
            state.mounted = mounted;
        }
    }

    private static void runGameplayEvent(Mob mob, DAI_EntitySettings settings, String eventName) {
        if (mob == null || settings == null || eventName == null) return;
        String reference = settings.gameplay().event(eventName);
        if (reference.isBlank()) return;

        List<DAI_ActionDefinition> actions = resolveBehavior(reference);
        if (actions.isEmpty()) {
            DAI_Core.debug(
                    "<DAI>: Entity gameplay event '{}' could not resolve action reference '{}'.",
                    eventName,
                    reference
            );
            return;
        }

        for (DAI_ActionDefinition action : actions) {
            if (entityConditionsPass(mob, action.conditions())) executeActorAction(mob, action);
        }
    }

    private static void disableVanillaGoals(Mob mob) {
        Class<?> type = mob.getClass();
        while (type != null && type != Object.class) {
            for (var field : type.getDeclaredFields()) {
                if (!field.getType().getName().endsWith("GoalSelector")) continue;
                try {
                    field.setAccessible(true);
                    Object selector = field.get(mob);
                    if (selector == null) continue;
                    for (var method : selector.getClass().getMethods()) {
                        if (!method.getName().equals("removeAllGoals") || method.getParameterCount() != 0) continue;
                        method.invoke(selector);
                        break;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Keep the template AI if this Minecraft mapping changes;
                    // behavior_sequence still layers deterministically above it.
                }
            }
            type = type.getSuperclass();
        }
    }

    private static EquipmentSlot equipmentSlot(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        return switch (value) {
            case "mainhand", "main_hand", "hand" -> EquipmentSlot.MAINHAND;
            case "offhand", "off_hand" -> EquipmentSlot.OFFHAND;
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            default -> null;
        };
    }


    private static void tickMovement(
            Mob mob,
            DAI_EntityMovementSettings movement
    ) {
        if (mob == null || movement == null || !movement.ownsMovement()) return;

        // These two shell-level flags are cheap to reapply and therefore hot-
        // reload cleanly even for already-spawned portal entities.
        mob.setNoGravity(movement.noGravity());
        setNoPhysics(mob, movement.noCollision());

        MovementState state = MOVEMENT_STATE.computeIfAbsent(
                mob.getUUID(), ignored -> new MovementState()
        );
        String type = movement.type();
        Player player = nearestPlayer(mob, Math.max(48.0D, movement.radius() * 4.0D));

        if (movement.lookAtPlayer() && player != null) {
            mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
        }

        switch (type) {
            case "stationary", "fixed" -> {
                mob.getNavigation().stop();
                if (movement.noGravity()) mob.setDeltaMovement(Vec3.ZERO);
            }
            case "drift" -> {
                Vec3 direction = new Vec3(
                        movement.driftX(), movement.driftY(), movement.driftZ()
                );
                if (direction.lengthSqr() > 1.0E-8D) {
                    mob.setDeltaMovement(direction.normalize().scale(movement.speed()));
                }
            }
            case "follow", "follow_player" -> {
                if (player != null) moveToward(mob, player.position(), movement.speed(), movement.flyingStyle());
            }
            case "flee", "flee_player", "avoid_player" -> {
                if (player != null) {
                    Vec3 away = mob.position().subtract(player.position());
                    if (away.lengthSqr() < 1.0E-8D) away = new Vec3(1.0D, 0.0D, 0.0D);
                    Vec3 destination = mob.position().add(away.normalize().scale(Math.max(2.0D, movement.radius())));
                    moveToward(mob, destination, movement.speed(), movement.flyingStyle());
                }
            }
            case "orbit", "orbit_player" -> {
                if (player != null) {
                    Vec3 radial = mob.position().subtract(player.position());
                    if (radial.lengthSqr() < 1.0E-8D) radial = new Vec3(movement.radius(), 0.0D, 0.0D);
                    Vec3 horizontal = new Vec3(radial.x, 0.0D, radial.z);
                    if (horizontal.lengthSqr() < 1.0E-8D) horizontal = new Vec3(1.0D, 0.0D, 0.0D);
                    Vec3 tangent = new Vec3(-horizontal.z, 0.0D, horizontal.x).normalize();
                    double desiredRadius = Math.max(0.5D, movement.radius());
                    double error = horizontal.length() - desiredRadius;
                    Vec3 correction = horizontal.normalize().scale(-error * 0.08D);
                    Vec3 velocity = tangent.scale(movement.speed()).add(correction);
                    double yError = player.getY() - mob.getY();
                    velocity = velocity.add(0.0D, Math.max(-movement.verticalRange(), Math.min(movement.verticalRange(), yError)) * 0.03D, 0.0D);
                    mob.setDeltaMovement(velocity);
                }
            }
            case "wander", "floating_wander" -> {
                if (serverTicks < state.nextTick) return;
                state.nextTick = serverTicks + movement.intervalTicks();
                Vec3 destination = randomDestination(mob, movement.radius(), movement.verticalRange());
                moveToward(mob, destination, movement.speed(), movement.flyingStyle() || type.equals("floating_wander"));
            }
            case "relocate", "blink", "teleport_wander" -> {
                if (serverTicks < state.nextTick) return;
                state.nextTick = serverTicks + movement.intervalTicks();
                Vec3 destination = randomDestination(mob, movement.radius(), movement.verticalRange());
                mob.getNavigation().stop();
                mob.setPos(destination.x, destination.y, destination.z);
                mob.setDeltaMovement(Vec3.ZERO);
            }
            default -> { }
        }
    }

    private static Vec3 randomDestination(Mob mob, double radius, double verticalRange) {
        var random = mob.getRandom();
        double x = mob.getX() + (random.nextDouble() * 2.0D - 1.0D) * radius;
        double y = mob.getY() + (random.nextDouble() * 2.0D - 1.0D) * verticalRange;
        double z = mob.getZ() + (random.nextDouble() * 2.0D - 1.0D) * radius;
        return new Vec3(x, y, z);
    }

    private static void moveToward(Mob mob, Vec3 destination, double speed, boolean flying) {
        if (flying) {
            Vec3 delta = destination.subtract(mob.position());
            if (delta.lengthSqr() > 1.0E-8D) {
                mob.setDeltaMovement(delta.normalize().scale(speed));
            }
            return;
        }
        mob.getNavigation().moveTo(destination.x, destination.y, destination.z, Math.max(0.05D, speed));
    }

    private static void tickPortal(
            Mob portalEntity,
            DAI_EntityPortalSettings portal
    ) {
        if (portalEntity == null || portal == null || !portal.enabled() || portal.destination().isBlank()) return;
        if (!(portalEntity.level() instanceof net.minecraft.server.level.ServerLevel level)) return;

        AABB trigger = portalEntity.getBoundingBox().inflate(portal.triggerRadius());
        for (Entity subject : level.getEntitiesOfClass(
                Entity.class,
                trigger,
                value -> value != portalEntity && value.isAlive() && portalAffects(portal, value)
        )) {
            if (subject instanceof ServerPlayer player && player.isSpectator()) continue;

            long readyTick = PORTAL_COOLDOWNS.getOrDefault(subject.getUUID(), 0L);
            if (serverTicks < readyTick) continue;
            if (subject.distanceToSqr(portalEntity) > portal.triggerRadius() * portal.triggerRadius()) continue;

            if (teleportThroughPortal(subject, portalEntity, portal)) {
                PORTAL_COOLDOWNS.put(subject.getUUID(), serverTicks + portal.cooldownTicks());
            }
        }
    }

    private static boolean portalAffects(DAI_EntityPortalSettings portal, Entity subject) {
        if (portal == null || subject == null) return false;
        if (portal.affects("all") || portal.affects("any")) return true;
        if (subject instanceof Player && portal.affects("players")) return true;
        if (subject instanceof Mob && portal.affects("mobs")) return true;
        if (subject instanceof ItemEntity && portal.affects("items")) return true;
        if (subject instanceof Projectile && portal.affects("projectiles")) return true;
        if (subject.isVehicle() && portal.affects("vehicles")) return true;
        return !(subject instanceof Player) && portal.affects("entities");
    }

    private static boolean teleportThroughPortal(
            Entity subject,
            Mob portalEntity,
            DAI_EntityPortalSettings portal
    ) {
        MinecraftServer server = subject.level().getServer();
        if (server == null) return false;

        Destination destination = resolvePortalDestination(portal.destination());
        Identifier dimensionId = Identifier.tryParse(destination.dimension());
        if (dimensionId == null) {
            DAI_Core.LOGGER.warn("<DAI>: Portal entity '{}' requested invalid dimension '{}'.", portalEntity.getType(), destination.dimension());
            return false;
        }

        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        if (server.getLevel(dimensionKey) == null) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Portal entity '{}' cannot enter unloaded/missing dimension '{}'. Generated DAI dimensions require a world reload/restart after their source JSON changes.",
                    portalEntity.getType(), destination.dimension()
            );
            return false;
        }

        Vec3 target = switch (portal.targetMode()) {
            case "fixed" -> new Vec3(portal.x(), portal.y(), portal.z());
            case "relative", "offset" -> subject.position().add(portal.x(), portal.y(), portal.z());
            case "portal_relative" -> portalEntity.position().add(portal.x(), portal.y(), portal.z());
            case "dimension_default" -> destination.target() == null ? subject.position() : destination.target();
            default -> subject.position();
        };

        Vec3 velocity = subject.getDeltaMovement();
        float yaw = portal.preserveRotation() ? subject.getYRot() : portal.yaw();
        float pitch = portal.preserveRotation() ? subject.getXRot() : portal.pitch();

        if (!portal.enterCommand().isBlank()) {
            performServerCommand(subject, portal.enterCommand());
        }

        String command = "execute in " + destination.dimension() + " run tp @s "
                + target.x + " " + target.y + " " + target.z + " " + yaw + " " + pitch;
        if (!performServerCommand(subject, command)) return false;

        if (portal.preserveVelocity()) {
            subject.setDeltaMovement(velocity);
        }

        if (!portal.exitCommand().isBlank()) {
            performServerCommand(subject, portal.exitCommand());
        }

        return true;
    }

    private static Destination resolvePortalDestination(String requested) {
        String normalized = requested == null ? "" : requested.trim().toLowerCase(Locale.ROOT);
        DAI_GameCustomizationRegistry.Entry entry =
                DAI_GameCustomizationRegistry.get(DAI_GameCustomizationKind.DIMENSION, normalized);
        if (entry == null) return new Destination(normalized, null);

        DAI_GameCustomizationDefinition definition = entry.definition();
        String dimension = definition.carrier().isBlank()
                ? entry.id().toString()
                : definition.carrier();
        return new Destination(dimension, parseTarget(definition.target()));
    }

    private static Vec3 parseTarget(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] split = raw.trim().split("\\s+");
        if (split.length < 3) return null;
        try {
            return new Vec3(
                    Double.parseDouble(split[0]),
                    Double.parseDouble(split[1]),
                    Double.parseDouble(split[2])
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean performServerCommand(Entity actor, String rawCommand) {
        MinecraftServer server = actor.level().getServer();
        if (server == null) return false;
        String command = rawCommand == null ? "" : rawCommand.trim();
        while (command.startsWith("/")) command = command.substring(1);
        if (command.isBlank()) return true;

        Object source = server.createCommandSourceStack();
        Object quiet = invokeNoArg(source, "withSuppressedOutput");
        if (quiet != null) source = quiet;

        String wrapped = "execute as " + actor.getUUID() + " at @s run " + command;
        Object commands = server.getCommands();
        for (Method method : commands.getClass().getMethods()) {
            String name = method.getName();
            if (!name.equals("performPrefixedCommand") && !name.equals("performCommand")) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length != 2 || types[1] != String.class || !types[0].isInstance(source)) continue;
            try {
                method.invoke(commands, source, wrapped);
                return true;
            } catch (ReflectiveOperationException exception) {
                DAI_Core.LOGGER.warn("<DAI>: Portal command '{}' failed.", command, exception);
                return false;
            }
        }
        return false;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (Throwable ignored) { }
        try {
            Method method = target.getClass().getDeclaredMethod(name);
            if (!method.canAccess(target) && !method.trySetAccessible()) return null;
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setNoPhysics(Mob mob, boolean value) {
        Class<?> type = mob.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField("noPhysics");
                if (!field.canAccess(mob) && !field.trySetAccessible()) return;
                field.setBoolean(mob, value);
                return;
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
    }

    private static void runNextBehaviorAction(
            Mob mob,
            DAI_EntitySettings settings,
            BehaviorState state
    ) {
        List<DAI_ActionDefinition> actions = resolveBehavior(settings.behaviorSequence());
        if (actions.isEmpty()) {
            state.nextTick = serverTicks + settings.behaviorInterval();
            return;
        }

        // Conditions that do not match are skipped without stalling the whole
        // behavior graph. This gives entity sequences useful branching while
        // keeping player-only condition providers out of the server actor path.
        for (int attempts = 0; attempts < actions.size(); attempts++) {
            int index = Math.floorMod(state.cursor, actions.size());
            state.cursor = (index + 1) % actions.size();
            DAI_ActionDefinition action = actions.get(index);
            if (!entityConditionsPass(mob, action.conditions())) continue;

            executeActorAction(mob, action);
            int delay = settings.behaviorInterval();
            if (action.type().equals("wait") && action.ticks() > 0) {
                delay = Math.max(delay, action.ticks());
            }
            state.nextTick = serverTicks + Math.max(1, delay);
            return;
        }

        state.nextTick = serverTicks + settings.behaviorInterval();
    }

    private static List<DAI_ActionDefinition> resolveBehavior(String sequenceId) {
        Identifier id = DAI_ActionReference.parse(sequenceId);
        if (id == null) return List.of();
        DAI_ActionDefinition root = DAI_ActionLibrary.get(id);
        if (root == null) return List.of();
        ArrayList<DAI_ActionDefinition> output = new ArrayList<>();
        flatten(root, output, new HashSet<>(), 0);
        return output;
    }

    private static void flatten(
            DAI_ActionDefinition action,
            List<DAI_ActionDefinition> output,
            Set<Identifier> resolving,
            int depth
    ) {
        if (action == null || depth > 32) return;
        if (action.hasSequence()) {
            for (DAI_ActionDefinition child : action.sequence()) flatten(child, output, resolving, depth + 1);
            return;
        }
        if (!action.hasType() && action.hasAction()) {
            Identifier id = DAI_ActionReference.parse(action.action());
            if (id == null || !resolving.add(id)) return;
            try {
                flatten(DAI_ActionLibrary.get(id), output, resolving, depth + 1);
            } finally {
                resolving.remove(id);
            }
            return;
        }
        if (action.hasType()) output.add(action);
    }

    private static boolean entityConditionsPass(
            Mob mob,
            List<DAI_ConditionDefinition> conditions
    ) {
        if (conditions == null || conditions.isEmpty()) return true;
        for (DAI_ConditionDefinition condition : conditions) {
            if (!entityCondition(mob, condition, 0)) return false;
        }
        return true;
    }

    private static boolean entityCondition(
            Mob mob,
            DAI_ConditionDefinition condition,
            int depth
    ) {
        if (mob == null || condition == null || depth > 16) return false;

        boolean result = switch (condition.type()) {
            case "all" -> {
                boolean value = true;
                for (DAI_ConditionDefinition child : condition.conditions()) {
                    if (!entityCondition(mob, child, depth + 1)) {
                        value = false;
                        break;
                    }
                }
                yield value;
            }
            case "any" -> {
                boolean value = false;
                for (DAI_ConditionDefinition child : condition.conditions()) {
                    if (entityCondition(mob, child, depth + 1)) {
                        value = true;
                        break;
                    }
                }
                yield value;
            }
            case "none" -> {
                boolean value = true;
                for (DAI_ConditionDefinition child : condition.conditions()) {
                    if (entityCondition(mob, child, depth + 1)) {
                        value = false;
                        break;
                    }
                }
                yield value;
            }
            case "not" -> condition.conditions().size() == 1
                    && !entityCondition(mob, condition.conditions().getFirst(), depth + 1);
            case "entity_health", "actor_health" -> compareNumber(
                    mob.getHealth(), condition.numberValue(), condition.operator());
            case "entity_health_percent", "actor_health_percent" -> compareNumber(
                    mob.getMaxHealth() <= 0.0F ? 0.0D : mob.getHealth() / mob.getMaxHealth(),
                    condition.numberValue(), condition.operator());
            case "entity_age_ticks", "actor_age_ticks" -> compareNumber(
                    mob.tickCount, condition.numberValue(), condition.operator());
            case "nearest_player_distance", "player_distance" -> {
                Player player = nearestPlayer(mob, 256.0D);
                double distance = player == null ? Double.POSITIVE_INFINITY : mob.distanceTo(player);
                yield compareNumber(distance, condition.numberValue(), condition.operator());
            }
            case "entity_has_target", "actor_has_target" -> compareBoolean(
                    mob.getTarget() != null, condition.booleanValue(), condition.operator());
            case "entity_target_alive", "actor_target_alive" -> compareBoolean(
                    mob.getTarget() != null && mob.getTarget().isAlive(), condition.booleanValue(), condition.operator());
            case "entity_target_distance", "actor_target_distance", "target_distance" -> {
                var target = mob.getTarget();
                double distance = target == null ? Double.POSITIVE_INFINITY : mob.distanceTo(target);
                yield compareNumber(distance, condition.numberValue(), condition.operator());
            }
            case "entity_can_see_target", "actor_can_see_target" -> compareBoolean(
                    mob.getTarget() != null && mob.hasLineOfSight(mob.getTarget()),
                    condition.booleanValue(),
                    condition.operator());
            case "entity_on_ground", "actor_on_ground" -> compareBoolean(
                    mob.onGround(), condition.booleanValue(), condition.operator());
            case "entity_in_water", "actor_in_water" -> compareBoolean(
                    mob.isInWater(), condition.booleanValue(), condition.operator());
            case "random_chance" -> {
                double chance = condition.numberValue();
                if (chance > 1.0D) chance /= 100.0D;
                chance = Math.max(0.0D, Math.min(1.0D, chance));
                yield mob.getRandom().nextDouble() < chance;
            }
            default -> false;
        };

        return condition.negate() ? !result : result;
    }

    private static boolean compareBoolean(boolean actual, boolean expected, String rawOperator) {
        String operator = rawOperator == null || rawOperator.isBlank()
                ? "is_true"
                : rawOperator.trim().toLowerCase();
        return switch (operator) {
            case "is_true" -> actual;
            case "is_false" -> !actual;
            case "equals" -> actual == expected;
            case "not_equals" -> actual != expected;
            default -> false;
        };
    }

    private static boolean compareNumber(double actual, double expected, String rawOperator) {
        String operator = rawOperator == null || rawOperator.isBlank()
                ? "equals"
                : rawOperator.trim().toLowerCase();
        return switch (operator) {
            case "equals" -> Double.compare(actual, expected) == 0;
            case "not_equals" -> Double.compare(actual, expected) != 0;
            case "less_than" -> actual < expected;
            case "less_than_or_equal" -> actual <= expected;
            case "greater_than" -> actual > expected;
            case "greater_than_or_equal" -> actual >= expected;
            default -> false;
        };
    }

    private static void executeActorAction(Mob mob, DAI_ActionDefinition action) {
        String type = action.type().trim().toLowerCase();
        Player player = nearestPlayer(mob, 48.0D);
        var target = mob.getTarget();
        double speed = action.value() > 0.0D ? Math.min(4.0D, action.value()) : 1.0D;

        switch (type) {
            case "move_to", "approach", "follow", "follow_player" -> {
                if (player != null) mob.getNavigation().moveTo(player, speed);
            }
            case "move_to_target", "approach_target", "chase_target" -> {
                if (target != null && target.isAlive()) mob.getNavigation().moveTo(target, speed);
            }
            case "look_at", "look_at_player", "face_player" -> {
                if (player != null) mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
            }
            case "look_at_target", "face_target" -> {
                if (target != null && target.isAlive()) mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
            case "stop", "stop_moving" -> mob.getNavigation().stop();
            case "jump" -> mob.getJumpControl().jump();
            case "target_player", "acquire_player", "attack" -> {
                if (player != null) mob.setTarget(player);
            }
            case "melee_attack", "attack_target" -> {
                if (target == null || !target.isAlive()) {
                    if (player != null) {
                        mob.setTarget(player);
                        target = player;
                    }
                }
                if (target != null && target.isAlive()) {
                    double reach = action.value() > 0.0D ? Math.max(0.25D, action.value()) : 2.25D;
                    if (mob.distanceToSqr(target) <= reach * reach && mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        mob.doHurtTarget(serverLevel, target);
                    }
                }
            }
            case "clear_target" -> mob.setTarget(null);
            case "flee_player", "avoid_player" -> {
                if (player != null) {
                    double dx = mob.getX() - player.getX();
                    double dz = mob.getZ() - player.getZ();
                    double length = Math.sqrt(dx * dx + dz * dz);
                    if (length < 0.001D) {
                        dx = mob.getRandom().nextDouble() - 0.5D;
                        dz = mob.getRandom().nextDouble() - 0.5D;
                        length = Math.sqrt(dx * dx + dz * dz);
                    }
                    double distance = action.ticks() > 0 ? Math.min(32.0D, action.ticks()) : 8.0D;
                    double x = mob.getX() + dx / length * distance;
                    double z = mob.getZ() + dz / length * distance;
                    mob.getNavigation().moveTo(x, mob.getY(), z, speed);
                }
            }
            case "wander" -> {
                var random = mob.getRandom();
                int radius = action.ticks() > 0 ? Math.max(1, Math.min(32, action.ticks())) : 6;
                double x = mob.getX() + random.nextInt(radius * 2 + 1) - radius;
                double z = mob.getZ() + random.nextInt(radius * 2 + 1) - radius;
                mob.getNavigation().moveTo(x, mob.getY(), z, speed);
            }
            case "function", "run_function", "server_run_function" -> {
                if (!action.action().isBlank()) performServerCommand(mob, "function " + action.action());
            }
            case "command", "run_command", "server_command" -> {
                if (!action.action().isBlank()) performServerCommand(mob, action.action());
            }
            case "projectile_spawn", "server_projectile_spawn" ->
                    DAI_ProjectileRuntime.spawn(mob, action.action(), action.arguments());
            case "particle_emit", "server_particle_emit" ->
                    DAI_ParticleRuntime.emit(mob, action.action(), action.arguments());
            case "effect_apply", "server_effect_apply" ->
                    DAI_EffectRuntime.apply(mob, action.action(), action.ticks(), (int)Math.round(action.value()));
            case "effect_remove", "server_effect_remove" ->
                    DAI_EffectRuntime.remove(mob, action.action());
            case "potion_apply", "server_potion_apply" ->
                    DAI_PotionRuntime.apply(mob, action.action());
            case "content_event", "emit_content_event" -> {
                DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(action.action());
                String event = action.arguments().string("event", action.target());
                if (entry != null && !event.isBlank()) DAI_RuntimeDispatch.contentEvent(mob, entry, event);
            }
            case "wait", "idle", "noop" -> { }
            default -> DAI_Core.debug(
                    "<DAI>: Entity behavior skipped unsupported actor action type '{}'.",
                    action.type()
            );
        }
    }

    private static Player nearestPlayer(Mob mob, double radius) {
        return mob.level().getNearestPlayer(mob, radius);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void tickNaturalSpawns(net.minecraft.server.level.ServerLevel level) {
        String dimensionId = level.dimension().identifier().toString();

        for (String idString : DAI_ContentRegistry.ids(DAI_ContentKind.ENTITY)) {
            DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(idString);
            if (entry == null) continue;
            DAI_EntitySpawnSettings spawning = entry.definition().entity().spawning();
            if (!spawning.natural() || serverTicks % spawning.intervalTicks() != 0) continue;
            if (!matchesDimension(dimensionId, spawning.dimensions())) continue;

            Identifier id = Identifier.tryParse(idString);
            EntityType type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(id);
            if (type == null) continue;

            for (var player : level.players()) {
                if (!player.isAlive() || player.isSpectator()) continue;

                AABB capBox = player.getBoundingBox().inflate(spawning.maxRadius());
                int present = level.getEntitiesOfClass(
                        Mob.class,
                        capBox,
                        mob -> mob.isAlive() && mob.getType() == type
                ).size();
                if (present >= spawning.capPerPlayer()) continue;

                for (int attempt = 0;
                     attempt < spawning.attemptsPerPlayer() && present < spawning.capPerPlayer();
                     attempt++) {
                    if (level.getRandom().nextInt(10000) >= spawning.weight()) continue;

                    int group = spawning.minGroup();
                    if (spawning.maxGroup() > spawning.minGroup()) {
                        group += level.getRandom().nextInt(spawning.maxGroup() - spawning.minGroup() + 1);
                    }

                    int spawned = 0;
                    for (int index = 0;
                         index < group && present + spawned < spawning.capPerPlayer();
                         index++) {
                        BlockPos pos = chooseSpawnPos(level, player.blockPosition(), spawning);
                        if (pos == null || !matchesSpawnRules(level, pos, spawning)) continue;

                        try {
                            Mob mob = DAI_EntityTemplateRegistry.create(type, level, entry.definition().carrier());
                            mob.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                            mob.setYRot(level.getRandom().nextFloat() * 360.0F);
                            if (!level.noCollision(mob)) continue;
                            if (level.addFreshEntity(mob)) spawned++;
                        } catch (RuntimeException exception) {
                            DAI_Core.LOGGER.warn("<DAI>: Natural spawn failed for custom entity '{}'.", id, exception);
                            break;
                        }
                    }
                    present += spawned;
                }
            }
        }
    }

    private static BlockPos chooseSpawnPos(
            net.minecraft.server.level.ServerLevel level,
            BlockPos origin,
            DAI_EntitySpawnSettings settings
    ) {
        int radius = settings.minRadius();
        if (settings.maxRadius() > settings.minRadius()) {
            radius += level.getRandom().nextInt(settings.maxRadius() - settings.minRadius() + 1);
        }
        double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
        int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
        int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);

        BlockPos chunkProbe = new BlockPos(x, origin.getY(), z);
        if (!level.hasChunkAt(chunkProbe)) return null;

        return switch (settings.placement()) {
            case "in_water", "water" -> {
                int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) + 1;
                yield new BlockPos(x, y, z);
            }
            case "in_air", "air", "flying" -> randomY(level, x, z, origin.getY(), settings);
            case "underground", "cave", "cave_floor" -> findCaveFloor(level, x, z, origin.getY(), settings);
            case "no_restrictions", "any" -> randomY(level, x, z, origin.getY(), settings);
            case "surface" -> new BlockPos(
                    x,
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
                    z
            );
            default -> {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                // on_ground adapts to the player's context: underground players
                // receive cave-floor attempts rather than always projecting a
                // custom creature onto the surface above them.
                if (origin.getY() + 8 < surface) {
                    BlockPos cave = findCaveFloor(level, x, z, origin.getY(), settings);
                    if (cave != null) yield cave;
                }
                yield new BlockPos(x, surface, z);
            }
        };
    }

    private static BlockPos randomY(
            net.minecraft.server.level.ServerLevel level,
            int x,
            int z,
            int originY,
            DAI_EntitySpawnSettings settings
    ) {
        int min = Math.max(settings.minY(), originY - 16);
        int max = Math.min(settings.maxY(), originY + 16);
        if (max < min) return null;
        int y = min == max ? min : min + level.getRandom().nextInt(max - min + 1);
        return new BlockPos(x, y, z);
    }

    private static BlockPos findCaveFloor(
            net.minecraft.server.level.ServerLevel level,
            int x,
            int z,
            int originY,
            DAI_EntitySpawnSettings settings
    ) {
        int min = Math.max(settings.minY(), originY - 20);
        int max = Math.min(settings.maxY(), originY + 20);
        if (max < min) return null;

        int start = min == max ? min : min + level.getRandom().nextInt(max - min + 1);
        for (int offset = 0; offset <= max - min; offset++) {
            int y = start - offset;
            if (y < min) y = max - (min - y - 1);
            BlockPos candidate = new BlockPos(x, y, z);
            if (isOpenGround(level, candidate)) return candidate;
        }
        return null;
    }

    private static boolean matchesSpawnRules(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos,
            DAI_EntitySpawnSettings settings
    ) {
        if (pos.getY() < settings.minY() || pos.getY() > settings.maxY()) return false;

        // Local raw brightness includes skylight. The old block-light-only
        // check made a max_light=7 hostile rule spawn in full daylight.
        int light = level.getMaxLocalRawBrightness(pos);
        if (light < settings.minLight() || light > settings.maxLight()) return false;

        if (!matchesBiome(level, pos, settings.biomes())) return false;

        return switch (settings.placement()) {
            case "in_water", "water" -> !level.getFluidState(pos).isEmpty();
            case "in_air", "air", "flying" -> level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir();
            case "no_restrictions", "any" -> true;
            default -> isOpenGround(level, pos);
        };
    }

    private static boolean isOpenGround(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos
    ) {
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && !level.getBlockState(pos.below()).isAir()
                && level.getFluidState(pos).isEmpty();
    }

    private static boolean matchesDimension(String dimensionId, List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) return true;
        for (String raw : allowed) {
            if (raw == null || raw.isBlank()) continue;
            if (dimensionId.equals(raw.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean matchesBiome(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos,
            List<String> allowed
    ) {
        if (allowed == null || allowed.isEmpty()) return true;
        var biome = level.getBiome(pos);
        String biomeId = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("");

        for (String raw : allowed) {
            if (raw == null || raw.isBlank()) continue;
            String value = raw.trim().toLowerCase(Locale.ROOT);
            if (value.startsWith("#")) {
                Identifier id = Identifier.tryParse(value.substring(1));
                if (id != null && biome.is(TagKey.create(Registries.BIOME, id))) return true;
            } else if (biomeId.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private record Destination(String dimension, Vec3 target) {}

    private static final class GameplayState {
        private long nextTick;
        private UUID target;
        private boolean mounted;
    }

    private static final class MovementState {
        private long nextTick;
    }

    private static final class BehaviorState {
        private int cursor;
        private long nextTick;
    }

}
