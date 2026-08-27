package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.logics.input.DAI_MouseState;
import io.github.j12h36h.dai.client.overlays.DAI_OverlayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MouseHandler.class)
public abstract class Mixin_Mouse {

    @Inject(
            method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dai$overlayClick(
            long handle,
            MouseButtonInfo rawButtonInfo,
            int action,
            CallbackInfo callbackInfo
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        /*
         * NeoForge's early display can dispatch GLFW mouse events while the
         * Minecraft client constructor is still running. During that narrow
         * window the vanilla FramerateLimitTracker has not been created yet,
         * but MouseHandler.onButton() unconditionally calls it after this
         * injection returns. Cancel the event until vanilla input is ready so
         * startup clicks cannot produce a non-fatal NullPointerException.
         */
        if (minecraft == null
                || minecraft.getFramerateLimitTracker() == null) {
            callbackInfo.cancel();
            return;
        }

        DAI_MouseState.onButton(rawButtonInfo.button(), action);

        if (action != GLFW.GLFW_PRESS
                || rawButtonInfo.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }

        double mouseX = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        double mouseY = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());

        if (DAI_OverlayManager.handleClick(mouseX, mouseY)) {
            callbackInfo.cancel();
        }
    }

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
