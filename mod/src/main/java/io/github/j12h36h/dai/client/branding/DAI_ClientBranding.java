package io.github.j12h36h.dai.client.branding;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceRuntime;
import io.github.j12h36h.dai.client.packs.DAI_CompanionResourcePackPreferences;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Optional OS-window branding for the Experience that owns the currently
 * attached gameplay world. DAI 4.3 never exposes Experience branding during
 * bootstrap or the launcher shell.
 */
public final class DAI_ClientBranding {

    private static String appliedKey = "";
    private static String companionCacheKey = "";
    private static Path companionCache;
    private static long companionCacheUntilMs;
    private static int tickCounter;

    private DAI_ClientBranding() {}

    public static void tick() {
        if (++tickCounter < 20) return;
        tickCounter = 0;
        applyNow();
    }

    /** Applies gameplay-world branding immediately. */
    public static void applyNow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        DAI_ExperienceDefinition experience = preferredExperience();
        if (experience == null) {
            DAI_FmlEarlyBranding.sync(null, null);
            long handle = windowHandle(minecraft);
            if (handle != 0L) {
                GLFW.glfwSetWindowTitle(handle, "D.A.I. Engine");
                if (!appliedKey.isEmpty()) {
                    byte[] icon = readClasspathEntry("logo.png");
                    if (icon != null && icon.length > 0) applyWindowIcon(handle, icon);
                }
            }
            appliedKey = "";
            return;
        }

        DAI_ExperienceDefinition.Branding branding = experience.branding();
        Path companion = resolveCompanion(experience, branding);
        DAI_FmlEarlyBranding.sync(experience, companion);

        long modified = modified(companion);
        String key = experience.id() + "|" + branding.hashCode() + "|"
                + (companion == null ? "" : companion.toAbsolutePath()) + "|" + modified;
        long handle = windowHandle(minecraft);
        if (handle == 0L) return;

        // Minecraft may update its title as gameplay screens change. Reassert
        // the active world owner's title while that Experience remains active.
        if (!branding.windowTitle().isBlank()) {
            GLFW.glfwSetWindowTitle(handle, branding.windowTitle());
        }

        if (!key.equals(appliedKey)) {
            if (branding.useResourcePackIcon() && companion != null) {
                byte[] png = readPackEntry(companion, "pack.png");
                if (png != null && png.length > 0) {
                    applyWindowIcon(handle, png);
                }
            }

            appliedKey = key;
            DAI_Core.LOGGER.info(
                    "<DAI>: Applied gameplay-world branding for experience '{}'.",
                    experience.id()
            );
        }
    }

    public static DAI_ExperienceDefinition preferredExperience() {
        /*
         * DAI 4.3 ownership boundary: an Experience may brand the application
         * only while its gameplay world is actually active. Pending launches,
         * bootstrap and the 3-D launcher shell are always DAI-owned.
         */
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) return null;
        if (DAI_ShellWorldRuntime.isShellActive() || DAI_ShellWorldRuntime.isBootstrapping()) return null;
        return DAI_ExperienceRuntime.active();
    }

    /**
     * Best-effort progress resolver for Mojang's resource reload overlay.
     * Reflection keeps this tiny branding layer resilient to field renames.
     */
    public static float reloadProgress(Object loadingOverlay) {
        if (loadingOverlay == null) return -1.0F;
        try {
            for (var field : loadingOverlay.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(loadingOverlay);
                if (value == null) continue;
                for (String methodName : new String[]{"getActualProgress", "getProgress"}) {
                    try {
                        Method method = value.getClass().getMethod(methodName);
                        Object result = method.invoke(value);
                        if (result instanceof Number number) {
                            float progress = number.floatValue();
                            if (Float.isFinite(progress)) {
                                return Math.max(0.0F, Math.min(1.0F, progress));
                            }
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // Try next candidate.
                    }
                }
            }
        } catch (Exception ignored) {
            // Rendering falls back to an indeterminate bar.
        }
        return -1.0F;
    }

    private static long windowHandle(Minecraft minecraft) {
        try {
            Object window = minecraft.getWindow();
            for (String methodName : new String[]{"getWindow", "handle"}) {
                try {
                    Method method = window.getClass().getMethod(methodName);
                    Object value = method.invoke(window);
                    if (value instanceof Number number) return number.longValue();
                } catch (ReflectiveOperationException ignored) {
                    // Try the next stable name.
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not resolve GLFW window handle.", exception);
        }
        return 0L;
    }

    private static void applyWindowIcon(long handle, byte[] png) {
        ByteBuffer encoded = MemoryUtil.memAlloc(png.length);
        ByteBuffer pixels = null;
        try {
            encoded.put(png).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var width = stack.mallocInt(1);
                var height = stack.mallocInt(1);
                var channels = stack.mallocInt(1);
                pixels = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
                if (pixels == null) {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Could not decode companion pack.png for application icon: {}",
                            STBImage.stbi_failure_reason()
                    );
                    return;
                }

                GLFWImage image = GLFWImage.malloc(stack)
                        .width(width.get(0))
                        .height(height.get(0))
                        .pixels(pixels);
                GLFWImage.Buffer icons = GLFWImage.malloc(1, stack);
                icons.put(0, image);
                GLFW.glfwSetWindowIcon(handle, icons);
            }
        } finally {
            if (pixels != null) STBImage.stbi_image_free(pixels);
            MemoryUtil.memFree(encoded);
        }
    }

    private static byte[] readClasspathEntry(String entryName) {
        if (entryName == null || entryName.isBlank()) return null;
        try (InputStream input = DAI_ClientBranding.class.getClassLoader().getResourceAsStream(entryName)) {
            if (input == null) return null;
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                input.transferTo(output);
                return output.toByteArray();
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not read built-in branding resource '{}'.", entryName, exception);
            return null;
        }
    }

    static byte[] readPackEntry(Path pack, String entryName) {
        try {
            if (Files.isDirectory(pack)) {
                Path file = pack.resolve(entryName);
                return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
            }
            if (!Files.isRegularFile(pack)) return null;
            String lower = pack.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".zip")) return null;

            try (ZipFile zip = new ZipFile(pack.toFile())) {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) return null;
                try (InputStream input = zip.getInputStream(entry);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    input.transferTo(output);
                    return output.toByteArray();
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not read '{}' from companion resource pack '{}'.",
                    entryName,
                    pack,
                    exception
            );
            return null;
        }
    }

    static Path resolveCompanion(
            DAI_ExperienceDefinition experience,
            DAI_ExperienceDefinition.Branding branding
    ) {
        String key = experience.id() + "|" + branding.companionId();
        long now = System.currentTimeMillis();
        if (key.equals(companionCacheKey) && now < companionCacheUntilMs) {
            return companionCache;
        }

        companionCache = DAI_CompanionResourcePackPreferences.findCompanionForExperience(
                experience.id(),
                branding.companionId()
        );
        companionCacheKey = key;
        companionCacheUntilMs = now + 2000L;
        return companionCache;
    }

    private static long modified(Path path) {
        if (path == null) return 0L;
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (Exception ignored) { return 0L; }
    }
}
