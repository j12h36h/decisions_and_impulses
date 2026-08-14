package io.github.j12h36h.dai.title;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.packs.DAI_PackBrowserScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.awt.Desktop;
import java.lang.reflect.Constructor;
import java.net.URI;

/** Keeps title JSON declarative by mapping stable action names to Minecraft screens. */
public final class DAI_TitleActionDispatcher {

    private DAI_TitleActionDispatcher() {}

    public static void run(
            Screen parent,
            DAI_TitleScreenDefinition.ButtonDefinition definition
    ) {
        if (definition == null) return;

        String action = definition.action();
        Minecraft minecraft = Minecraft.getInstance();

        switch (action) {
            case "open_singleplayer" -> openReflective(
                    parent,
                    "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen"
            );
            case "open_multiplayer" -> openReflective(
                    parent,
                    "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen"
            );
            case "open_options" -> openReflective(
                    parent,
                    "net.minecraft.client.gui.screens.options.OptionsScreen"
            );
            case "open_mods" -> openReflective(
                    parent,
                    "net.neoforged.neoforge.client.gui.ModListScreen"
            );
            case "open_official_packs" -> minecraft.gui.setScreen(
                    new DAI_PackBrowserScreen(parent)
            );
            case "open_url" -> openExternal(definition.url());
            case "reload_title_json" -> {
                DAI_TitleScreenDefinition refreshed = DAI_TitleScreenRepository.reload();
                minecraft.gui.setScreen(new DAI_TitleScreen(refreshed));
            }
            case "quit" -> stopMinecraft();
            default -> DAI_Core.LOGGER.warn(
                    "<DAI>: Unknown title-screen action '{}'.",
                    action
            );
        }
    }

    public static void openExternal(String value) {
        if (value == null || value.isBlank()) return;
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    && !"http".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Only HTTP(S) links are allowed.");
            }
            if (!Desktop.isDesktopSupported()) {
                throw new UnsupportedOperationException("Desktop browsing is not supported.");
            }
            Desktop.getDesktop().browse(uri);
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not open external URL '{}'.", value, exception);
        }
    }

    public static void stopMinecraft() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Minecraft.class.getMethod("stop").invoke(minecraft);
        } catch (Exception exception) {
            DAI_Core.LOGGER.error("<DAI>: Could not request Minecraft shutdown.", exception);
        }
    }

    private static void openReflective(Screen parent, String className) {
        Minecraft minecraft = Minecraft.getInstance();

        try {
            Class<?> rawClass = Class.forName(className);
            if (!Screen.class.isAssignableFrom(rawClass)) {
                throw new IllegalStateException(className + " is not a Screen.");
            }

            Constructor<?>[] constructors = rawClass.getConstructors();
            for (Constructor<?> constructor : constructors) {
                Object[] arguments = resolveArguments(constructor.getParameterTypes(), parent, minecraft);
                if (arguments == null) continue;

                Object instance = constructor.newInstance(arguments);
                minecraft.gui.setScreen((Screen) instance);
                return;
            }

            throw new NoSuchMethodException("No compatible public constructor found.");
        } catch (Exception exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Failed to open Minecraft screen '{}'.",
                    className,
                    exception
            );
        }
    }

    private static Object[] resolveArguments(
            Class<?>[] types,
            Screen parent,
            Minecraft minecraft
    ) {
        Object[] arguments = new Object[types.length];

        for (int index = 0; index < types.length; index++) {
            Class<?> type = types[index];
            Object value = null;

            if (parent != null && type.isInstance(parent)) {
                value = parent;
            } else if (type.isInstance(minecraft)) {
                value = minecraft;
            } else if (minecraft.options != null && type.isInstance(minecraft.options)) {
                value = minecraft.options;
            } else if (!type.isPrimitive() && type.isAssignableFrom(Screen.class)) {
                value = parent;
            }

            if (value == null) {
                return null;
            }

            arguments[index] = value;
        }

        return arguments;
    }
}
