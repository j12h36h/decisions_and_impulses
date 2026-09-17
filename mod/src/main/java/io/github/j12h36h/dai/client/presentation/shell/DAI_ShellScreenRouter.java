package io.github.j12h36h.dai.client.presentation.shell;

import io.github.j12h36h.dai.client.screens.data.DAI_DataScreen;
import io.github.j12h36h.dai.client.screens.data.DAI_DataScreenDefinition;
import io.github.j12h36h.dai.client.screens.data.DAI_DataScreenRegistry;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Supplier;

/** Central stage router used by the default DAI shell and pack-defined replacements. */
public final class DAI_ShellScreenRouter {
    public static final String SAFE_LOADING = "safe_loading";
    public static final String TITLE = "title";
    public static final String PAUSE = "pause";
    public static final String PLAY = "play";
    public static final String PLAY_WORLDS = "play_worlds";
    public static final String SINGLEPLAYER_CREATE = "singleplayer_create";
    public static final String MINECRAFT_DAI_CREATE = "minecraft_dai_create";
    public static final String EXPERIENCE_CREATE = "experience_create";
    public static final String EXPERIENCE_SETUP = "experience_setup";
    public static final String ADDON_SELECTION = "addon_selection";
    public static final String WORLD_OPTIONS = "world_options";
    public static final String LAUNCH_TRANSITION = "launch_transition";
    public static final String WORLDS = "worlds";
    public static final String LIBRARY = "library";
    public static final String CREATOR = "creator";
    public static final String SETTINGS = "settings";
    public static final String WORLD_LOADING = "world_loading";
    public static final String RETURN_TO_SHELL = "return_to_shell";

    private DAI_ShellScreenRouter() {}

    public static DAI_ShellPresentationDefinition.Route route(String stage) {
        return DAI_ShellPresentationRepository.current().route(stage);
    }

    public static boolean vanilla(String stage) {
        return DAI_ShellPresentationRepository.current().vanilla(stage);
    }

    public static boolean none(String stage) {
        return DAI_ShellPresentationRepository.current().none(stage);
    }

    public static String scene(String stage, String fallback) {
        String authored = route(stage).scene();
        return authored.isBlank() ? fallback : authored;
    }

    public static Screen resolve(
            String stage,
            Screen parent,
            Supplier<Screen> defaultFactory,
            Supplier<Screen> vanillaFactory
    ) {
        DAI_ShellPresentationDefinition.Route route = route(stage);
        return switch (route.mode()) {
            case DAI_ShellPresentationDefinition.MODE_VANILLA -> safeGet(vanillaFactory, parent);
            case DAI_ShellPresentationDefinition.MODE_NONE -> null;
            case DAI_ShellPresentationDefinition.MODE_DATA_SCREEN -> dataScreen(
                    route.screen(), parent, defaultFactory, vanillaFactory
            );
            default -> safeGet(defaultFactory, parent);
        };
    }

    public static void open(
            String stage,
            Screen parent,
            Supplier<Screen> defaultFactory,
            Supplier<Screen> vanillaFactory
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) return;
        minecraft.gui.setScreen(resolve(stage, parent, defaultFactory, vanillaFactory));
    }

    private static Screen dataScreen(
            String id,
            Screen parent,
            Supplier<Screen> defaultFactory,
            Supplier<Screen> vanillaFactory
    ) {
        DAI_DataScreenDefinition definition = DAI_DataScreenRegistry.get(id);
        if (definition != null) return new DAI_DataScreen(id, definition);
        if (id != null && !id.isBlank()) {
            DAI_Core.LOGGER.warn("<DAI>: Shell route requested missing data screen '{}'; using presentation fallback.", id);
        }

        String fallbackMode = DAI_ShellPresentationRepository.current().fallback();
        return switch (fallbackMode) {
            case DAI_ShellPresentationDefinition.MODE_VANILLA -> safeGet(vanillaFactory, parent);
            case DAI_ShellPresentationDefinition.MODE_NONE -> null;
            default -> safeGet(defaultFactory, parent);
        };
    }

    private static Screen safeGet(Supplier<Screen> supplier, Screen fallback) {
        if (supplier == null) return fallback;
        try {
            Screen result = supplier.get();
            return result == null ? fallback : result;
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.warn("<DAI>: Shell route factory failed; keeping current/parent screen.", exception);
            return fallback;
        }
    }
}
