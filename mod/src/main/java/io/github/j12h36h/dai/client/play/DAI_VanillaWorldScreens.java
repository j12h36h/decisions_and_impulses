package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.branding.DAI_SafeLoadingVeil;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.client.title.DAI_TitleActionDispatcher;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.runtime.DAI_StandaloneLaunchState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reflection bridge/watchdog for Minecraft 26.x world creation.
 * Mojang remains the controller; DAI_WorldCreationThemeRuntime supplies the presentation.
 */
public final class DAI_VanillaWorldScreens {

    private static boolean createPending;
    private static boolean createScreenSeen;
    private static boolean transitionVeilStarted;
    private static Object sourceLevel;
    private static Screen sourceParent;
    private static Screen lastPrompt;
    private static int promptTicks;
    private static int transitionTicks;

    private DAI_VanillaWorldScreens() {}

    public static void openWorldList(Screen parent) {
        DAI_TitleActionDispatcher.openReflective(
                parent,
                "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen"
        );
    }

    public static boolean openCreate(Screen parent) {
        return DAI_ShellWorldRuntime.runAfterCleanWorldDetach(
                "PREPARING WORLD CREATION",
                parent,
                () -> {
                    if (!openCreateDetached(parent)) {
                        throw new IllegalStateException("Minecraft Create World flow was unavailable after shell detach");
                    }
                }
        );
    }

    private static boolean openCreateDetached(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) return false;

        armCreateWatch(minecraft, parent);
        Runnable cancel = DAI_VanillaWorldScreens::cancelCreateToSource;
        try {
            Class<?> screenClass = Class.forName(
                    "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen"
            );
            for (Method method : screenClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                if (!name.contains("openfresh") && !name.equals("open")) continue;
                Object[] args = resolve(method.getParameterTypes(), parent, minecraft, cancel);
                if (args == null) continue;
                if (!method.canAccess(null) && !method.trySetAccessible()) continue;
                try {
                    Object result = method.invoke(null, args);
                    if (result instanceof Screen returned) minecraft.gui.setScreen(returned);
                    return true;
                } catch (Throwable ignored) {
                    // Keep probing mapped overloads.
                }
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Vanilla Create World flow unavailable after shell detach: {}",
                    exception.toString()
            );
        }

