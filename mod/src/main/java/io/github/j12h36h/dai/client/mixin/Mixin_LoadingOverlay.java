package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.branding.DAI_ClientBranding;
import io.github.j12h36h.dai.client.branding.DAI_SafeLoadingVeil;
import io.github.j12h36h.dai.client.config.DAI_ClientConfig;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellScreenRouter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Covers Mojang's startup reload with DAI's engine-owned 2-D splash.
 *
 * Experience branding is intentionally unavailable here in 4.3: Experiences
 * own gameplay worlds, not the launcher/bootstrap period. The injection stays
 * at TAIL and never cancels vanilla reload bookkeeping.
 */
@Mixin(LoadingOverlay.class)
public abstract class Mixin_LoadingOverlay {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("TAIL"),
            require = 0
    )
    private void dai$extractDaiBootstrapSplash(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callback
    ) {
        if (!DAI_ClientConfig.loadingScreens()) return;
        if (DAI_ShellScreenRouter.vanilla(DAI_ShellScreenRouter.SAFE_LOADING)
                || DAI_ShellScreenRouter.none(DAI_ShellScreenRouter.SAFE_LOADING)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (width <= 0 || height <= 0) return;

        float progress = DAI_ClientBranding.reloadProgress(this);
        DAI_SafeLoadingVeil.renderExperienceBootstrap(graphics, width, height, progress);
    }
}
