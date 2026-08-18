package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.branding.DAI_WorldLoadingBranding;
import io.github.j12h36h.dai.client.overlays.DAI_OverlayManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class Mixin_Screen {

    /*
     * Minecraft 1.21.9+ moved the global modifier queries from Screen to
     * Minecraft. DAI's Ctrl-forcing hook therefore lives in Mixin_Minecraft.
     *
     * This mixin keeps Screen's wrapper lifecycle completely vanilla. DAI may
     * replace only the inner screen-presentation extraction call for a world
     * loading screen; tooltip/subtitle extraction and all wrapper bookkeeping
     * still execute normally before, during, and after world transitions.
     */

    /**
     * Replace only LevelLoadingScreen's visual extraction, never the enclosing
     * Screen.extractRenderStateWithTooltipAndSubtitles wrapper.
     *
     * The previous implementation cancelled that whole wrapper at HEAD. It
     * looked correct while entering a world, but left Minecraft's screen
     * presentation path in an unsafe state for the later disconnect/save ->
     * title handoff. Redirecting the single virtual extractRenderState call
     * gives DAI the same full visual replacement without suppressing any of
     * Minecraft's wrapper work.
     */
    @Redirect(
            method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
            ),
            require = 1
    )
    private void dai$replaceOnlyWorldLoadingPresentation(
            Screen screen,
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (!DAI_WorldLoadingBranding.extractReplacement(screen, graphics)) {
            screen.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

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
        DAI_OverlayManager.extractForScreen(graphics);
    }
}
