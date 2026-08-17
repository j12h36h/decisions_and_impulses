package io.github.j12h36h.dai.client.title;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/** Swaps only the vanilla TitleScreen, leaving every destination screen vanilla. */
public final class DAI_TitleScreenController {

    private static final int TITLE_STABLE_TICKS_BEFORE_REPLACE = 4;

    private static boolean replacing;
    private static int vanillaTitleTicks;

    private DAI_TitleScreenController() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || replacing) return;

        if (!DAI_Config.customTitleScreens()) {
            vanillaTitleTicks = 0;
            return;
        }

        if (!(minecraft.gui.screen() instanceof TitleScreen)) {
            vanillaTitleTicks = 0;
            return;
        }

        // A world disconnect can install the vanilla title screen while the
        // mouse button that activated "Save and Quit to Title" is still in
        // its release cycle.  Do not replace that screen in the same input
        // transition.  MineTrigger (and any other JSON title) may place its
        // own Quit button in the same region, which can otherwise turn an
        // ordinary world exit into a clean Minecraft.stop() shutdown.
        vanillaTitleTicks++;
        if (vanillaTitleTicks < TITLE_STABLE_TICKS_BEFORE_REPLACE) {
            return;
        }

        try {
            replacing = true;
            DAI_TitleScreenDefinition definition = DAI_TitleScreenRepository.current();
            if (!definition.enabled()) return;

            minecraft.gui.setScreen(new DAI_TitleScreen(definition));
            vanillaTitleTicks = 0;
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
