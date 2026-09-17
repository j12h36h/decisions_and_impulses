package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.client.creator.DAI_CreatorScreen;
import io.github.j12h36h.dai.client.data.DAI_ClientDataBootstrap;
import io.github.j12h36h.dai.client.menus.DAI_GameMenuScreen;
import io.github.j12h36h.dai.client.packs.DAI_PackBrowserScreen;
import io.github.j12h36h.dai.client.play.DAI_ExperienceCreateScreen;
import io.github.j12h36h.dai.client.play.DAI_MinecraftDaiCreateScreen;
import io.github.j12h36h.dai.client.play.DAI_NewSingleplayerScreen;
import io.github.j12h36h.dai.client.play.DAI_PlayScreen;
import io.github.j12h36h.dai.client.play.DAI_WorldLibraryScreen;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellPresentationRepository;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellScreenRouter;
import io.github.j12h36h.dai.client.settings.DAI_SettingsScreen;
import io.github.j12h36h.dai.client.title.DAI_TitleActionDispatcher;
import io.github.j12h36h.dai.client.title.DAI_TitleScreen;
import io.github.j12h36h.dai.client.title.DAI_TitleScreenRepository;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Locale;
import java.util.function.Supplier;

/** Generic JSON-facing presentation operations. */
public final class DAI_PresentationLogic {
    private DAI_PresentationLogic() {}

    /** Opens any Screen subclass using DAI's safe reflective constructor resolver. */
    public static void openScreenClass(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.gui.screen();
        String className = action.arguments().string("class", action.target());
        if (className == null || className.isBlank()) className = action.action();
        if (className == null || className.isBlank()) {
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            return;
        }
        DAI_TitleActionDispatcher.openReflective(parent, className.trim());
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    /**
     * Opens a stable shell lifecycle stage. If JSON has routed the stage to a
     * data screen, that screen wins; Java factories below are only replaceable
     * fallback behavior.
     */
    public static void openShellStage(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.gui.screen();
        String stage = action.arguments().normalized("stage", action.target());
        if (stage.isBlank()) stage = action.action() == null ? "" : action.action().trim().toLowerCase(Locale.ROOT);
        if (stage.isBlank()) {
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
            return;
        }

        Supplier<Screen> fallback = fallback(stage, parent);
        String vanillaClass = action.arguments().string("vanilla_class", vanillaClass(stage));
        Supplier<Screen> vanilla = () -> {
            if (vanillaClass == null || vanillaClass.isBlank()) return parent;
            DAI_TitleActionDispatcher.openReflective(parent, vanillaClass);
            return minecraft.gui.screen();
        };

        DAI_ShellScreenRouter.open(stage, parent, fallback, vanilla);
        DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
    }

    /** Reloads all local JSON presentation data without rebuilding DAI. */
    public static void reloadPresentation(DAI_ActionDefinition action) {
        try {
            DAI_ClientDataBootstrap.reloadLocalData();
            DAI_ShellPresentationRepository.reload();
            DAI_TitleScreenRepository.reload();
            DAI_ActionStatus.set(DAI_ActionResult.SUCCESS);
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.warn("<DAI>: Presentation JSON reload failed.", exception);
            DAI_ActionStatus.set(DAI_ActionResult.FAILURE);
        }
    }

    private static Supplier<Screen> fallback(String stage, Screen parent) {
        return switch (stage) {
            case DAI_ShellScreenRouter.TITLE -> () -> new DAI_TitleScreen(DAI_TitleScreenRepository.current());
            case DAI_ShellScreenRouter.PAUSE -> DAI_GameMenuScreen::new;
            case DAI_ShellScreenRouter.PLAY -> () -> new DAI_PlayScreen(parent);
            case DAI_ShellScreenRouter.PLAY_WORLDS -> () -> new DAI_WorldLibraryScreen(parent);
            case DAI_ShellScreenRouter.SINGLEPLAYER_CREATE -> () -> new DAI_NewSingleplayerScreen(parent);
            case DAI_ShellScreenRouter.MINECRAFT_DAI_CREATE -> () -> new DAI_MinecraftDaiCreateScreen(parent);
            case DAI_ShellScreenRouter.EXPERIENCE_CREATE -> () -> new DAI_ExperienceCreateScreen(parent);
            case DAI_ShellScreenRouter.WORLDS -> () -> new DAI_PackBrowserScreen(parent);
            case DAI_ShellScreenRouter.LIBRARY -> () -> DAI_PackBrowserScreen.library(parent);
            case DAI_ShellScreenRouter.CREATOR -> DAI_CreatorScreen::new;
            case DAI_ShellScreenRouter.SETTINGS -> () -> new DAI_SettingsScreen(parent);
            default -> () -> parent;
        };
    }

    private static String vanillaClass(String stage) {
        return switch (stage) {
            case DAI_ShellScreenRouter.TITLE -> "net.minecraft.client.gui.screens.TitleScreen";
            case DAI_ShellScreenRouter.PAUSE -> "net.minecraft.client.gui.screens.PauseScreen";
            case DAI_ShellScreenRouter.PLAY,
                 DAI_ShellScreenRouter.PLAY_WORLDS,
                 DAI_ShellScreenRouter.SINGLEPLAYER_CREATE,
                 DAI_ShellScreenRouter.WORLDS,
                 DAI_ShellScreenRouter.LIBRARY,
                 DAI_ShellScreenRouter.CREATOR -> "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen";
            case DAI_ShellScreenRouter.SETTINGS -> "net.minecraft.client.gui.screens.options.OptionsScreen";
            default -> "";
        };
    }
}
