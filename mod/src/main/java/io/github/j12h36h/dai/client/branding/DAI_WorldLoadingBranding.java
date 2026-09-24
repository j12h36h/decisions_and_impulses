package io.github.j12h36h.dai.client.branding;

import io.github.j12h36h.dai.client.config.DAI_ClientConfig;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellScreenRouter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * DAI 4.3 world-transition presentation.
 *
 * Experience packs no longer own loading screens. Their unique presentation
 * starts after the gameplay world exists (intro cinematic, camera, HUD, world
 * design, etc.). Until then, DAI's one splash hides Minecraft's internal world
 * switching and shell detach/attach frames.
 */
public final class DAI_WorldLoadingBranding {
    private DAI_WorldLoadingBranding() {}

    public static boolean extractReplacement(Screen screen, GuiGraphicsExtractor graphics) {
        if (screen == null || graphics == null || !DAI_ClientConfig.loadingScreens()) return false;
        if (DAI_ShellScreenRouter.vanilla(DAI_ShellScreenRouter.WORLD_LOADING)
                || DAI_ShellScreenRouter.none(DAI_ShellScreenRouter.WORLD_LOADING)) return false;
        if (!isWorldLoadingScreen(screen)) return false;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return false;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (width <= 0 || height <= 0) return false;

        DAI_IntroSplashRenderer.render(graphics, width, height, progress(screen));
        return true;
    }

    /** Final covering pass for live-level handoff frames. */
    public static void extractPostVeil(Screen screen, GuiGraphicsExtractor graphics) {
        if (screen == null || graphics == null || !DAI_ClientConfig.loadingScreens()) return;
        if (DAI_ShellScreenRouter.vanilla(DAI_ShellScreenRouter.WORLD_LOADING)
                || DAI_ShellScreenRouter.none(DAI_ShellScreenRouter.WORLD_LOADING)) return;
        if (!isWorldLoadingScreen(screen)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (width <= 0 || height <= 0) return;
        DAI_IntroSplashRenderer.render(graphics, width, height, progress(screen));
    }

    private static boolean isWorldLoadingScreen(Screen screen) {
        String name = screen.getClass().getSimpleName();
        return "LevelLoadingScreen".equals(name) || "ReceivingLevelScreen".equals(name);
    }

    /** Best-effort determinate progress when the mapped screen exposes it. */
    private static float progress(Screen screen) {
        if (screen == null) return -1.0F;
        try {
            for (var field : screen.getClass().getDeclaredFields()) {
                if (!field.canAccess(screen) && !field.trySetAccessible()) continue;
                Object value = field.get(screen);
                if (value instanceof Number number) {
                    float f = number.floatValue();
                    if (Float.isFinite(f) && f >= 0.0F && f <= 1.0F) return f;
                }
            }
        } catch (Throwable ignored) { }
        return -1.0F;
    }
}
