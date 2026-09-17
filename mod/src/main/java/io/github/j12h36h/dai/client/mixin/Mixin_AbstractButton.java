package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.play.DAI_WorldCreationThemeRuntime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DAI visual skin for vanilla buttons while the DAI world creator owns the flow. */
@Mixin(AbstractButton.class)
public abstract class Mixin_AbstractButton {

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void dai$extractWorldCreationButtonBackground(
            GuiGraphicsExtractor graphics,
            CallbackInfo callbackInfo
    ) {
        AbstractButton button = (AbstractButton)(Object)this;
        if (DAI_WorldCreationThemeRuntime.extractButtonBackground(button, graphics)) {
            callbackInfo.cancel();
        }
    }
}
