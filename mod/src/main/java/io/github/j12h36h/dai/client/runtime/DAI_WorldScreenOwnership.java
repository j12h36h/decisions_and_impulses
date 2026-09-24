package io.github.j12h36h.dai.client.runtime;

import io.github.j12h36h.dai.client.screens.data.DAI_DataScreen;
import io.github.j12h36h.dai.client.screens.data.DAI_DataScreenDefinition;
import io.github.j12h36h.dai.client.screens.data.DAI_DataScreenRegistry;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Gives an active Experience ownership of selected gameplay-world UI surfaces
 * without giving it ownership of the DAI launcher.
 */
public final class DAI_WorldScreenOwnership {
    private static Screen replacedSource;

    private DAI_WorldScreenOwnership() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || !DAI_WorldRuntimeContext.isExperience()) {
            replacedSource = null;
            return;
        }

        Screen screen = minecraft.gui.screen();
        if (screen == null || screen instanceof DAI_DataScreen) {
            if (screen != replacedSource) replacedSource = null;
            return;
        }
        if (screen == replacedSource) return;

        DAI_ExperienceDefinition.Ui ui = DAI_WorldRuntimeContext.ui();
        String target = "";
        if (screen instanceof PauseScreen) {
            target = ui.pauseScreen();
        } else {
            String simple = screen.getClass().getSimpleName();
            if ("InventoryScreen".equals(simple)) target = ui.inventoryScreen();
            else if ("DeathScreen".equals(simple)) target = ui.deathScreen();
        }

        if (target == null || target.isBlank()) return;
        DAI_DataScreenDefinition definition = DAI_DataScreenRegistry.get(target);
        if (definition == null) {
            DAI_Core.LOGGER.warn("<DAI>: Experience '{}' requested missing world UI screen '{}'.",
                    DAI_WorldRuntimeContext.experienceId(), target);
            replacedSource = screen;
            return;
        }

        replacedSource = screen;
        minecraft.gui.setScreen(new DAI_DataScreen(target, definition));
        DAI_Core.LOGGER.debug("<DAI>: World runtime '{}' replaced '{}' with '{}'.",
                DAI_WorldRuntimeContext.experienceId(), screen.getClass().getSimpleName(), target);
    }
}
