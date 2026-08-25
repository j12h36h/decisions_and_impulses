package io.github.j12h36h.dai.client.branding;

import net.minecraft.client.renderer.RenderPipelines;
import io.github.j12h36h.dai.client.config.DAI_ClientConfig;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Lifecycle-safe presentation replacement for Minecraft's world/terrain
 * loading screens. DAI owns only Screen render-state extraction while one of
 * these loading screens is active; world generation, chunk loading, ticking,
 * narration state and completion/removal remain owned by vanilla Minecraft.
 */
public final class DAI_WorldLoadingBranding {

    private DAI_WorldLoadingBranding() {}

    /**
     * Extracts the complete DAI world-loading presentation.
     *
     * @return true when the current screen is owned by DAI and vanilla screen
     *         render extraction should be cancelled for this frame. This only
     *         replaces presentation; Screen tick/progress/completion lifecycle
     *         remains vanilla.
     */
    public static boolean extractReplacement(Screen screen, GuiGraphicsExtractor graphics) {
        if (screen == null || graphics == null) return false;

        /*
         * Cheap first gate: this method is reached from Screen's shared render
         * wrapper, so normal gameplay/title/pause screens must not resolve or
         * scan MAIN-experience branding at all. This is especially important
         * while an integrated server is disconnecting and saving on the way
         * back to the title screen.
         */
        String screenName = screen.getClass().getSimpleName();
        if (!("LevelLoadingScreen".equals(screenName) || "ReceivingLevelScreen".equals(screenName))) {
            return false;
        }

        DAI_ExperienceDefinition experience = DAI_ClientBranding.preferredExperience();
        DAI_ExperienceDefinition.WorldLoading world = experience == null ? null : experience.branding().worldLoading();
        boolean experienceOwns = world != null && world.enabled() && isWorldLoadingScreen(screen, world.includeTransitions());
        boolean daiFallback = DAI_ClientConfig.loadingScreens() && isWorldLoadingScreen(screen, true);
        if (!experienceOwns && !daiFallback) return false;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return false;

        /*
         * Critical live-world handoff guard. Minecraft begins preparing its
         * LevelRenderer as soon as the client level is attached, while the
         * LevelLoadingScreen may remain installed for a few more frames. DAI
         * must not keep cancelling Screen render-state extraction across that
         * boundary: on some OpenGL drivers (notably the tested AMD 760M path)
         * doing so overlaps the custom GUI presentation with the first dynamic
         * chunk/uniform-buffer allocations and can hard-terminate the client.
         *
         * Keep the fully branded screen for the expensive world-generation
         * phase, then let vanilla own the final handoff frames as soon as a
         * live client level exists. Screen ticking/progress/removal was always
         * vanilla; this additionally makes the renderer transition vanilla.
         */
        if (minecraft.level != null) return false;

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (width <= 0 || height <= 0) return false;

        if (!experienceOwns) {
            float p = progress(screen);
            DAI_UniverseLoadingRenderer.render(graphics, width, height, p);
            String stage = "LevelLoadingScreen".equals(screen.getClass().getSimpleName())
                    ? "GENERATING WORLD" : "ENTERING WORLD";
            graphics.centeredText(minecraft.font, Component.literal(stage), width / 2, height / 2 + 54, 0xFFFFA15C);
            return true;
        }

        Identifier background = parse(world.backgroundTexture());
        if (background != null) {
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    background,
                    0,
                    0,
                    0.0F,
                    0.0F,
                    width,
                    height,
                    width,
                    height,
                    0xFFFFFFFF
            );
        } else {
            graphics.fill(0, 0, width, height, world.background());
        }

