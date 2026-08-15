package io.github.j12h36h.dai.client.title;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/** Swaps only the vanilla TitleScreen, leaving every destination screen vanilla. */
public final class DAI_TitleScreenController {

    private static boolean replacing;

    private DAI_TitleScreenController() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || replacing) return;

        if (!(minecraft.gui.screen() instanceof TitleScreen)) return;

        try {
            replacing = true;
            DAI_TitleScreenDefinition definition = DAI_TitleScreenRepository.current();
            if (!definition.enabled()) return;

            minecraft.gui.setScreen(new DAI_TitleScreen(definition));
        } catch (Exception exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Failed to replace vanilla title screen; leaving Minecraft's title screen active.",
                    exception
            );
        } finally {
            replacing = false;
        }
    }
}