        clearCreateWatch(true);
        DAI_ShellWorldRuntime.restartShellAfterDetachedCancellation(parent);
        return false;
    }

    /** True while Minecraft's world-creation controller is owned by the DAI create flow. */
    public static boolean isCreateUiActive() {
        return createPending;
    }

    /** Client post-tick watchdog for vanilla and Minecraft+DAI creation. */
    public static void tickCreateFlow() {
        if (!createPending) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            clearCreateWatch(true);
            return;
        }

        Screen screen = minecraft.gui.screen();
        if (isCreateWorldScreen(screen)) {
            if (!createScreenSeen) {
                createScreenSeen = true;
                // The shell handoff veil is only for the teardown/rebuild gap.
                // Vanilla/Minecraft+DAI creation is intentionally interactive,
                // so reveal Mojang's Create World screen as soon as it is ready.
                // Keeping the veil active here strands the player behind an
                // invisible CreateWorldScreen while no server has started yet.
                DAI_SafeLoadingVeil.cancel();
                DAI_Core.LOGGER.info(
                        "<DAI>: DAI Create World presentation is ready after shell detach; revealing themed world setup."
                );
            }
            transitionTicks = 0;
            lastPrompt = null;
            promptTicks = 0;
            return;
        }

        if (!createScreenSeen) {
            transitionTicks++;

            if (screen == sourceParent) {
                cancelCreateToSource();
                return;
            }

            // The reflection bridge can occasionally return successfully while
            // an intermediate Mojang screen owns the UI. Do not keep that screen
            // hidden forever if CreateWorldScreen never appears.
            if (transitionTicks == 80) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Create World screen has not appeared 80 ticks after shell detach (screen='{}'); revealing Minecraft UI while continuing to watch.",
                        screen == null ? "<none>" : screen.getClass().getName()
                );
                DAI_SafeLoadingVeil.cancel();
            } else if (transitionTicks >= 240) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Create World screen never appeared after {} ticks (screen='{}'); cancelling the pending create watchdog.",
                        transitionTicks,
                        screen == null ? "<none>" : screen.getClass().getName()
                );
                cancelCreateToSource();
            }
            return;
        }

        // Returning to the source screen means Create World was cancelled.
        if (screen == sourceParent) {
            cancelCreateToSource();
            return;
        }

        // DAI's experimental warning is interactive UI, not a loading state.
        // Keep it visible even though it appears after the Create action begins.
        if (screen instanceof DAI_ExperimentalFeaturesScreen) {
            DAI_ShellWorldRuntime.resumeAfterCancelledTransition();
            return;
        }

        transitionTicks++;
        if (!transitionVeilStarted) {
            transitionVeilStarted = true;
            DAI_ShellWorldRuntime.prepareExperienceTransition("CREATING WORLD");
        }

        // Only the explicit Minecraft backup warning is safe to auto-accept.
        // A generic ConfirmScreen may represent lifecycle/experimental world
        // data or another creation warning; pressing it blindly can launch a
        // second/invalid creation continuation while configuration is active.
        if (DAI_WorldLaunchConfirmation.isBackupPrompt(screen)) {
            if (screen != lastPrompt) {
                lastPrompt = screen;
                promptTicks = 0;
            }
            promptTicks++;
            if (promptTicks == 1 || promptTicks % 10 == 0) {
                if (DAI_WorldLaunchConfirmation.acceptAffirmative(screen)) {
                    DAI_Core.LOGGER.info(
                            "<DAI>: Auto-accepted Create World backup confirmation '{}'.",
                            screen.getClass().getSimpleName()
                    );
                    promptTicks = -20;
                }
            }
            return;
        }

        if (DAI_WorldLaunchConfirmation.isGenericPrompt(screen)) {
            // Do not hide or press an unknown vanilla confirmation. Keep it
            // visible and let the user decide; pure Vanilla should normally
            // never reach this after the generated-pack exclusion above.
            if (screen != lastPrompt) {
                lastPrompt = screen;
                DAI_Core.LOGGER.warn(
                        "<DAI>: Vanilla Create World reached generic confirmation '{}'; leaving it untouched.",
                        screen.getClass().getName()
                );
                DAI_ShellWorldRuntime.resumeAfterCancelledTransition();
            }
            return;
        }

        boolean attached = minecraft.level != null
                && minecraft.player != null
                && minecraft.level != sourceLevel;
        if (attached) {
            DAI_Core.LOGGER.info("<DAI>: New world attached successfully after {} transition ticks.", transitionTicks);
            clearCreateWatch(false);
            return;
        }

        if (transitionTicks % 100 == 0) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Create World transition still active ({} ticks, screen='{}', sourceLevelSame={}).",
                    transitionTicks,
                    screen == null ? "<none>" : screen.getClass().getName(),
                    minecraft.level == sourceLevel
            );
        }

        // Never leave a hidden deadlock indefinitely. The DAI disconnect
        // runtime handles true protocol failures separately.
        if (transitionTicks >= 300 && !isLoadingScreen(screen)) {
            DAI_ShellWorldRuntime.resumeAfterCancelledTransition();
        }
    }

    /**
     * Cancels the DAI-owned vanilla/Minecraft+DAI creation flow and returns to
     * the DAI screen that launched it. If the shell was already detached, the
     * requested screen is restored immediately after the shell rebuilds.
     */
    public static void cancelCreateToSource() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen target = sourceParent;
        clearCreateWatch(true);
        DAI_SafeLoadingVeil.cancel();

        if (minecraft == null || minecraft.gui == null) return;
        if (minecraft.level == null && minecraft.player == null) {
            DAI_ShellWorldRuntime.restartShellAfterDetachedCancellation(target);
        } else if (target != null) {
            minecraft.gui.setScreen(target);
        }
    }

    private static void armCreateWatch(Minecraft minecraft, Screen parent) {
        createPending = true;
        createScreenSeen = false;
        transitionVeilStarted = false;
        sourceLevel = minecraft == null ? null : minecraft.level;
        sourceParent = parent;
        lastPrompt = null;
        promptTicks = 0;
        transitionTicks = 0;
    }

    private static void clearCreateWatch(boolean clearStandaloneSelection) {
        createPending = false;
        createScreenSeen = false;
        transitionVeilStarted = false;
        sourceLevel = null;
        sourceParent = null;
        lastPrompt = null;
        promptTicks = 0;
        transitionTicks = 0;
        if (clearStandaloneSelection) DAI_StandaloneLaunchState.clear();
    }

    private static boolean isCreateWorldScreen(Screen screen) {
        return screen != null && "CreateWorldScreen".equals(screen.getClass().getSimpleName());
    }

    private static boolean isLoadingScreen(Screen screen) {
        if (screen == null) return false;
        String name = screen.getClass().getSimpleName();
        return "LevelLoadingScreen".equals(name)
                || "ReceivingLevelScreen".equals(name)
                || "ProgressScreen".equals(name)
                || "GenericWaitingScreen".equals(name)
                || "GenericMessageScreen".equals(name);
    }

    private static Object[] resolve(Class<?>[] types, Screen parent, Minecraft minecraft, Runnable cancel) {
        Object[] values = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (Screen.class.isAssignableFrom(type)) values[i] = parent;
            else if (Minecraft.class.isAssignableFrom(type)) values[i] = minecraft;
            else if (type == String.class) values[i] = "";
            else if (type == boolean.class || type == Boolean.class) values[i] = false;
            else if (Runnable.class.isAssignableFrom(type)) values[i] = cancel == null ? (Runnable) () -> {} : cancel;
            else return null;
        }
        return values;
    }
}
