package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.branding.DAI_ClientBranding;
import io.github.j12h36h.dai.client.branding.DAI_UniverseLoadingRenderer;
import io.github.j12h36h.dai.client.config.DAI_ClientConfig;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paints MAIN-experience branding over Mojang's resource-reload overlay.
 *
 * IMPORTANT: this injection intentionally runs at TAIL and never cancels the
 * vanilla method. In modern Minecraft LoadingOverlay's render-state extraction
 * also participates in reload/fade lifecycle bookkeeping; cancelling it can
 * leave the loading overlay permanently attached even after the reload itself
 * has finished.
 *
 * The startup resource reload can also be rebuilding Minecraft's text shader,
 * so this overlay deliberately uses only texture/fill primitives. Authored
 * title/subtitle text remains available to later safe UI surfaces, while the
 * loading_logo texture is the startup-safe place for branded lettering.
 */
@Mixin(LoadingOverlay.class)
public abstract class Mixin_LoadingOverlay {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("TAIL"),
            require = 0
    )
    private void dai$extractExperienceLoadingScreen(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo callback
    ) {
        DAI_ClientBranding.applyNow();
        DAI_ExperienceDefinition experience = DAI_ClientBranding.preferredExperience();
        DAI_ExperienceDefinition.Branding branding = DAI_ClientBranding.currentBranding();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return;

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (width <= 0 || height <= 0) return;

        // Experience branding always wins. DAI's universe presentation is the
        // low-priority fallback when no Experience supplies its own screen.
        if (experience == null || !branding.customLoadingScreen()) {
            if (!DAI_ClientConfig.loadingScreens()) return;
            DAI_UniverseLoadingRenderer.render(graphics, width, height, DAI_ClientBranding.reloadProgress(this));
            return;
        }

        Identifier background = DAI_ClientBranding.loadingBackgroundTexture();
        if (background != null) {
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    background,
                    0,
                    0,
                    0.0F,
                    0.0F,
                    width,
                    height,
                    width,
                    height,
                    0xFFFFFFFF
            );
        } else {
            graphics.fill(0, 0, width, height, branding.loadingBackground());
        }

        int centerX = width / 2;
        int centerY = height / 2;
        Identifier logo = DAI_ClientBranding.loadingLogo();
        int logoSize = 0;
        int logoTop = centerY - 64;

        if (logo != null && branding.loadingLogoSize() > 0) {
            logoSize = Math.max(16, Math.min(
                    branding.loadingLogoSize(),
                    Math.max(16, Math.min(width, height) / 2)
            ));
            logoTop = centerY - logoSize / 2 - 16;

            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    logo,
                    centerX - logoSize / 2,
                    logoTop,
                    0.0F,
                    0.0F,
                    logoSize,
                    logoSize,
                    logoSize,
                    logoSize,
                    0xFFFFFFFF
            );
        }

        if (branding.showLoadingProgress()) {
            float progress = DAI_ClientBranding.reloadProgress(this);
            if (progress < 0.0F) {
                // Keep the fallback visibly alive without pretending it is a
                // percentage. The real overlay lifecycle is still vanilla.
                progress = (float) ((System.nanoTime() / 1_000_000_000.0D) % 1.0D);
            }

            int barWidth = Math.max(32, Math.min(branding.loadingProgressWidth(), width - 24));
            int barHeight = Math.max(1, branding.loadingProgressHeight());
            int barX = centerX - barWidth / 2;

            int preferredY = logoSize > 0
                    ? logoTop + logoSize + 18
                    : centerY + 34;
            int barY = Math.max(12, Math.min(height - 20, preferredY));

            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x55333333);
            int filled = Math.round(barWidth * Math.max(0.0F, Math.min(1.0F, progress)));
            if (filled > 0) {
                graphics.fill(
                        barX,
                        barY,
                        barX + filled,
                        barY + barHeight,
                        branding.loadingAccent()
                );
            }
        }
    }
}
