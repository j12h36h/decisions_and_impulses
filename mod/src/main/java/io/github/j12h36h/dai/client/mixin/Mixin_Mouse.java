package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
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
        if (action != GLFW.GLFW_PRESS
                || rawButtonInfo.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
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
