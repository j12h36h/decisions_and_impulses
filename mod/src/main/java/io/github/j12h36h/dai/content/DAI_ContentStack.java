package io.github.j12h36h.dai.content;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Resolves both legacy carrier identities and true registry-backed DAI items. */
public final class DAI_ContentStack {

    private DAI_ContentStack() {}

    public static String id(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";

        String explicit = stack.get(DAI_ContentComponents.CONTENT_ID.get());
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim().toLowerCase();
        }

        Identifier nativeId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (nativeId != null && DAI_ContentRegistry.contains(nativeId.toString())) {
            return nativeId.toString().toLowerCase();
        }

        return "";
    }

    public static DAI_ContentRegistry.Entry resolve(ItemStack stack) {
        return DAI_ContentRegistry.get(id(stack));
    }
}
