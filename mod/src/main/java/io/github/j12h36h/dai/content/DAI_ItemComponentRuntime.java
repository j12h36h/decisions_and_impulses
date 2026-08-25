package io.github.j12h36h.dai.content;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Open runtime bridge to any persistent vanilla/modded item component codec. */
public final class DAI_ItemComponentRuntime {
    private DAI_ItemComponentRuntime() {}

    public static ItemStack resolveStack(Player player, String target) {
        if (player == null) return ItemStack.EMPTY;
        String value = target == null ? "" : target.trim().toLowerCase();
        if (value.isBlank() || value.equals("mainhand") || value.equals("main_hand") || value.equals("held")) {
            return player.getMainHandItem();
        }
        if (value.equals("offhand") || value.equals("off_hand")) return player.getOffhandItem();
        int slot = parseSlot(value);
        if (slot >= 0 && slot < player.getInventory().getContainerSize()) return player.getInventory().getItem(slot);
        return ItemStack.EMPTY;
    }

    public static boolean exists(ItemStack stack, String componentId) {
        DataComponentType<?> type = component(componentId);
        return stack != null && !stack.isEmpty() && type != null && stack.has(type);
    }

    public static String readJson(ItemStack stack, String componentId, HolderLookup.Provider provider) {
        DataComponentType<?> type = component(componentId);
        if (stack == null || stack.isEmpty() || type == null || provider == null) return "";
        return encode(stack, type, provider);
    }

    public static boolean set(ItemStack stack, String componentId, JsonElement json, HolderLookup.Provider provider) {
        DataComponentType<?> type = component(componentId);
        if (stack == null || stack.isEmpty() || type == null || json == null || provider == null) return false;
        return decodeAndSet(stack, type, json, provider);
    }

    public static boolean remove(ItemStack stack, String componentId) {
        DataComponentType<?> type = component(componentId);
        if (stack == null || stack.isEmpty() || type == null) return false;
        if (!stack.has(type)) return true;
        stack.remove(type);
        return true;
    }

    public static boolean copy(ItemStack source, ItemStack target, String componentId) {
        DataComponentType<?> type = component(componentId);
        if (source == null || target == null || source.isEmpty() || target.isEmpty() || type == null) return false;
        return copyTyped(source, target, type);
    }

    private static DataComponentType<?> component(String raw) {
        Identifier id = Identifier.tryParse(raw == null ? "" : raw.trim());
        return id == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String encode(ItemStack stack, DataComponentType type, HolderLookup.Provider provider) {
        Codec codec = type.codec();
        if (codec == null) return "";
        Object value = stack.get(type);
        if (value == null) return "";
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, provider);
        Object encoded = codec.encodeStart(ops, value).result().orElse(null);
        return encoded instanceof JsonElement json ? json.toString() : "";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean decodeAndSet(ItemStack stack, DataComponentType type, JsonElement json, HolderLookup.Provider provider) {
        Codec codec = type.codec();
        if (codec == null) return false;
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, provider);
        Object value = codec.parse(ops, json).result().orElse(null);
        if (value == null) return false;
        stack.set(type, value);
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean copyTyped(ItemStack source, ItemStack target, DataComponentType type) {
        Object value = source.get(type);
        if (value == null) {
            target.remove(type);
            return true;
        }
        target.set(type, value);
        return true;
    }

    private static int parseSlot(String value) {
        int colon = value.indexOf(':');
        if (colon < 0) return -1;
        String prefix = value.substring(0, colon);
        if (!prefix.equals("slot") && !prefix.equals("inventory") && !prefix.equals("hotbar")) return -1;
        try { return Integer.parseInt(value.substring(colon + 1).trim()); }
        catch (NumberFormatException ignored) { return -1; }
    }
}
