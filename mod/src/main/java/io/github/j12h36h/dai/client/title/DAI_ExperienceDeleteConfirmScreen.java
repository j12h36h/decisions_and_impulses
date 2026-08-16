package io.github.j12h36h.dai.client.title;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
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
    private final String experienceId;
    private final DAI_ExperienceLauncher.ExperienceSave save;
    private final String displayName;

    public DAI_ExperienceDeleteConfirmScreen(
            Screen cancelTarget,
            Screen successTarget,
            String experienceId,
            DAI_ExperienceLauncher.ExperienceSave save,
            String displayName
    ) {
        super(Component.literal("Delete " + displayName));
        this.cancelTarget = cancelTarget;
        this.successTarget = successTarget;
        this.experienceId = experienceId;
        this.save = save;
        this.displayName = displayName;
    }

    @Override
    protected void init() {
        int y = height / 2 + 30;
        addRenderableWidget(Button.builder(
                        Component.literal("CANCEL"),
                        button -> Minecraft.getInstance().gui.setScreen(cancelTarget)
                )
                .bounds(width / 2 - 108, y, 100, 24)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("DELETE"),
                        button -> {
                            if (save != null && DAI_ExperienceLauncher.deleteSave(experienceId, save.saveId())) {
                                Minecraft.getInstance().gui.setScreen(successTarget);
                            }
                        }
                )
                .bounds(width / 2 + 8, y, 100, 24)
                .build());
    }

    @Override
    public void extractRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        DAI_TitleMineShaftRenderer.render(graphics, width, height, 0xFF080706, 0xFF21170E);
        graphics.fill(width / 2 - 176, height / 2 - 72, width / 2 + 176, height / 2 + 70, 0xE20B0907);
        graphics.outline(width / 2 - 176, height / 2 - 72, 352, 142, 0xFF9C6B3C);
        graphics.centeredText(
                font,
                Component.literal("DELETE " + displayName.toUpperCase() + "?"),
                width / 2,
                height / 2 - 43,
                0xFFFFC875
        );
        graphics.centeredText(
                font,
                Component.literal("This permanently deletes this MineShaft save."),
                width / 2,
                height / 2 - 13,
                0xFFE5D7BF
        );
        graphics.centeredText(
                font,
                Component.literal("Equipment, floor progress, and the world cannot be recovered."),
                width / 2,
                height / 2 + 3,
                0xFFBEA98B
        );
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // Custom mine background above.
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(cancelTarget);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
