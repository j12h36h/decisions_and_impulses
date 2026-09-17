package io.github.j12h36h.dai.client.presentation.scene;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Session-level guard for registry-backed scene models.
 *
 * Minecraft 26.2 can expose BuiltInRegistries before item/block component
 * holders are fully bound. Constructing an ItemStack during that window throws
 * "Components not bound yet". DAI therefore keeps the bootstrap presentation
 * texture/fill-only until at least one real client level + player has existed.
 * Once that point is reached, registry components stay bound for the rest of
 * the client session and DAI may safely use block/item GUI models even while a
 * later world transition temporarily detaches the client level.
 */
public final class DAI_SceneRenderSafety {

    private static volatile boolean registryModelsReady;

    private DAI_SceneRenderSafety() {}

    public static void tick() {
        if (registryModelsReady) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.level != null && minecraft.player != null) {
            probeBindings();
        }
    }

    public static void markReady() {
        probeBindings();
    }

    private static void probeBindings() {
        if (registryModelsReady) return;
        try {
            // This is the exact constructor path that crashed before registry
            // components were bound. A successful probe makes the readiness
            // boundary explicit instead of inferring it only from level state.
            new ItemStack(Items.STONE);
            registryModelsReady = true;
        } catch (RuntimeException ignored) {
            // Keep using DAI's bootstrap-safe loader and try again next tick.
        }
    }

    public static boolean registryModelsReady() {
        return registryModelsReady;
    }
}
