package io.github.j12h36h.dai.client.branding;

import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellScreenRouter;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * DAI 4.3's single bootstrap/transition veil.
 *
 * There is intentionally no 3-D promotion stage here. The same engine-owned
 * splash covers Minecraft bootstrap, shell-world creation, shell detach and
 * gameplay-world attachment. It disappears only after the destination world
 * and the appropriate DAI runtime are ready, producing one visual handoff:
 * DAI splash -> fully ready 3-D shell/gameplay.
 */
public final class DAI_SafeLoadingVeil {

    private static volatile boolean active;
    private static volatile boolean autoCompleteOnWorldChange;
    private static volatile Object sourceLevel;
    private static volatile String stage = "LOADING";
    private static volatile long startedNanos;
    private static volatile int readyTicks;
    private static volatile float displayedProgress;

    private DAI_SafeLoadingVeil() {}

    public static void beginBootstrap(String stageText) {
        begin(stageText, false);
    }

    public static void beginWorldTransition(String stageText) {
        begin(stageText, true);
    }

    private static synchronized void begin(String stageText, boolean autoComplete) {
        Minecraft minecraft = Minecraft.getInstance();
        sourceLevel = minecraft == null ? null : minecraft.level;
        stage = stageText == null || stageText.isBlank() ? "LOADING" : stageText.trim();
        autoCompleteOnWorldChange = autoComplete;
        startedNanos = System.nanoTime();
        readyTicks = 0;
        displayedProgress = 0.02F;
        active = true;
    }

    public static synchronized void complete() {
        displayedProgress = 1.0F;
        active = false;
        autoCompleteOnWorldChange = false;
        sourceLevel = null;
        readyTicks = 0;
    }

    public static void cancel() { complete(); }
    public static boolean active() { return active; }
    public static String stage() { return stage; }

    public static void tick() {
        if (!active) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        Screen screen = minecraft.gui == null ? null : minecraft.gui.screen();
        Object currentLevel = minecraft.level;
        float target = progressTarget(screen, currentLevel);
        displayedProgress += (target - displayedProgress) * 0.12F;
        displayedProgress = Math.max(0.0F, Math.min(0.99F, displayedProgress));

        if (!autoCompleteOnWorldChange) return;
        boolean changed = currentLevel != null && minecraft.player != null && currentLevel != sourceLevel;
        if (changed) {
            if (++readyTicks >= 6) complete();
        } else {
            readyTicks = 0;
        }
    }

    public static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        renderIfActive(graphics);
    }

    public static void extractForScreen(GuiGraphicsExtractor graphics) {
        renderIfActive(graphics);
    }

    public static void renderIfActive(GuiGraphicsExtractor graphics) {
        if (graphics == null) return;
        if (DAI_ShellScreenRouter.vanilla(DAI_ShellScreenRouter.SAFE_LOADING)
                || DAI_ShellScreenRouter.none(DAI_ShellScreenRouter.SAFE_LOADING)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return;
        Screen screen = minecraft.gui == null ? null : minecraft.gui.screen();
        boolean bootstrapTitle = !active
                && screen instanceof TitleScreen
                && DAI_ShellWorldRuntime.shouldBootstrapTitle();
        if (!active && !bootstrapTitle) return;

        // LevelLoadingScreen / ReceivingLevelScreen are painted by
        // DAI_WorldLoadingBranding with the exact same splash, avoiding double
        // extraction while keeping one continuous presentation.
        if (isWorldLoadingScreen(screen)) return;

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (width <= 0 || height <= 0) return;
        float progress = bootstrapTitle ? 0.03F : displayedProgress;
        DAI_IntroSplashRenderer.render(graphics, width, height, progress);
    }

    /**
     * Startup-safe hook retained for LoadingOverlay compatibility. Experience
     * branding is deliberately ignored before the DAI launcher exists.
     */
    public static boolean renderExperienceBootstrap(
            GuiGraphicsExtractor graphics,
            int width,
            int height,
            float progress
    ) {
        if (graphics == null || width <= 0 || height <= 0) return false;
        DAI_IntroSplashRenderer.render(graphics, width, height, progress);
        return true;
    }

    private static float progressTarget(Screen screen, Object currentLevel) {
        double seconds = Math.max(0L, System.nanoTime() - startedNanos) / 1_000_000_000.0D;
        float timeProgress = (float)Math.min(0.30D, seconds * 0.035D);
        String name = screen == null ? "" : screen.getClass().getSimpleName();
        if (name.contains("CreateWorld")) return 0.42F + timeProgress * 0.35F;
        if (isWorldLoadingScreen(screen)) return 0.78F;
        if (sourceLevel != null && currentLevel == null) return 0.70F + timeProgress * 0.25F;
        if (currentLevel != null && currentLevel != sourceLevel) return 0.97F;
        if (sourceLevel != null && currentLevel == sourceLevel) return 0.34F + timeProgress * 0.55F;
        return 0.16F + timeProgress;
    }

    private static boolean isWorldLoadingScreen(Screen screen) {
        if (screen == null) return false;
        String name = screen.getClass().getSimpleName();
        return "LevelLoadingScreen".equals(name) || "ReceivingLevelScreen".equals(name);
    }
}
