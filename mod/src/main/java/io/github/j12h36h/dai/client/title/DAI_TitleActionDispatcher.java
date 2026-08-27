package io.github.j12h36h.dai.client.title;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.packs.DAI_PackBrowserScreen;
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
            case "launch_experience" -> DAI_ExperienceLauncher.launch(
                    parent,
                    definition.experience()
            );
            case "start_new_experience" -> DAI_ExperienceLauncher.launchNew(
                    parent,
                    definition.experience()
            );
            case "open_experience_creator" -> minecraft.gui.setScreen(
                    new DAI_BoxheadExperienceCreateScreen(parent, definition.experience())
            );
            case "continue_experience" -> DAI_ExperienceLauncher.continueLast(
                    parent,
                    definition.experience()
            );
            case "open_url" -> openExternal(definition.url());
            case "reload_title_json" -> {
                DAI_TitleScreenDefinition refreshed = DAI_TitleScreenRepository.reload();
                minecraft.gui.setScreen(new DAI_TitleScreen(refreshed));
            }
            case "quit" -> {
                DAI_Core.LOGGER.info(
                        "<DAI>: Title-screen quit action requested by button '{}'.",
                        definition.id()
                );
                stopMinecraft();
            }
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

            Constructor<?>[] constructors = rawClass.getDeclaredConstructors();

            // First prefer constructors whose parameters we can resolve exactly.
            if (tryConstruct(constructors, parent, minecraft, false)) {
                return;
            }

            // 26.x moved some vanilla screen constructors away from the older
            // public signatures. A second pass permits safe/default values for
            // optional reference/primitive parameters while still preferring
            // parent, Minecraft and Options whenever their types match.
            if (tryConstruct(constructors, parent, minecraft, true)) {
                return;
            }

            throw new NoSuchMethodException("No compatible constructor found.");
        } catch (Exception exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Failed to open Minecraft screen '{}'.",
                    className,
                    exception
            );
        }
    }

    private static boolean tryConstruct(
            Constructor<?>[] constructors,
            Screen parent,
            Minecraft minecraft,
            boolean allowDefaults
    ) {
        for (Constructor<?> constructor : constructors) {
            Object[] arguments = resolveArguments(
                    constructor.getParameterTypes(),
                    parent,
                    minecraft,
                    allowDefaults
            );
            if (arguments == null) continue;

            try {
                if (!constructor.canAccess(null) && !constructor.trySetAccessible()) {
                    continue;
                }

                Object instance = constructor.newInstance(arguments);
                minecraft.gui.setScreen((Screen) instance);
                return true;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                DAI_Core.debug(
                        "<DAI>: Screen constructor '{}' rejected resolved arguments: {}",
                        constructor,
                        exception.getClass().getSimpleName()
                );
            }
        }

        return false;
    }

    private static Object[] resolveArguments(
            Class<?>[] types,
            Screen parent,
            Minecraft minecraft,
            boolean allowDefaults
    ) {
        Object[] arguments = new Object[types.length];

        for (int index = 0; index < types.length; index++) {
            Class<?> type = types[index];
            Object value = resolveKnownArgument(type, parent, minecraft);

            if (value == null && allowDefaults) {
                value = defaultArgument(type);
            }

            if (value == null && type.isPrimitive()) {
                return null;
            }

            if (value == null && !allowDefaults) {
                return null;
            }

            arguments[index] = value;
        }

        return arguments;
    }

    private static Object resolveKnownArgument(
            Class<?> type,
            Screen parent,
            Minecraft minecraft
    ) {
        if (parent != null && type.isInstance(parent)) {
            return parent;
        }
        if (type.isInstance(minecraft)) {
            return minecraft;
        }
        if (minecraft.options != null && type.isInstance(minecraft.options)) {
            return minecraft.options;
        }
        return null;
    }

    private static Object defaultArgument(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == char.class) return '\0';
        return null;
    }
}
