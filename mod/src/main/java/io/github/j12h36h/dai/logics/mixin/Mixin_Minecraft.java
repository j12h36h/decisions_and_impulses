package io.github.j12h36h.dai.logics.mixin;

import io.github.j12h36h.dai.logics.DAI_CreativeInputState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class Mixin_Minecraft {

    /*
     * Minecraft 1.21.9+ moved the global modifier queries from Screen to
     * Minecraft. Keep Ctrl artificially down only for the short window in
     * which vanilla consumes DAI's queued Pick Block key click.
     */
    @Inject(
            method = "hasControlDown",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void dai$forceControlDown(
            CallbackInfoReturnable<Boolean> callback
    ) {

        if (DAI_CreativeInputState.forceControlModifier()) {
            callback.setReturnValue(true);
        }
    }
}
