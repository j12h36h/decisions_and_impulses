package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DAI's reserved shell ClientLevel is a registry/runtime host only.
 * The visible launcher is rendered by DAI's scene/UI pipeline, therefore
 * rendering vanilla terrain behind it wastes chunk-section GPU buffers and
 * system commit memory without contributing a single visible pixel.
 */
@Mixin(GameRenderer.class)
public abstract class Mixin_GameRenderer {

    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void dai$skipReservedShellWorldRender(
            DeltaTracker deltaTracker,
            CallbackInfo callback
    ) {
        if (DAI_ShellWorldRuntime.shouldSuppressWorldRender()) {
            callback.cancel();
        }
    }
}
