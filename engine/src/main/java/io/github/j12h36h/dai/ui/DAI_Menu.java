package io.github.j12h36h.dai.ui;

import io.github.j12h36h.dai.core.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.awt.*;

public class DAI_Menu extends Screen {

    public DAI_Menu() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        super.init();

        DAI_Layout.Layout layout_menu = DAI_Layout.getLayout(
                DAI_Position.valueOf(Config.MENU_POSITION.get()),
                this.width,
                this.height,
                150,
                20,
                DAI_Layout.DEFAULT_MARGIN,
                DAI_Layout.DEFAULT_MARGIN,
                DAI_Layout.DEFAULT_MARGIN,
                DAI_Layout.DEFAULT_MARGIN
        );

        DAI_Layout.Layout layout_impulse = DAI_Layout.getLayout(
                DAI_Position.valueOf(Config.IMPULSE_POSITION.get()),
                this.width,
                this.height,
                150,
                20,
                DAI_Layout.DEFAULT_MARGIN,
                DAI_Layout.DEFAULT_MARGIN,
                DAI_Layout.DEFAULT_MARGIN,
                DAI_Layout.DEFAULT_MARGIN
        );

        DAI_Layout.Layout layout_decision = DAI_Layout.getLayout(
                DAI_Position.valueOf(Config.DECISION_POSITION.get()),
                this.width,
                this.height,
                150,
                20,
                DAI_Layout.DEFAULT_MARGIN,
                DAI_Layout.DEFAULT_MARGIN,
                DAI_Layout.DEFAULT_MARGIN,
                DAI_Layout.DEFAULT_MARGIN
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.literal("Menu"),
                                button -> Minecraft.getInstance().gui.setScreen(new DAI_Pause(true))
                        )
                        .bounds(
                                layout_menu.x(),
                                layout_menu.y(),
                                layout_menu.width(),
                                layout_menu.height()
                        )
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.literal("Impulses"),
                                button -> {
                                    // TODO: Open impulse screen
                                }
                        )
                        .bounds(
                                layout_impulse.x(),
                                layout_impulse.y(),
                                layout_impulse.width(),
                                layout_impulse.height()
                        )
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.literal("Decisions"),
                                button -> {
                                    // TODO: Open decisions screen
                                }
                        )
                        .bounds(
                                layout_decision.x(),
                                layout_decision.y(),
                                layout_decision.width(),
                                layout_decision.height()
                        )
                        .build()
        );
    }

    @Override
    public void extractRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty.
        // Don't render any background or dark overlay.
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}