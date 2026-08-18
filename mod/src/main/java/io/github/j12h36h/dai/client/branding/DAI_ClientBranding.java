package io.github.j12h36h.dai.client.branding;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceRuntime;
import io.github.j12h36h.dai.client.packs.DAI_CompanionResourcePackPreferences;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.experience.DAI_ExperienceRepository;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
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
import java.util.Comparator;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Application-level branding owned by the active/primary MAIN experience.
 *
 * This deliberately uses GLFW for the OS window title/icon so branding stays
 * independent from Mojang's internal Window implementation. The icon is read
 * from the companion resource pack's root pack.png; no image is copied into
 * the DAI mod and versioned resource-pack filenames remain transparent.
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

    /** Applies MAIN branding immediately, including during the startup reload overlay. */
    public static void applyNow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        DAI_ExperienceDefinition experience = preferredExperience();
        if (experience == null) {
            DAI_FmlEarlyBranding.sync(null, null);
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

        // Minecraft may update its own title when connection/screen state
        // changes. Reassert the authored MAIN title periodically so it stays
        // stable for the whole experience, while keeping icon decoding cached.
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
                    "<DAI>: Applied application branding for MAIN experience '{}'.",
                    experience.id()
            );
        }
    }

    public static DAI_ExperienceDefinition preferredExperience() {
        DAI_ExperienceDefinition active = DAI_ExperienceRuntime.active();
        if (active != null) return active;

        return DAI_ExperienceRepository.all().values().stream()
                .filter(DAI_ExperienceDefinition::enabled)
                .max(Comparator
                        .comparingInt(DAI_ExperienceDefinition::priority)
                        .thenComparing(DAI_ExperienceDefinition::id))
                .orElse(null);
    }

    public static DAI_ExperienceDefinition.Branding currentBranding() {
        DAI_ExperienceDefinition experience = preferredExperience();
        return experience == null ? DAI_ExperienceDefinition.Branding.DEFAULT : experience.branding();
    }

    public static String loadingTitle() {
        DAI_ExperienceDefinition experience = preferredExperience();
        if (experience == null) return "Minecraft";
        String authored = experience.branding().loadingTitle();
        return authored.isBlank() ? experience.saveName() : authored;
    }

    public static String loadingSubtitle() {
        DAI_ExperienceDefinition experience = preferredExperience();
        if (experience == null) return "";
        return experience.branding().loadingSubtitle();
    }

    public static Identifier loadingBackgroundTexture() {
        String value = currentBranding().loadingBackgroundTexture();
        return value.isBlank() ? null : Identifier.tryParse(value);
    }

    public static Identifier loadingLogo() {
        String value = currentBranding().loadingLogo();
        return value.isBlank() ? null : Identifier.tryParse(value);
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
