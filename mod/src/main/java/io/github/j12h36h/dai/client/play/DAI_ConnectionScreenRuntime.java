package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;

import java.lang.reflect.Field;

/** Replaces vanilla disconnected/protocol-error presentation with DAI UI. */
public final class DAI_ConnectionScreenRuntime {

    private static Screen replaced;

    private DAI_ConnectionScreenRuntime() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) return;
        Screen screen = minecraft.gui.screen();
        if (screen == null || screen instanceof DAI_ConnectionLostScreen) return;

        String simple = screen.getClass().getSimpleName();
        if (!"DisconnectedScreen".equals(simple) && !simple.contains("Disconnected")) {
            if (screen != replaced) replaced = null;
            return;
        }
        if (screen == replaced) return;
        replaced = screen;

        String heading = screen.getTitle() == null ? "CONNECTION INTERRUPTED" : screen.getTitle().getString();
        String detail = extractDetail(screen, heading);
        DAI_ShellWorldRuntime.resumeAfterCancelledTransition();
        DAI_Core.LOGGER.warn("<DAI>: Replacing Minecraft disconnect screen '{}' detail='{}'.", heading, detail);
        minecraft.gui.setScreen(new DAI_ConnectionLostScreen(heading, detail));
    }

    private static String extractDetail(Screen screen, String heading) {
        // Minecraft 26.2 stores the actual disconnect reason in a
        // DisconnectionDetails record. Do not scan arbitrary Components here:
        // DisconnectedScreen also contains buttonText (for example
        // "Back to Server List"), which is not the failure reason.
        Class<?> type = screen.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!DisconnectionDetails.class.isAssignableFrom(field.getType())) continue;
                try {
                    if (!field.canAccess(screen) && !field.trySetAccessible()) continue;
                    Object value = field.get(screen);
                    if (!(value instanceof DisconnectionDetails details)) continue;
                    if (details.reason() == null) continue;
                    String text = details.reason().getString();
                    if (text != null && !text.isBlank() && !text.equals(heading)) return text;
                } catch (Throwable ignored) { }
            }
            type = type.getSuperclass();
        }
        return "Minecraft reported a local connection or network protocol error while switching worlds.";
    }
}
