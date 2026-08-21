package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Server lifecycle hooks shared by registry-backed and carrier-backed DAI items. */
public final class DAI_ItemRuntime {
    private static final Map<UUID, Set<String>> EQUIPPED = new HashMap<>();
    private static final EquipmentSlot[] TRACKED_EQUIPMENT = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private DAI_ItemRuntime() {}

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener(DAI_ItemRuntime::rightClickItem);
        NeoForge.EVENT_BUS.addListener(DAI_ItemRuntime::rightClickBlock);
        NeoForge.EVENT_BUS.addListener(DAI_ItemRuntime::interactEntity);
        NeoForge.EVENT_BUS.addListener(DAI_ItemRuntime::attackEntity);
        NeoForge.EVENT_BUS.addListener(DAI_ItemRuntime::useStart);
        NeoForge.EVENT_BUS.addListener(DAI_ItemRuntime::useTick);
        NeoForge.EVENT_BUS.addListener(DAI_ItemRuntime::useStop);
        NeoForge.EVENT_BUS.addListener(DAI_ItemRuntime::useFinish);
        NeoForge.EVENT_BUS.addListener(DAI_ItemRuntime::toss);
        NeoForge.EVENT_BUS.addListener(DAI_ItemRuntime::pickup);
        NeoForge.EVENT_BUS.addListener(DAI_ItemRuntime::serverTick);
    }

    private static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) return;
        fire(event.getEntity(), event.getItemStack(), "use");
    }

    private static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        fire(event.getEntity(), event.getItemStack(), "use_on_block");
    }

    private static void interactEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        fire(event.getEntity(), event.getItemStack(), "interact_entity");
    }

    private static void attackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        fire(event.getEntity(), event.getEntity().getMainHandItem(), "attack_entity");
    }

    private static void useStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity().level().isClientSide()) return;
        fire(event.getEntity(), event.getItem(), "use_start");
    }

    private static void useTick(LivingEntityUseItemEvent.Tick event) {
        if (event.getEntity().level().isClientSide()) return;
        fire(event.getEntity(), event.getItem(), "use_tick");
    }

    private static void useStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity().level().isClientSide()) return;
        fire(event.getEntity(), event.getItem(), "use_release");
    }

    private static void useFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide()) return;
        fire(event.getEntity(), event.getItem(), "use_finish");
    }

    private static void toss(ItemTossEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        fire(event.getPlayer(), event.getEntity().getItem(), "drop");
    }

    private static void pickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer().level().isClientSide()) return;
        fire(event.getPlayer(), event.getOriginalStack(), "pickup");
    }


    private static void serverTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack == null || stack.isEmpty()) continue;
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id == null) continue;
                DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(id.toString());
                if (entry == null || entry.definition().event("inventory_tick").isBlank()) continue;
                int interval = Math.max(1, entry.definition().stats().tickInterval());
                if (player.tickCount % interval == 0) {
                    DAI_RuntimeDispatch.contentEvent(player, entry, "inventory_tick");
                }
            }

            Set<String> now = new HashSet<>();
            for (EquipmentSlot slot : TRACKED_EQUIPMENT) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack == null || stack.isEmpty()) continue;
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id == null) continue;
                DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(id.toString());
                if (entry != null) now.add(id.toString());
            }

            Set<String> before = EQUIPPED.getOrDefault(player.getUUID(), Set.of());
            for (String id : now) {
                if (before.contains(id)) continue;
                DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(id);
                if (entry != null) DAI_RuntimeDispatch.contentEvent(player, entry, "equip");
            }
            for (String id : before) {
                if (now.contains(id)) continue;
                DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(id);
                if (entry != null) DAI_RuntimeDispatch.contentEvent(player, entry, "unequip");
            }
            EQUIPPED.put(player.getUUID(), Set.copyOf(now));
        }
        EQUIPPED.keySet().removeIf(uuid -> event.getServer().getPlayerList().getPlayer(uuid) == null);
    }

    private static void fire(net.minecraft.world.entity.Entity actor, ItemStack stack, String event) {
        if (actor == null || stack == null || stack.isEmpty()) return;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return;
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(id.toString());
        if (entry != null) DAI_RuntimeDispatch.contentEvent(actor, entry, event);
    }
}
