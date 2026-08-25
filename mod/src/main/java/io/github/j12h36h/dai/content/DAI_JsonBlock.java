package io.github.j12h36h.dai.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.core.registries.BuiltInRegistries;
import io.github.j12h36h.dai.server.runtime.DAI_RuntimeDispatch;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import io.github.j12h36h.dai.registry.DAI_DynamicRegistryBootstrap;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generic physical Block used by registry-backed DAI blocks when JSON requests
 * state properties, custom shapes, ladder behavior or redstone output.
 *
 * Block's constructor asks subclasses to build their StateDefinition before a
 * normal instance field can be initialized, so construction settings are passed
 * through a short-lived ThreadLocal. Registration is single-threaded on the mod
 * bus and the value is cleared in finally by create().
 */
public final class DAI_JsonBlock extends Block implements EntityBlock {
    private static final ThreadLocal<DAI_BlockSettings> CONSTRUCTION = new ThreadLocal<>();

    private final DAI_BlockSettings settings;

    private DAI_JsonBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.settings = current();
    }

    public static DAI_JsonBlock create(BlockBehaviour.Properties properties, DAI_BlockSettings settings) {
        CONSTRUCTION.set(settings == null ? DAI_BlockSettings.DEFAULT : settings);
        try {
            return new DAI_JsonBlock(properties);
        } finally {
            CONSTRUCTION.remove();
        }
    }

    private static DAI_BlockSettings current() {
        DAI_BlockSettings value = CONSTRUCTION.get();
        return value == null ? DAI_BlockSettings.DEFAULT : value;
    }

    public DAI_BlockSettings settings() { return settings; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return settings.blockEntity() ? new DAI_JsonBlockEntity(pos, state) : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        BlockEntityType<DAI_JsonBlockEntity> expected = DAI_DynamicRegistryBootstrap.jsonBlockEntityType();
        if (!settings.blockEntity() || expected == null || type != expected) return null;
        return (BlockEntityTicker) (BlockEntityTicker<DAI_JsonBlockEntity>) DAI_JsonBlockEntity::tick;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        DAI_BlockSettings config = current();
        for (String raw : config.states()) {
            Property<?> property = property(raw);
            if (property != null) builder.add(property);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!settings.hasOutlineShape()) return super.getShape(state, level, pos, context);
        return shape(settings.outlineShape());
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (settings.noCollision()) return net.minecraft.world.phys.shapes.Shapes.empty();
        if (!settings.hasCollisionShape()) return super.getCollisionShape(state, level, pos, context);
        return shape(settings.collisionShape());
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return settings.redstoneSignal() > 0 || super.isSignalSource(state);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return settings.redstoneSignal() > 0
                ? settings.redstoneSignal()
                : super.getSignal(state, level, pos, direction);
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return settings.climbable() || super.isLadder(state, level, pos, entity);
    }

    private static VoxelShape shape(List<Double> box) {
        return Block.box(box.get(0), box.get(1), box.get(2), box.get(3), box.get(4), box.get(5));
    }

    /**
     * Supported syntax:
     *   powered / lit / open / waterlogged / enabled
     *   facing / horizontal_facing / axis
     *   boolean:<name>
     *   int:<name>:<min>:<max>
     *
     * Defaults are Minecraft's first legal value; authored packs can select a
     * different value in blockstate strings (setblock ...[powered=true]).
     */
    private static Property<?> property(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "powered" -> BlockStateProperties.POWERED;
            case "lit" -> BlockStateProperties.LIT;
            case "open" -> BlockStateProperties.OPEN;
            case "waterlogged" -> BlockStateProperties.WATERLOGGED;
            case "enabled" -> BooleanProperty.create("enabled");
            case "facing" -> BlockStateProperties.FACING;
            case "horizontal_facing" -> BlockStateProperties.HORIZONTAL_FACING;
            case "axis" -> BlockStateProperties.AXIS;
            default -> customProperty(value);
        };
    }

    private static Property<?> customProperty(String value) {
        String[] parts = value.split(":");
        try {
            if (parts.length == 2 && parts[0].equals("boolean")) {
                return BooleanProperty.create(parts[1]);
            }
            if (parts.length == 4 && parts[0].equals("int")) {
                return IntegerProperty.create(parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            }
        } catch (RuntimeException ignored) {
            // Invalid JSON vocabulary is ignored during registry construction;
            // datapack validation can report it without crashing bootstrap.
        }
        return null;
    }
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var id = BuiltInRegistries.BLOCK.getKey(this);
        if (id != null) {
            var entry = DAI_ContentRegistry.get(id.toString());
            if (entry != null) DAI_RuntimeDispatch.contentEventAt(level, pos, entry, "random_tick");
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var id = BuiltInRegistries.BLOCK.getKey(this);
        if (id != null) {
            var entry = DAI_ContentRegistry.get(id.toString());
            if (entry != null) DAI_RuntimeDispatch.contentEventAt(level, pos, entry, "scheduled_tick");
        }
        if (settings.scheduledTickDelay() > 0) {
            level.scheduleTick(pos, this, settings.scheduledTickDelay());
        }
    }

}
