package io.github.j12h36h.dai.logics.mixin;

import io.github.j12h36h.dai.logics.DAI_CreativeInputState;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class Mixin_Screen {

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
