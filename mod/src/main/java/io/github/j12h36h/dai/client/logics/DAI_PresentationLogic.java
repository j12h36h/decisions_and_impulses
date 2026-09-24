package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.client.creator.DAI_CreatorScreen;
import io.github.j12h36h.dai.client.data.DAI_ClientDataBootstrap;
import io.github.j12h36h.dai.client.menus.DAI_GameMenuScreen;
import io.github.j12h36h.dai.client.navigation.DAI_ControlsMapScreen;
import io.github.j12h36h.dai.client.navigation.DAI_CreatorCategoryScreen;
import io.github.j12h36h.dai.client.navigation.DAI_ServerPortalScreen;
import io.github.j12h36h.dai.client.navigation.DAI_ShellHubScreen;
import io.github.j12h36h.dai.client.play.DAI_ExperienceCreateScreen;
import io.github.j12h36h.dai.client.play.DAI_MinecraftDaiCreateScreen;
import io.github.j12h36h.dai.client.play.DAI_WorldLibraryScreen;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellPresentationRepository;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellScreenRouter;
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

/** Generic JSON-facing presentation operations for DAI's engine-owned shell. */
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

    /** Opens a stable DAI 4.3 launcher stage. */
    public static void openShellStage(DAI_ActionDefinition action) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.gui.screen();
        String stage = action.arguments().normalized("stage", action.target());
        if (stage.isBlank()) stage = action.action() == null ? "" : action.action().trim().toLowerCase(Locale.ROOT);
        stage = DAI_ShellScreenRouter.canonicalStage(stage);
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

    /** Reloads local DAI launcher/presentation data without rebuilding the engine. */
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
            case DAI_ShellScreenRouter.PLAY -> () -> hub(parent, DAI_ShellHubScreen.Hub.PLAY);
            case DAI_ShellScreenRouter.SOLO -> () -> hub(parent, DAI_ShellHubScreen.Hub.SOLO);
            case DAI_ShellScreenRouter.START -> () -> hub(parent, DAI_ShellHubScreen.Hub.START);
            case DAI_ShellScreenRouter.CONNECT -> () -> hub(parent, DAI_ShellHubScreen.Hub.CONNECT);
            case DAI_ShellScreenRouter.CREATE -> () -> hub(parent, DAI_ShellHubScreen.Hub.CREATE);
            case DAI_ShellScreenRouter.CONTENT -> () -> hub(parent, DAI_ShellHubScreen.Hub.CONTENT);
            case DAI_ShellScreenRouter.ASSET -> () -> hub(parent, DAI_ShellHubScreen.Hub.ASSET);
            case DAI_ShellScreenRouter.SETTINGS -> () -> hub(parent, DAI_ShellHubScreen.Hub.SETTINGS);
            case DAI_ShellScreenRouter.OPTIONS -> () -> hub(parent, DAI_ShellHubScreen.Hub.OPTIONS);
            case DAI_ShellScreenRouter.VANILLA -> () -> new DAI_MinecraftDaiCreateScreen(parent);
            case DAI_ShellScreenRouter.LOADED -> () -> new DAI_ExperienceCreateScreen(parent);
            case DAI_ShellScreenRouter.CONTINUE -> () -> new DAI_WorldLibraryScreen(parent);
            case DAI_ShellScreenRouter.PUBLIC -> () -> new DAI_ServerPortalScreen(parent, DAI_ServerPortalScreen.Mode.PUBLIC);
            case DAI_ShellScreenRouter.SAVED -> () -> new DAI_ServerPortalScreen(parent, DAI_ServerPortalScreen.Mode.SAVED);
            case DAI_ShellScreenRouter.CONTROLS -> () -> new DAI_ControlsMapScreen(parent);
            case DAI_ShellScreenRouter.OBJECT -> () -> new DAI_CreatorCategoryScreen(parent, DAI_CreatorCategoryScreen.Category.OBJECT);
            case DAI_ShellScreenRouter.LOGIC -> () -> new DAI_CreatorCategoryScreen(parent, DAI_CreatorCategoryScreen.Category.LOGIC);
            case DAI_ShellScreenRouter.VISUAL -> () -> new DAI_CreatorCategoryScreen(parent, DAI_CreatorCategoryScreen.Category.VISUAL);
            case DAI_ShellScreenRouter.CORE -> () -> new DAI_CreatorCategoryScreen(parent, DAI_CreatorCategoryScreen.Category.CORE);
            case DAI_ShellScreenRouter.AUDIO -> () -> reflective(parent, "net.minecraft.client.gui.screens.options.SoundOptionsScreen");
            case DAI_ShellScreenRouter.DISPLAY -> () -> reflective(parent, "net.minecraft.client.gui.screens.options.VideoSettingsScreen");
            default -> () -> parent;
        };
    }

    private static Screen hub(Screen parent, DAI_ShellHubScreen.Hub hub) {
        return new DAI_ShellHubScreen(parent, hub);
    }

    private static Screen reflective(Screen parent, String className) {
        DAI_TitleActionDispatcher.openReflective(parent, className);
        Screen opened = Minecraft.getInstance().gui.screen();
        return opened == null ? parent : opened;
    }

    private static String vanillaClass(String stage) {
        return switch (stage) {
            case DAI_ShellScreenRouter.TITLE -> "net.minecraft.client.gui.screens.TitleScreen";
            case DAI_ShellScreenRouter.PAUSE -> "net.minecraft.client.gui.screens.PauseScreen";
            case DAI_ShellScreenRouter.VANILLA -> "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen";
            case DAI_ShellScreenRouter.CONTINUE -> "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen";
            case DAI_ShellScreenRouter.SAVED -> "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen";
            case DAI_ShellScreenRouter.AUDIO -> "net.minecraft.client.gui.screens.options.SoundOptionsScreen";
            case DAI_ShellScreenRouter.DISPLAY -> "net.minecraft.client.gui.screens.options.VideoSettingsScreen";
            case DAI_ShellScreenRouter.CONTROLS -> "net.minecraft.client.gui.screens.options.controls.ControlsScreen";
            default -> "";
        };
    }
}
