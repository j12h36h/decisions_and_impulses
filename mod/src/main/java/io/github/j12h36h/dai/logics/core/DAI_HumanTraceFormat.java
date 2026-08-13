package io.github.j12h36h.dai.logics.core;

import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.logics.controller.DAI_BreakController;
import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

final class DAI_HumanTraceFormat {

    private DAI_HumanTraceFormat() {
        // Utility class.
    }

    static String context(
            Minecraft minecraft,
            DAI_HumanTrace.InputFrame input
    ) {
        Vec3 position = minecraft.player.position();
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 velocity = minecraft.player.getDeltaMovement();
        Vec3 look = minecraft.player.getLookAngle();

        return "wall_ms=" + System.currentTimeMillis()
                + " game_tick=" + minecraft.level.getGameTime()
                + " day_time=" + minecraft.level.getOverworldClockTime()
                + " dim=" + minecraft.level.dimension().identifier()
                + " pos=(" + vec(position) + ")"
                + " eye=(" + vec(eye) + ")"
                + " vel=(" + vec(velocity) + ")"
                + " yaw=" + number(minecraft.player.getYRot())
                + " pitch=" + number(minecraft.player.getXRot())
                + " head_yaw=" + number(minecraft.player.getYHeadRot())
                + " body_yaw=" + number(minecraft.player.yBodyRot)
                + " look=(" + vec(look) + ")"
                + " ground=" + minecraft.player.onGround()
                + " water=" + minecraft.player.isInWater()
                + " health=" + number(minecraft.player.getHealth())
                + "/" + number(minecraft.player.getMaxHealth())
                + " food=" + minecraft.player.getFoodData().getFoodLevel()
                + " air=" + minecraft.player.getAirSupply()
                + " slot=" + minecraft.player.getInventory().getSelectedSlot()
                + " held=" + item(minecraft.player.getMainHandItem())
                + " offhand=" + item(minecraft.player.getOffhandItem())
                + " input=" + input
                + " crosshair=" + describeHit(minecraft)
                + " screen=" + screenState(minecraft)
                + " raining=" + minecraft.level.isRaining()
                + " thundering=" + minecraft.level.isThundering()
                + " dai_queue=" + DAI_ActionQueue.size()
                + " dai_status=" + DAI_ActionStatus.get()
                + " dai_target=" + DAI_TargetState.selectedBlock()
                + " dai_approach=" + DAI_ApproachController.isActive()
                + " dai_break=" + DAI_BreakController.isActive();
    }

    static String describeHit(Minecraft minecraft) {
        HitResult hitResult = minecraft.hitResult;
        if (hitResult == null) {
            return "null";
        }

        if (hitResult instanceof BlockHitResult blockHit) {
            BlockPos position = blockHit.getBlockPos();
            BlockState state = minecraft.level.getBlockState(position);
            BlockPos adjacent =
                    position.relative(blockHit.getDirection());

            return "BLOCK{pos=" + position
                    + ",id=" + blockId(state)
                    + ",state=" + state
                    + ",face=" + blockHit.getDirection()
                    + ",hit=(" + vec(blockHit.getLocation()) + ")"
                    + ",distance="
                    + number(
                    minecraft.player.getEyePosition()
                            .distanceTo(blockHit.getLocation())
            )
                    + ",adjacent=" + adjacent
                    + ",adjacent_id=" + block(minecraft, adjacent)
                    + "}";
        }

        if (hitResult instanceof EntityHitResult entityHit) {
            return "ENTITY{"
                    + entity(minecraft, entityHit.getEntity())
                    + ",hit=(" + vec(entityHit.getLocation()) + ")"
                    + ",distance="
                    + number(
                    minecraft.player.getEyePosition()
                            .distanceTo(entityHit.getLocation())
            )
                    + "}";
        }

        return hitResult.getType().name()
                + "{hit=(" + vec(hitResult.getLocation()) + ")}";
    }

    static String screenState(Minecraft minecraft) {
        return minecraft.gui.screen() == null
                ? "none"
                : minecraft.gui.screen().getClass().getName();
    }

    static String menuState(Minecraft minecraft) {
        if (minecraft.player == null
                || minecraft.player.containerMenu == null) {
            return "none";
        }

        StringJoiner slots = new StringJoiner(",", "[", "]");
        for (int index = 0;
                index < minecraft.player.containerMenu.slots.size();
                index++) {
            ItemStack stack =
                    minecraft.player.containerMenu.getSlot(index).getItem();
            if (!stack.isEmpty()) {
                slots.add(index + "=" + item(stack));
            }
        }

        return minecraft.player.containerMenu.getClass().getName()
                + "#" + minecraft.player.containerMenu.containerId
                + slots;
    }

