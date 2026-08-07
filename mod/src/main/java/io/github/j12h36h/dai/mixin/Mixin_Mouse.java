package io.github.j12h36h.dai.mixin;

import io.github.j12h36h.dai.input.DAI_InputState;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MouseHandler.class)
public abstract class Mixin_Mouse {

    @Inject(
            method = "turnPlayer(D)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dai$turnPlayer(
            double deltaTime,
            CallbackInfo callbackInfo
    ) {

        if (!DAI_InputState.isOverrideEnabled()) {
            return;
        }

        callbackInfo.cancel();
    }
}
