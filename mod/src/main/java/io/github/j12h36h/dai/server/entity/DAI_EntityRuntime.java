package io.github.j12h36h.dai.server.entity;

import io.github.j12h36h.dai.entity.DAI_EntitySettings;
import io.github.j12h36h.dai.entity.DAI_EntitySpawnSettings;
import io.github.j12h36h.dai.entity.DAI_EntityTemplateRegistry;

import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import io.github.j12h36h.dai.logics.action.DAI_ActionReference;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionDefinition;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime bridge between JSON entity definitions and Minecraft mobs.
 *
 * Vanilla AI remains available by default. A DAI behavior_sequence can layer
 * actor-safe movement/look/target actions over that AI, and natural spawning
 * is evaluated from the entity's JSON rules without requiring generated Java.
 */
public final class DAI_EntityRuntime {

    private static long serverTicks;
    private static final Map<UUID, BehaviorState> BEHAVIOR_STATE = new HashMap<>();
    private static final Set<UUID> INITIALIZED_GAMEPLAY = new HashSet<>();

    private DAI_EntityRuntime() {}

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener(DAI_EntityRuntime::onServerTick);
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
        DAI_Core.LOGGER.info(
                "<DAI>: Custom-entity runtime adopted reloaded behavior/spawn definitions."
        );
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
        double speed = action.value() > 0.0D ? Math.min(4.0D, action.value()) : 1.0D;

        switch (type) {
            case "move_to", "approach", "follow", "follow_player" -> {
                if (player != null) mob.getNavigation().moveTo(player, speed);
            }
            case "look_at", "look_at_player", "face_player" -> {
                if (player != null) mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
            }
            case "stop", "stop_moving" -> mob.getNavigation().stop();
            case "jump" -> mob.getJumpControl().jump();
            case "target_player", "attack" -> {
                if (player != null) mob.setTarget(player);
            }
            case "clear_target" -> mob.setTarget(null);
            case "wander" -> {
                var random = mob.getRandom();
                double x = mob.getX() + random.nextInt(13) - 6;
                double z = mob.getZ() + random.nextInt(13) - 6;
                mob.getNavigation().moveTo(x, mob.getY(), z, speed);
            }
            case "wait", "noop" -> { }
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
        for (String idString : DAI_ContentRegistry.ids(DAI_ContentKind.ENTITY)) {
            DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(idString);
            if (entry == null) continue;
            DAI_EntitySpawnSettings spawning = entry.definition().entity().spawning();
            if (!spawning.natural() || serverTicks % spawning.intervalTicks() != 0) continue;

            Identifier id = Identifier.tryParse(idString);
            EntityType type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(id);
            if (type == null) continue;

            for (var player : level.players()) {
                AABB capBox = player.getBoundingBox().inflate(spawning.maxRadius());
                int present = level.getEntitiesOfClass(
                        Mob.class,
                        capBox,
                        mob -> mob.isAlive() && mob.getType() == type
                ).size();
                if (present >= spawning.capPerPlayer()) continue;

                if (level.getRandom().nextInt(10000) >= spawning.weight()) continue;

                int group = spawning.minGroup();
                if (spawning.maxGroup() > spawning.minGroup()) {
                    group += level.getRandom().nextInt(spawning.maxGroup() - spawning.minGroup() + 1);
                }

                for (int index = 0; index < group && present + index < spawning.capPerPlayer(); index++) {
                    BlockPos pos = chooseSpawnPos(level, player.blockPosition(), spawning);
                    if (pos == null || !matchesSpawnRules(level, pos, spawning)) continue;

                    try {
                        Mob mob = DAI_EntityTemplateRegistry.create(type, level, entry.definition().carrier());
                        mob.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                        mob.setYRot(level.getRandom().nextFloat() * 360.0F);
                        if (!level.noCollision(mob)) continue;
                        level.addFreshEntity(mob);
                    } catch (RuntimeException exception) {
                        DAI_Core.LOGGER.warn("<DAI>: Natural spawn failed for custom entity '{}'.", id, exception);
                        break;
                    }
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

        return switch (settings.placement()) {
            case "in_water", "water" -> {
                int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) + 1;
                yield new BlockPos(x, y, z);
            }
            case "no_restrictions", "any" -> {
                int min = Math.max(settings.minY(), origin.getY() - 12);
                int max = Math.min(settings.maxY(), origin.getY() + 12);
                int y = min >= max ? min : min + level.getRandom().nextInt(max - min + 1);
                yield new BlockPos(x, y, z);
            }
            default -> new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        };
    }

    private static boolean matchesSpawnRules(
            net.minecraft.server.level.ServerLevel level,
            BlockPos pos,
            DAI_EntitySpawnSettings settings
    ) {
        if (pos.getY() < settings.minY() || pos.getY() > settings.maxY()) return false;

        int light = level.getBrightness(LightLayer.BLOCK, pos);
        if (light < settings.minLight() || light > settings.maxLight()) return false;

        if (!matchesBiome(level, pos, settings.biomes())) return false;

        return switch (settings.placement()) {
            case "in_water", "water" -> !level.getFluidState(pos).isEmpty();
            case "no_restrictions", "any" -> true;
            default -> level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir()
                    && !level.getBlockState(pos.below()).isAir();
        };
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
            String value = raw.trim().toLowerCase();
            if (value.startsWith("#")) {
                Identifier id = Identifier.tryParse(value.substring(1));
                if (id != null && biome.is(TagKey.create(Registries.BIOME, id))) return true;
            } else if (biomeId.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static final class BehaviorState {
        private int cursor;
        private long nextTick;
    }

}
