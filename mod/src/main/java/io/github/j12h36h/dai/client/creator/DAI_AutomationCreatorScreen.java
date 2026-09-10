package io.github.j12h36h.dai.client.creator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/**
 * Deprecated compatibility entrypoint. The old Automation Creator is now just
 * a route into the schema-driven Creator using the data-defined action schema.
 */
@Deprecated
public final class DAI_AutomationCreatorScreen extends Screen {
    public DAI_AutomationCreatorScreen() {
        super(Component.literal("DAI Creator"));
    }

    @Override
    protected void init() {
        super.init();
        DAI_CreatorRuntime.selectSchema("decisions_and_impulses:action");
        Minecraft.getInstance().gui.setScreen(new DAI_CreatorScreen());
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
}