        int centerX = width / 2;
        int centerY = height / 2;
        Identifier logo = parse(world.logo());
        int logoSize = 0;
        int logoTop = centerY - 82;
        if (logo != null && world.logoSize() > 0) {
            logoSize = Math.max(16, Math.min(world.logoSize(), Math.max(16, Math.min(width, height) / 2)));
            logoTop = centerY - logoSize / 2 - 28;
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    logo,
                    centerX - logoSize / 2,
                    logoTop,
                    0.0F,
                    0.0F,
                    logoSize,
                    logoSize,
                    logoSize,
                    logoSize,
                    0xFFFFFFFF
            );
        }

        int textY = logoSize > 0 ? logoTop + logoSize + 12 : centerY - 18;
        if (world.showStatusText()) {
            String title = world.title().isBlank() ? defaultTitle(screen, experience) : world.title();
            String subtitle = world.subtitle();
            if (!title.isBlank()) {
                graphics.centeredText(
                        minecraft.font,
                        Component.literal(title),
                        centerX,
                        textY,
                        world.foreground()
                );
                textY += minecraft.font.lineHeight + 5;
            }
            if (!subtitle.isBlank()) {
                graphics.centeredText(
                        minecraft.font,
                        Component.literal(subtitle),
                        centerX,
                        textY,
                        withAlpha(world.foreground(), 0xCC)
                );
                textY += minecraft.font.lineHeight + 7;
            }
        }

        if (world.showProgress()) {
            float progress = progress(screen);
            boolean determinate = progress >= 0.0F;
            if (!determinate) {
                progress = (float) ((System.nanoTime() / 1_000_000_000.0D) % 1.0D);
            }

            int barWidth = Math.max(32, Math.min(world.progressWidth(), width - 24));
            int barHeight = Math.max(1, world.progressHeight());
            int barX = centerX - barWidth / 2;
            int barY = Math.max(12, Math.min(height - 20, Math.max(textY + 4, centerY + 52)));
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x55333333);

            if (determinate) {
                int filled = Math.round(barWidth * clamp(progress));
                if (filled > 0) {
                    graphics.fill(barX, barY, barX + filled, barY + barHeight, world.accent());
                }
            } else {
                int segment = Math.max(12, barWidth / 5);
                int travel = Math.max(1, barWidth - segment);
                int start = barX + Math.round(travel * clamp(progress));
                graphics.fill(start, barY, start + segment, barY + barHeight, world.accent());
            }
        }

        return true;
    }

    private static boolean isWorldLoadingScreen(Screen screen, boolean includeTransitions) {
        String name = screen.getClass().getSimpleName();
        if ("LevelLoadingScreen".equals(name)) return true;
        if (!includeTransitions) return false;
        return "ReceivingLevelScreen".equals(name);
    }

    private static String defaultTitle(Screen screen, DAI_ExperienceDefinition experience) {
        String name = screen.getClass().getSimpleName();
        if ("LevelLoadingScreen".equals(name)) return "GENERATING " + experience.saveName().toUpperCase(Locale.ROOT);
        return "ENTERING " + experience.saveName().toUpperCase(Locale.ROOT);
    }

    /** Best-effort extraction; absence simply produces an indeterminate branded bar. */
    private static float progress(Object object) {
        if (object == null) return -1.0F;

        for (String methodName : new String[]{"getProgress", "getPercent", "progress"}) {
            try {
                Method method = object.getClass().getMethod(methodName);
                Object value = method.invoke(object);
                float converted = convertProgress(value);
                if (converted >= 0.0F) return converted;
            } catch (ReflectiveOperationException ignored) {
                // Try fields below.
            }
        }

        Class<?> type = object.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (!(name.contains("progress") || name.contains("percent"))) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    float converted = convertProgress(value);
                    if (converted >= 0.0F) return converted;
                    if (value != null && (name.contains("progress") || name.contains("listener"))) {
                        for (String methodName : new String[]{"getProgress", "getPercent", "progress"}) {
                            try {
                                Method method = value.getClass().getMethod(methodName);
                                converted = convertProgress(method.invoke(value));
                                if (converted >= 0.0F) return converted;
                            } catch (ReflectiveOperationException ignoredNested) {
                                // Try another progress accessor.
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Try another field.
                }
            }
            type = type.getSuperclass();
        }
        return -1.0F;
    }

    private static float convertProgress(Object value) {
        if (!(value instanceof Number number)) return -1.0F;
        float result = number.floatValue();
        if (!Float.isFinite(result) || result < 0.0F) return -1.0F;
        if (result <= 1.0F) return clamp(result);
        if (result <= 100.0F) return clamp(result / 100.0F);
        return -1.0F;
    }

    private static Identifier parse(String value) {
        return value == null || value.isBlank() ? null : Identifier.tryParse(value);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
