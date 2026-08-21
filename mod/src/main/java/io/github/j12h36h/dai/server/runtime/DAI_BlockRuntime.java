package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Hot-reloadable lifecycle callbacks for native DAI blocks. */
public final class DAI_BlockRuntime {
    private DAI_BlockRuntime() {}

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener(DAI_BlockRuntime::onPlace);
        NeoForge.EVENT_BUS.addListener(DAI_BlockRuntime::onBreak);
        NeoForge.EVENT_BUS.addListener(DAI_BlockRuntime::onRightClick);
        NeoForge.EVENT_BUS.addListener(DAI_BlockRuntime::onLeftClick);
        NeoForge.EVENT_BUS.addListener(DAI_BlockRuntime::onNeighborNotify);
        NeoForge.EVENT_BUS.addListener(DAI_BlockRuntime::onEntityTick);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        DAI_ContentRegistry.Entry entry = entry(event.getPlacedBlock());
        if (entry == null) return;
        Entity actor = event.getEntity();
        if (actor != null) DAI_RuntimeDispatch.contentEvent(actor, entry, "place");
        else DAI_RuntimeDispatch.contentEventAt(level, event.getPos(), entry, "place");
    }

    private static void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        DAI_ContentRegistry.Entry entry = entry(event.getState());
        if (entry != null) DAI_RuntimeDispatch.contentEvent(event.getPlayer(), entry, "break");
    }

    private static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        DAI_ContentRegistry.Entry entry = entry(level.getBlockState(event.getPos()));
        if (entry != null) DAI_RuntimeDispatch.contentEvent(event.getEntity(), entry, "use");
    }

    private static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        DAI_ContentRegistry.Entry entry = entry(level.getBlockState(event.getPos()));
        if (entry != null) DAI_RuntimeDispatch.contentEvent(event.getEntity(), entry, "attack");
    }

    private static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        DAI_ContentRegistry.Entry entry = entry(event.getState());
        if (entry == null) return;
        DAI_RuntimeDispatch.contentEventAt(level, event.getPos(), entry, "neighbor_changed");
        syncRedstoneState(level, event.getPos(), entry.definition().block().redstoneState());
        if (event.getForceRedstoneUpdate()) {
            DAI_RuntimeDispatch.contentEventAt(level, event.getPos(), entry, "redstone_changed");
        }
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) return;
        BlockPos inside = entity.blockPosition();
        dispatchContact(level, inside, entity, "entity_inside");
        BlockPos below = BlockPos.containing(entity.getX(), entity.getBoundingBox().minY - 0.05D, entity.getZ());
        if (!below.equals(inside)) dispatchContact(level, below, entity, "step");
    }

    private static void dispatchContact(ServerLevel level, BlockPos pos, Entity actor, String event) {
        DAI_ContentRegistry.Entry entry = entry(level.getBlockState(pos));
        if (entry != null) DAI_RuntimeDispatch.contentEvent(actor, entry, event);
    }

    private static DAI_ContentRegistry.Entry entry(BlockState state) {
        if (state == null) return null;
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return null;
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(id.toString());
        return entry != null && entry.kind() == DAI_ContentKind.BLOCK ? entry : null;
    }
    private static void toggleBoolean(ServerLevel level, BlockPos pos, String propertyName) {
        if (propertyName == null || propertyName.isBlank()) return;
        BlockState state = level.getBlockState(pos);
        Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
        if (!(property instanceof BooleanProperty bool)) return;
        level.setBlock(pos, state.setValue(bool, !state.getValue(bool)), 3);
    }

    private static void syncRedstoneState(ServerLevel level, BlockPos pos, String propertyName) {
        if (propertyName == null || propertyName.isBlank()) return;
        BlockState state = level.getBlockState(pos);
        Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
        if (!(property instanceof BooleanProperty bool)) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(bool) != powered) level.setBlock(pos, state.setValue(bool, powered), 3);
    }

}
