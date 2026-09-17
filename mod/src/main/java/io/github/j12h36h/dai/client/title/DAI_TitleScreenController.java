package io.github.j12h36h.dai.client.title;

import io.github.j12h36h.dai.client.config.DAI_ClientConfig;
import io.github.j12h36h.dai.client.menus.DAI_GameMenuScreen;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellPresentationDefinition;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellScreenRouter;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/** Installs the DAI Engine 4.1 shell over vanilla title and pause entry points. */
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

        /*
         * Full-shell mode owns the normal Esc pause entry point. Other vanilla
         * screens (chat, inventory, mod config, etc.) remain reachable and
         * functional; only the presentation/navigation shell is replaced.
         */
        if (DAI_ClientConfig.fullGameShell()
                && minecraft.level != null
                && minecraft.gui.screen() instanceof PauseScreen pauseScreen) {
            DAI_ShellPresentationDefinition.Route pauseRoute = DAI_ShellScreenRouter.route(DAI_ShellScreenRouter.PAUSE);
            if (DAI_ShellPresentationDefinition.MODE_VANILLA.equals(pauseRoute.mode())) {
                vanillaTitleTicks = 0;
                return;
            }
            try {
                replacing = true;
                minecraft.gui.setScreen(DAI_ShellScreenRouter.resolve(
                        DAI_ShellScreenRouter.PAUSE,
                        pauseScreen,
                        DAI_GameMenuScreen::new,
                        () -> pauseScreen
                ));
            } catch (Exception exception) {
                DAI_Core.LOGGER.error("<DAI>: Failed to install DAI in-game system shell.", exception);
            } finally {
                replacing = false;
            }
            vanillaTitleTicks = 0;
            return;
        }

        if (!(minecraft.gui.screen() instanceof TitleScreen)) {
            vanillaTitleTicks = 0;
            return;
        }

        // In full-shell mode the vanilla title is only a bootstrap waypoint.
        // The shell runtime immediately hides it behind the DAI safe loader and
        // creates/loads the reserved paused world. If that flow is unavailable
        // for this session, the ordinary 2-D DAI title replacement remains the
        // fallback instead of trapping the user on a hidden vanilla screen.
        if (minecraft.level == null && DAI_ShellWorldRuntime.shouldBootstrapTitle()) {
            vanillaTitleTicks = 0;
            return;
        }

        // A world disconnect can install the vanilla title screen while the
        // mouse button that activated "Save and Quit to Title" is still in
        // its release cycle. Do not replace that screen in the same input
        // transition, preventing accidental click-through into Quit Game.
        vanillaTitleTicks++;
        if (vanillaTitleTicks < TITLE_STABLE_TICKS_BEFORE_REPLACE) {
            return;
        }

        try {
            replacing = true;
            DAI_TitleScreenDefinition definition = DAI_TitleScreenRepository.current();
            if (!definition.enabled()) return;

            minecraft.gui.setScreen(DAI_ShellScreenRouter.resolve(
                    DAI_ShellScreenRouter.TITLE,
                    minecraft.gui.screen(),
                    () -> new DAI_TitleScreen(definition),
                    TitleScreen::new
            ));
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