    static Map<String, Integer> inventoryTotals(Minecraft minecraft) {
        Map<String, Integer> totals = new LinkedHashMap<>();

        for (int slot = 0;
                slot < minecraft.player.getInventory().getContainerSize();
                slot++) {
            ItemStack stack =
                    minecraft.player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                totals.merge(
                        itemId(stack),
                        stack.getCount(),
                        Integer::sum
                );
            }
        }

        return totals;
    }

    static String inventorySlots(Minecraft minecraft) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");

        for (int slot = 0;
                slot < minecraft.player.getInventory().getContainerSize();
                slot++) {
            ItemStack stack =
                    minecraft.player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                joiner.add(slot + "=" + item(stack));
            }
        }

        return joiner.toString();
    }

    static String inventoryDelta(
            Map<String, Integer> before,
            Map<String, Integer> after
    ) {
        LinkedHashMap<String, Integer> ids = new LinkedHashMap<>();
        before.keySet().forEach(id -> ids.put(id, 0));
        after.keySet().forEach(id -> ids.put(id, 0));

        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (String id : ids.keySet()) {
            int delta = after.getOrDefault(id, 0)
                    - before.getOrDefault(id, 0);
            if (delta != 0) {
                joiner.add(
                        id + ":" + (delta > 0 ? "+" : "") + delta
                );
            }
        }

        return joiner.toString();
    }

    static String inventoryCompact(Map<String, Integer> inventory) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        inventory.forEach(
                (id, count) -> joiner.add(id + "x" + count)
        );
        return joiner.toString();
    }

    static String terrainPatch(Minecraft minecraft) {
        BlockPos center = minecraft.player.blockPosition();
        StringJoiner joiner = new StringJoiner(",", "[", "]");

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int foundDy = -3;
                String found = "minecraft:air";

                for (int dy = 1; dy >= -3; dy--) {
                    BlockPos candidate = center.offset(dx, dy, dz);
                    BlockState state =
                            minecraft.level.getBlockState(candidate);

                    if (!state.isAir()) {
                        foundDy = dy;
                        found = blockId(state);
                        break;
                    }
                }

                joiner.add(
                        dx + ":" + dz + "=" + foundDy + ":" + found
                );
            }
        }

        return joiner.toString();
    }

    static String nearbyLiving(Minecraft minecraft) {
        List<LivingEntity> entities = new ArrayList<>(
                minecraft.level.getEntitiesOfClass(
                        LivingEntity.class,
                        minecraft.player.getBoundingBox().inflate(12.0D),
                        entity -> entity != minecraft.player
                                && entity.isAlive()
                )
        );

        entities.sort(
                Comparator.comparingDouble(
                        entity -> entity.distanceToSqr(minecraft.player)
                )
        );

        StringJoiner joiner = new StringJoiner(",", "[", "]");
        int count = Math.min(6, entities.size());

        for (int index = 0; index < count; index++) {
            joiner.add(entity(minecraft, entities.get(index)));
        }

        return joiner.toString();
    }

    static String entity(Minecraft minecraft, Entity entity) {
        String health = entity instanceof LivingEntity living
                ? ",health="
                + number(living.getHealth())
                + "/"
                + number(living.getMaxHealth())
                : "";

        return "{id=" + entity.getId()
                + ",uuid=" + entity.getUUID()
                + ",type=" + entity.getType()
                + ",pos=(" + vec(entity.position()) + ")"
                + ",distance="
                + number(minecraft.player.distanceTo(entity))
                + health
                + "}";
    }

    static float entityHealth(Entity entity) {
        return entity instanceof LivingEntity living
                ? living.getHealth()
                : Float.NaN;
    }

    static String block(Minecraft minecraft, BlockPos position) {
        return blockId(minecraft.level.getBlockState(position));
    }

    static String blockId(BlockState state) {
        return state.getBlock()
                .builtInRegistryHolder()
                .key()
                .identifier()
                .toString();
    }

    static String item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }

        String durability = stack.isDamageableItem()
                ? "@" + stack.getDamageValue()
                + "/" + stack.getMaxDamage()
                : "";

        return itemId(stack) + "x" + stack.getCount() + durability;
    }

    private static String itemId(ItemStack stack) {
        return stack.getItem()
                .builtInRegistryHolder()
                .key()
                .identifier()
                .toString();
    }

    static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String vec(Vec3 value) {
        if (value == null) {
            return "null";
        }

        return number(value.x)
                + "," + number(value.y)
                + "," + number(value.z);
    }
}
