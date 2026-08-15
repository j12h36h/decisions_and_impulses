package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.overlays.DAI_OverlayManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class Mixin_Screen {

    /*
     * Minecraft 1.21.9+ moved the global modifier queries from Screen to
     * Minecraft. DAI's Ctrl-forcing hook therefore lives in Mixin_Minecraft.
     *
     * This mixin is now only responsible for extracting DAI overlay sprites
     * after the screen has produced its normal render state.
     */
    @Inject(
            method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN")
    )
    private void dai$extractOverlaySprites(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callbackInfo
    ) {

        DAI_OverlayManager.extractForScreen(
                graphics
        );
    }
}
