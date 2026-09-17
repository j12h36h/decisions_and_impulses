package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/**
 * DAI-owned presentation for Minecraft's experimental-world warning.
 *
 * <p>The original Mojang screen is retained as the controller. Continue invokes
 * its existing affirmative callback; only presentation and DAI navigation are
 * replaced. For a fresh Experience, declining returns to the Experience picker
 * instead of falling through to the vanilla CreateWorldScreen.</p>
 */
public final class DAI_ExperimentalFeaturesScreen extends Screen {

    private final Screen original;
    private final boolean experienceOwned;

    public DAI_ExperimentalFeaturesScreen(Screen original, boolean experienceOwned) {
        super(Component.literal("Experimental World Settings"));
        this.original = original;
        this.experienceOwned = experienceOwned;
    }

    @Override
    protected void init() {
        super.init();
        int panelWidth = Math.min(520, Math.max(250, width - 34));
        int left = width / 2 - panelWidth / 2;
        int buttonWidth = Math.min(170, Math.max(116, (panelWidth - 20) / 2));
        int y = height - 54;

        addRenderableWidget(button(
                left + 6,
                y,
                buttonWidth,
                24,
                experienceOwned ? "BACK TO EXPERIENCES" : "BACK TO WORLD SETUP",
                b -> decline(),
                false
        ));
        addRenderableWidget(button(
                left + panelWidth - buttonWidth - 6,
                y,
                buttonWidth,
                24,
                "CONTINUE ANYWAY",
                b -> proceed(),
                true
        ));
    }

    private DAI_UniverseButton button(
            int x,
            int y,
            int w,
            int h,
            String text,
            Button.OnPress press,
            boolean primary
    ) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        return new DAI_UniverseButton(
                x,
                y,
                w,
                h,
                Component.literal(text),
                press,
                DAI_UniverseButton.Shape.CHIP,
                primary ? profile.primary() : profile.secondary()
        );
    }

    private void proceed() {
        if (DAI_WorldLaunchConfirmation.acceptPresentedAffirmative(original)) {
            DAI_Core.LOGGER.info("<DAI>: Experimental world settings accepted through DAI presentation.");
            return;
        }

        // If a future Minecraft build changes the warning controller shape,
        // expose the original screen rather than silently trapping the player.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gui != null) minecraft.gui.setScreen(original);
        DAI_Core.LOGGER.warn(
                "<DAI>: Experimental warning affirmative callback was unavailable; restored Minecraft's controller screen."
        );
    }

    private void decline() {
        if (experienceOwned && DAI_ExperienceLauncher.cancelPendingFreshToParent()) {
            return;
        }

        if (DAI_WorldLaunchConfirmation.declinePresented(original)) {
            return;
        }

        if (DAI_VanillaWorldScreens.isCreateUiActive()) {
            DAI_VanillaWorldScreens.cancelCreateToSource();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gui != null) minecraft.gui.setScreen(original);
    }

    @Override
    public void onClose() {
        decline();
    }

    @Override
    public void extractRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());

        int panelWidth = Math.min(520, Math.max(250, width - 34));
        int left = width / 2 - panelWidth / 2;
        int top = Math.max(42, height / 6);
        int bottom = height - 68;

        graphics.fill(left, top, left + panelWidth, bottom, 0xD4070A10);
        graphics.outline(left, top, panelWidth, Math.max(1, bottom - top), withAlpha(profile.secondary(), 0x90));
        graphics.fill(left, top, left + 3, bottom, profile.primary());

        graphics.centeredText(font, Component.literal("DAI WORLD CREATION"), width / 2, 12, profile.primary());
        graphics.centeredText(font, Component.literal("EXPERIMENTAL WORLD SETTINGS"), width / 2, 28, profile.text());

        int textX = left + 18;
        int textY = top + 22;
        graphics.text(font, Component.literal("THIS WORLD USES EXPERIMENTAL FEATURES"), textX, textY, profile.primary());
        textY += 22;
        graphics.text(font, Component.literal("Minecraft may change or remove experimental behavior in future versions."), textX, textY, 0xFFC4D1D8);
        textY += 14;
        graphics.text(font, Component.literal("World data created with these settings may not be fully portable."), textX, textY, 0xFFC4D1D8);
        textY += 24;
        graphics.text(font, Component.literal(experienceOwned
                ? "Continue to create this DAI experience, or return to the experience selector."
                : "Continue to create the world, or return to world setup."), textX, textY, 0xFF9FB2BE);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
