package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.play.DAI_WorldCreationThemeRuntime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds DAI field chrome while preserving Minecraft's EditBox text/input logic. */
@Mixin(EditBox.class)
public abstract class Mixin_EditBox {

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"))
    private void dai$extractWorldCreationEditBoxBackground(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callbackInfo
    ) {
        DAI_WorldCreationThemeRuntime.extractEditBoxBackground((EditBox)(Object)this, graphics);
    }
}
