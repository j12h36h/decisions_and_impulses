package io.github.j12h36h.dai.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.api.DAI_StateValue;
import io.github.j12h36h.dai.registry.DAI_DynamicRegistryBootstrap;
import io.github.j12h36h.dai.server.runtime.DAI_RuntimeDispatch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Generic persistent runtime data/inventory for JSON-owned DAI blocks. */
public final class DAI_JsonBlockEntity extends BlockEntity implements Container {
    private final Map<String, DAI_StateValue> state = new LinkedHashMap<>();
    private NonNullList<ItemStack> items;

    public DAI_JsonBlockEntity(BlockPos pos, BlockState blockState) {
        super(DAI_DynamicRegistryBootstrap.jsonBlockEntityType(), pos, blockState);
        int slots = settings(blockState).inventorySlots();
        this.items = NonNullList.withSize(slots, ItemStack.EMPTY);
    }

    public DAI_StateValue getState(String key) {
        String normalized = normalize(key);
        return normalized.isEmpty() ? DAI_StateValue.missing() : state.getOrDefault(normalized, DAI_StateValue.missing());
    }

    public boolean containsState(String key) {
        String normalized = normalize(key);
        return !normalized.isEmpty() && state.containsKey(normalized);
    }

    public void setState(String key, DAI_StateValue value) {
        String normalized = normalize(key);
        if (normalized.isEmpty()) return;
        if (value == null || value.isMissing()) state.remove(normalized);
        else state.put(normalized, value);
        changedAndSync();
    }

    public void addNumber(String key, double delta) {
        DAI_StateValue current = getState(key);
        double base = current.type() == DAI_StateValue.Type.NUMBER ? current.numberValue() : 0.0D;
        setState(key, DAI_StateValue.number(base + delta));
    }

    public void toggleBoolean(String key) {
        DAI_StateValue current = getState(key);
        boolean base = current.type() == DAI_StateValue.Type.BOOLEAN && current.booleanValue();
        setState(key, DAI_StateValue.bool(!base));
    }

    public Map<String, DAI_StateValue> stateSnapshot() { return Map.copyOf(state); }

    public static void tick(Level level, BlockPos pos, BlockState blockState, DAI_JsonBlockEntity blockEntity) {
        if (level.isClientSide()) return;
        DAI_BlockSettings settings = settings(blockState);
        int interval = settings.blockEntityTickInterval();
        if (interval <= 0 || level.getGameTime() % interval != 0L) return;
        var id = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
        if (id == null) return;
        var entry = DAI_ContentRegistry.get(id.toString());
        if (entry != null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            DAI_RuntimeDispatch.contentEventAt(serverLevel, pos, entry, "block_entity_tick");
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("dai_state_json", encodeState());
        if (!items.isEmpty()) ContainerHelper.saveAllItems(output, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        decodeState(input.getStringOr("dai_state_json", "{}"));
        int slots = settings(getBlockState()).inventorySlots();
        if (items.size() != slots) items = NonNullList.withSize(slots, ItemStack.EMPTY);
        if (!items.isEmpty()) ContainerHelper.loadAllItems(input, items);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        if (!settings(getBlockState()).blockEntitySync()) return new CompoundTag();
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return settings(getBlockState()).blockEntitySync()
                ? ClientboundBlockEntityDataPacket.create(this)
                : null;
    }

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide() && settings(getBlockState()).blockEntitySync()) {
            BlockState current = getBlockState();
            level.sendBlockUpdated(worldPosition, current, current, 3);
        }
    }

    private String encodeState() {
        JsonObject root = new JsonObject();
        state.forEach((key, value) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("type", value.type().name().toLowerCase(Locale.ROOT));
            switch (value.type()) {
                case BOOLEAN -> entry.addProperty("value", value.booleanValue());
                case NUMBER -> entry.addProperty("value", value.numberValue());
                case STRING -> entry.addProperty("value", value.stringValue());
                case MISSING -> { return; }
            }
            root.add(key, entry);
        });
        return root.toString();
    }

    private void decodeState(String raw) {
        state.clear();
        try {
            var parsed = JsonParser.parseString(raw == null || raw.isBlank() ? "{}" : raw);
            if (!parsed.isJsonObject()) return;
            parsed.getAsJsonObject().entrySet().forEach(entry -> {
                if (!entry.getValue().isJsonObject()) return;
                JsonObject object = entry.getValue().getAsJsonObject();
                String type = object.has("type") ? object.get("type").getAsString() : "string";
                if (!object.has("value")) return;
                DAI_StateValue value = switch (type) {
                    case "boolean" -> DAI_StateValue.bool(object.get("value").getAsBoolean());
                    case "number" -> DAI_StateValue.number(object.get("value").getAsDouble());
                    default -> DAI_StateValue.string(object.get("value").getAsString());
                };
                state.put(normalize(entry.getKey()), value);
            });
        } catch (RuntimeException ignored) {
            state.clear();
        }
    }

    private static DAI_BlockSettings settings(BlockState state) {
        return state != null && state.getBlock() instanceof DAI_JsonBlock block
                ? block.settings()
                : DAI_BlockSettings.DEFAULT;
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack result = slot >= 0 && slot < items.size() ? ContainerHelper.removeItem(items, slot, amount) : ItemStack.EMPTY;
        if (!result.isEmpty()) changedAndSync();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        return slot >= 0 && slot < items.size() ? ContainerHelper.takeItem(items, slot) : ItemStack.EMPTY;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) return;
        items.set(slot, stack == null ? ItemStack.EMPTY : stack);
        changedAndSync();
    }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() {
        for (int index = 0; index < items.size(); index++) items.set(index, ItemStack.EMPTY);
        changedAndSync();
    }
}
