package io.github.j12h36h.dai.client.title;

import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import io.github.j12h36h.dai.client.presentation.scene.DAI_SceneRenderer;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/** Confirmation gate for permanent experience-save deletion. */
public final class DAI_ExperienceDeleteConfirmScreen extends Screen {

    private final Screen cancelTarget;
    private final Screen successTarget;
    private final DAI_TitleScreenDefinition titleDefinition;
    private final DAI_TitleScreenDefinition.SaveBrowserDefinition browser;
    private final String experienceId;
    private final DAI_ExperienceLauncher.ExperienceSave save;
    private final String displayName;

    public DAI_ExperienceDeleteConfirmScreen(
            Screen cancelTarget,
            Screen successTarget,
            DAI_TitleScreenDefinition titleDefinition,
            String experienceId,
            DAI_ExperienceLauncher.ExperienceSave save,
            String displayName
    ) {
        super(Component.literal("Delete " + displayName));
        this.cancelTarget = cancelTarget;
        this.successTarget = successTarget;
        this.titleDefinition = titleDefinition == null
                ? DAI_TitleScreenDefinition.fallback("decisions_and_impulses:delete_save")
                : titleDefinition;
        this.browser = this.titleDefinition.saveBrowser();
        this.experienceId = experienceId;
        this.save = save;
        this.displayName = displayName;
    }

    @Override
    protected void init() {
        int y = height / 2 + 30;
        var profile = DAI_PresentationProfileService.selected();
        addRenderableWidget(new DAI_UniverseButton(width / 2 - 108, y, 100, 24, Component.literal("CANCEL"),
                button -> Minecraft.getInstance().gui.setScreen(cancelTarget),
                DAI_UniverseButton.Shape.CHIP, profile.secondary()));
        addRenderableWidget(new DAI_UniverseButton(width / 2 + 8, y, 100, 24, Component.literal("DELETE"),
                button -> {
                    if (save != null && DAI_ExperienceLauncher.deleteSave(experienceId, save.saveId())) {
                        Minecraft.getInstance().gui.setScreen(successTarget);
                    }
                }, DAI_UniverseButton.Shape.CHIP, 0xFFFF5F70));
    }

    @Override
    public void extractRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderPackBackground(graphics, partialTick);

        int panelLeft = width / 2 - 176;
        int panelTop = height / 2 - 72;
        graphics.fill(panelLeft, panelTop, panelLeft + 352, panelTop + 142, browser.background());
        graphics.outline(panelLeft, panelTop, 352, 142, browser.border());
        graphics.centeredText(
                font,
                Component.literal("DELETE " + displayName.toUpperCase() + "?"),
                width / 2,
                height / 2 - 43,
                browser.deleteBorder()
        );
        graphics.centeredText(
                font,
                Component.literal(browser.deleteWarning()),
                width / 2,
                height / 2 - 13,
                browser.textColor()
        );
        graphics.centeredText(
                font,
                Component.literal(browser.deleteDetail()),
                width / 2,
                height / 2 + 3,
                browser.mutedColor()
        );
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPackBackground(GuiGraphicsExtractor graphics, float partialTick) {
        if (DAI_Config.featureModuleEnabled("scene_environments") && !titleDefinition.backgroundScene().isBlank()) {
            DAI_SceneRenderer.render(graphics, titleDefinition.backgroundScene(), 0, 0, width, height, partialTick, java.util.Map.of("title.id", titleDefinition.id()));
        } else if ("decisions_and_impulses:default".equals(titleDefinition.id())) {
            DAI_UniverseShellRenderer.render(graphics, width, height, DAI_PresentationProfileService.selected(), System.nanoTime());
        } else {
            graphics.fillGradient(0, 0, width, height, titleDefinition.backgroundTop(), titleDefinition.backgroundBottom());
        }
    }

    @Override
    public void extractBackground(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // This screen draws the owning pack's JSON-configured background.
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(cancelTarget);
    }

    @Override
    public boolean isPauseScreen() {
        return DAI_ShellWorldRuntime.isShellActive();
    }
}
