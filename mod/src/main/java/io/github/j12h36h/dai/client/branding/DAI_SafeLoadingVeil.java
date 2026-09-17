package io.github.j12h36h.dai.client.branding;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellScreenRouter;
import io.github.j12h36h.dai.client.presentation.scene.DAI_SceneRenderer;
import io.github.j12h36h.dai.client.presentation.scene.DAI_SceneRenderSafety;
import io.github.j12h36h.dai.logics.core.DAI_Config;

/**
 * Universal DAI loading veil used to hide Minecraft's intermediary UI while
 * the engine is creating, unloading or switching worlds.
 *
 * The veil intentionally begins with a bootstrap-safe vanilla village splash.
 * LevelLoadingScreen / ReceivingLevelScreen are left to
 * {@link DAI_WorldLoadingBranding}, which promotes the same visual into the
 * real 3-D village once Minecraft has bound registry components.
 */
public final class DAI_SafeLoadingVeil {

    private static volatile boolean active;
    private static volatile boolean autoCompleteOnWorldChange;
    private static volatile Object sourceLevel;
    private static volatile String stage = "LOADING";
    private static volatile long startedNanos;
    private static volatile int readyTicks;
    private static volatile float displayedProgress;
    private static volatile long scenePromotionNanos;

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
        scenePromotionNanos = 0L;
        active = true;
    }

    public static synchronized void complete() {
        displayedProgress = 1.0F;
        active = false;
        autoCompleteOnWorldChange = false;
        sourceLevel = null;
        readyTicks = 0;
        scenePromotionNanos = 0L;
    }

    public static void cancel() {
        complete();
    }

    public static boolean active() {
        return active;
    }

    public static String stage() {
        return stage;
    }

    public static void tick() {
        if (!active) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        Screen screen = minecraft.gui == null ? null : minecraft.gui.screen();
        Object currentLevel = minecraft.level;
        float target = progressTarget(screen, currentLevel);
        displayedProgress += (target - displayedProgress) * 0.12F;
        if (displayedProgress < 0.0F) displayedProgress = 0.0F;
        if (displayedProgress > 0.99F) displayedProgress = 0.99F;

        if (!autoCompleteOnWorldChange) return;

        boolean changed = currentLevel != null
                && minecraft.player != null
                && currentLevel != sourceLevel;
        if (changed) {
            readyTicks++;
            if (readyTicks >= 6) {
                complete();
            }
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

        // Dedicated world-loading branding owns these screens. Keeping this
        // veil off them lets DAI promote to the 3-D implosion once safe.
        if (isWorldLoadingScreen(screen)) return;

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (width <= 0 || height <= 0) return;

        float progress = bootstrapTitle ? 0.03F : displayedProgress;
        if (renderExperienceSafe(graphics, minecraft, width, height, progress)) {
            return;
        }

        renderDefaultLoading(graphics, width, height, progress);
    }

    /**
     * Resource-independent Experience branding used while Minecraft is actively
     * rebuilding the resource manager. Only fill primitives are emitted here;
     * this lets an Experience replace DAI's default bootstrap art without depending
     * on textures or text shaders that may currently be reloading.
     */
    public static boolean renderExperienceBootstrap(
            GuiGraphicsExtractor graphics,
            int width,
            int height,
            float progress
    ) {
        DAI_ExperienceDefinition experience = DAI_ClientBranding.preferredExperience();
        if (experience == null || !experience.branding().customLoadingScreen()) return false;
        DAI_ExperienceDefinition.Branding branding = experience.branding();

        graphics.fill(0, 0, width, height, branding.loadingBackground());
        int accent = branding.loadingAccent();
        int dimAccent = (accent & 0x00FFFFFF) | 0x44000000;
        int faintAccent = (accent & 0x00FFFFFF) | 0x22000000;

        // Experience-owned safe geometry: a framed Rift-style field rather
        // than DAI's default village splash. Packs choose the actual palette.
        graphics.fill(0, 0, width, 2, accent);
        graphics.fill(0, height - 2, width, height, accent);
        graphics.fill(width / 2 - 1, 0, width / 2 + 1, height, faintAccent);
        graphics.fill(0, height / 2 - 1, width, height / 2 + 1, faintAccent);

        int barWidth = Math.max(64, Math.min(branding.loadingProgressWidth(), width - 36));
        int barHeight = Math.max(2, branding.loadingProgressHeight());
        int x = width / 2 - barWidth / 2;
        int y = Math.max(14, height - 34);
        graphics.fill(x, y, x + barWidth, y + barHeight, dimAccent);
        int filled = Math.round(barWidth * Math.max(0.0F, Math.min(1.0F, progress)));
        if (filled > 0) graphics.fill(x, y, x + filled, y + barHeight, accent);
        return true;
    }

    private static boolean renderExperienceSafe(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            int width,
            int height,
            float progress
    ) {
        DAI_ExperienceDefinition experience = DAI_ClientBranding.preferredExperience();
        if (experience == null || !experience.branding().customLoadingScreen()) return false;

        DAI_ExperienceDefinition.Branding branding = experience.branding();
        graphics.fill(0, 0, width, height, branding.loadingBackground());

        Identifier background = DAI_ClientBranding.loadingBackgroundTexture();
        if (resourceAvailable(minecraft, background)) {
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED, background,
                    0, 0, 0.0F, 0.0F, width, height, width, height, 0xFFFFFFFF
            );
        }

        int cx = width / 2;
        int logoSize = 0;
        Identifier logo = DAI_ClientBranding.loadingLogo();
        if (resourceAvailable(minecraft, logo) && branding.loadingLogoSize() > 0) {
            logoSize = Math.max(24, Math.min(branding.loadingLogoSize(), Math.min(width, height) / 3));
            int logoY = Math.max(24, height / 2 - logoSize / 2 - 44);
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED, logo,
                    cx - logoSize / 2, logoY, 0.0F, 0.0F,
                    logoSize, logoSize, logoSize, logoSize, 0xFFFFFFFF
            );
        }

        int textY = height / 2 + (logoSize > 0 ? logoSize / 2 - 26 : -20);
        String title = DAI_ClientBranding.loadingTitle();
        if (title == null || title.isBlank()) title = experience.saveName();
        String subtitle = DAI_ClientBranding.loadingSubtitle();
        if (subtitle == null || subtitle.isBlank()) subtitle = stage();

        graphics.centeredText(minecraft.font, Component.literal(title), cx, textY, branding.loadingForeground());
        graphics.centeredText(
                minecraft.font, Component.literal(subtitle), cx, textY + minecraft.font.lineHeight + 4,
                (branding.loadingForeground() & 0x00FFFFFF) | 0xCC000000
        );

        int barWidth = Math.max(64, Math.min(branding.loadingProgressWidth(), width - 36));
        int barHeight = Math.max(2, branding.loadingProgressHeight());
        int barX = cx - barWidth / 2;
        int barY = Math.min(height - 24, textY + minecraft.font.lineHeight * 2 + 14);
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x55333333);
        int filled = Math.round(barWidth * Math.max(0.0F, Math.min(1.0F, progress)));
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + barHeight, branding.loadingAccent());
        }
        return true;
    }

    private static boolean resourceAvailable(Minecraft minecraft, Identifier id) {
        if (minecraft == null || id == null || minecraft.getResourceManager() == null) return false;
        try {
            return minecraft.getResourceManager().getResource(id).isPresent();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Default DAI transition presentation. Before block/item components are
     * bound, draw only the primitive village splash. Once safe, render the
     * real 3-D village underneath and fade the splash away instead of ever
     * exposing the old universe loader.
     */
    private static void renderDefaultLoading(
            GuiGraphicsExtractor graphics,
            int width,
            int height,
            float progress
    ) {
        boolean renderedScene = false;
        if (DAI_SceneRenderSafety.registryModelsReady()
                && DAI_Config.featureModuleEnabled("scene_environments")) {
            renderedScene = DAI_SceneRenderer.render(
                    graphics,
                    DAI_ShellScreenRouter.scene(
                            DAI_ShellScreenRouter.WORLD_LOADING,
                            "decisions_and_impulses:dai_loading_village"
                    ),
                    0, 0, width, height, 0.0F,
                    java.util.Map.of("loading.progress", progress)
            );
        }

        if (!renderedScene) {
            scenePromotionNanos = 0L;
            DAI_VanillaVillageSplashRenderer.render(graphics, width, height, progress);
            return;
        }

        if (scenePromotionNanos == 0L) scenePromotionNanos = System.nanoTime();
        float splashAlpha = 1.0F - Math.min(1.0F,
                (System.nanoTime() - scenePromotionNanos) / 650_000_000.0F);
        if (splashAlpha > 0.001F) {
            DAI_VanillaVillageSplashRenderer.render(
                    graphics, width, height, progress, splashAlpha
            );
        }
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
