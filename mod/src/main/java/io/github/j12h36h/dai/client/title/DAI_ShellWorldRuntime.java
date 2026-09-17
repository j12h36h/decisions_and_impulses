package io.github.j12h36h.dai.client.title;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.client.branding.DAI_SafeLoadingVeil;
import io.github.j12h36h.dai.client.data.DAI_ClientDataBootstrap;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellPresentationRepository;
import io.github.j12h36h.dai.client.config.DAI_ClientConfig;
import io.github.j12h36h.dai.client.presentation.scene.DAI_SceneRenderSafety;
import io.github.j12h36h.dai.client.play.DAI_WorldLaunchConfirmation;
import io.github.j12h36h.dai.client.packs.DAI_WorldResourcePackSelection;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellPresentationDefinition;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellScreenRouter;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.runtime.DAI_ShellSessionState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

/**
 * Boots DAI Engine's full game shell through a tiny reserved singleplayer world.
 *
 * Before this world exists DAI deliberately renders only the safe vanilla village
 * splash. Once the shell level/player are attached, Minecraft's block
 * and item component registries are fully bound and the JSON 3-D scene system
 * may use vanilla, modded and DAI-provided models on title/editor surfaces.
 */
public final class DAI_ShellWorldRuntime {

    private static final String SHELL_SAVE_BASE = "DAI_Engine_Shell";
    private static final String SHELL_DISPLAY_NAME = "DAI Engine Shell";
    private static final String MARKER = "dai/shell.json";
    private static final int SHELL_MARKER_SCHEMA = 2;

    private enum State {
        IDLE,
        OPENING_CREATE,
        CONFIGURING_CREATE,
        WAITING_FOR_WORLD,
        ACTIVE,
        LEAVING
    }

    private static final int STALLED_OPEN_TICKS = 240;
    private static final int SERVER_START_GRACE_TICKS = 600;

    private static State state = State.IDLE;
    private static Screen bootParent;
    private static Object shellLevel;
    private static int createTicks;
    private static int readyTicks;
    private static int waitTicks;
    private static int shellRevealTicks;
    private static boolean createInvoked;
    private static boolean shellConfirmationAccepted;
    private static boolean waitingFromExisting;
    private static boolean rebuildAttemptedThisBoot;
    private static boolean failedThisSession;
    private static String expectedSaveId = SHELL_SAVE_BASE;

    /*
     * DAI's title is rendered from a real reserved integrated-server world.
     * Minecraft's Create/Open flows normally begin from TitleScreen with no
     * active connection. Starting a second integrated server directly from
     * the shell can overlap the old configuration/tag teardown with the new
     * RegistryDataCollector and produce late-load Network Protocol Error.
     * Queue the target flow until the shell connection and server are fully
     * gone, then give the client a few ticks to settle before launching it.
     */
    private static final int WORLD_HANDOFF_SETTLE_TICKS = 8;
    private static final int WORLD_HANDOFF_TIMEOUT_TICKS = 400;
    private static Runnable pendingWorldHandoff;
    private static Screen pendingWorldHandoffFallback;
    private static Screen pendingDetachedReturnScreen;
    private static int worldHandoffTicks;
    private static int worldHandoffDetachedTicks;

    private DAI_ShellWorldRuntime() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) return;

        if (!enabled()) {
            reset(false);
            return;
        }

        Screen screen = minecraft.gui.screen();

        if (pendingWorldHandoff != null) {
            tickPendingWorldHandoff(minecraft, screen);
            return;
        }

        if (state == State.ACTIVE) {
            if (minecraft.level == null) {
                reset(false);
                return;
            }
            if (shellLevel != null && minecraft.level != shellLevel) {
                // A real experience/world switch won the race. Any short shell
                // reveal guard must disappear immediately so it cannot cover
                // gameplay after the destination level is attached.
                if (shellRevealTicks > 0) DAI_SafeLoadingVeil.complete();
                shellRevealTicks = 0;
                state = State.LEAVING;
                shellLevel = null;
                return;
            }

            // Never expose the reserved shell world's HUD/gameplay if a child
            // DAI screen closes without an explicit parent (including the OS X
            // button). Raise the resource-safe village splash veil *before* restoring
            // the title and hold it briefly so no raw shell-world frame leaks.
            if (screen == null) {
                if (shellRevealTicks <= 0) {
                    shellRevealTicks = 20;
                    DAI_SafeLoadingVeil.beginBootstrap("RETURNING TO DAI");
                }
                try {
                    DAI_TitleScreenDefinition definition = DAI_TitleScreenRepository.current();
                    if (definition.enabled()) {
                        minecraft.gui.setScreen(DAI_ShellScreenRouter.resolve(
                                DAI_ShellScreenRouter.TITLE,
                                null,
                                () -> new DAI_TitleScreen(definition),
                                TitleScreen::new
                        ));
                    }
                } catch (Throwable exception) {
                    DAI_Core.LOGGER.debug("<DAI>: Could not restore DAI shell title after screen close: {}", exception.toString());
                }
                return;
            }

            if (shellRevealTicks > 0) {
                shellRevealTicks--;
                if (shellRevealTicks <= 0) DAI_SafeLoadingVeil.complete();
            }
            return;
        }

        if (state == State.LEAVING) {
            // Returning to a vanilla TitleScreen after an experience disconnect
            // arms a new shell boot on the next tick.
            if (minecraft.level == null && screen instanceof TitleScreen) {
                reset(false);
            }
            return;
        }

        if (state == State.IDLE) {
            if (failedThisSession) return;
            if (minecraft.level != null) return;
            if (!(screen instanceof TitleScreen)) return;
            // LoadingOverlay can sit above TitleScreen while registries and
            // resources are still being finalized. Never start world creation
            // underneath that bootstrap overlay; the bootstrap-safe DAI village splash owns
            // this interval instead.
            if (hasBlockingOverlay(minecraft)) return;
            // A normal world may have temporarily auto-activated per-world
            // resource packs. Restore/reload the player's pre-world selection
            // before booting the DAI shell so those assets never leak into it.
            if (!DAI_WorldResourcePackSelection.ensureRestoredBeforeShell()) return;
            bootParent = screen;
            startShell(minecraft, screen);
            return;
        }

        if (state == State.CONFIGURING_CREATE || state == State.OPENING_CREATE) {
            tickCreateScreen(minecraft, screen);
            return;
        }

        if (state == State.WAITING_FOR_WORLD) {
            tickWorldReady(minecraft, screen);
        }
    }

    public static boolean enabled() {
        DAI_ShellPresentationDefinition.Route title = DAI_ShellScreenRouter.route(DAI_ShellScreenRouter.TITLE);
        boolean shellOwnsTitle = !DAI_ShellPresentationDefinition.MODE_VANILLA.equals(title.mode())
                && !DAI_ShellPresentationDefinition.MODE_NONE.equals(title.mode());
        return shellOwnsTitle
                && DAI_ClientConfig.fullGameShell()
                && DAI_ClientConfig.autoShellWorld()
                && DAI_Config.customTitleScreens();
    }

    public static boolean shouldBootstrapTitle() {
        return enabled() && !failedThisSession;
    }

    public static boolean isShellActive() {
        return state == State.ACTIVE && shellLevel != null;
    }

    public static boolean isBootstrapping() {
        return state == State.OPENING_CREATE
                || state == State.CONFIGURING_CREATE
                || state == State.WAITING_FOR_WORLD;
    }

    /**
     * Runs a world-create/open action only after the current integrated-server
     * connection has been cleanly torn down. This is required when DAI's 3-D
     * shell is active because vanilla's world flows are designed to start from
     * a disconnected title state.
     */
    public static boolean runAfterCleanWorldDetach(
            String label,
            Screen fallback,
            Runnable launch
    ) {
        if (launch == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) return false;

        if (pendingWorldHandoff != null) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Refusing to stack another world handoff while one is already pending."
            );
            return false;
        }

        if (minecraft.level == null
                && !hasIntegratedServer(minecraft)
                && invokeNoArg(minecraft, "getConnection") == null) {
            try {
                launch.run();
                return true;
            } catch (Throwable exception) {
                DAI_Core.LOGGER.error("<DAI>: Detached world launch failed.", exception);
                return false;
            }
        }

        pendingWorldHandoff = launch;
        pendingWorldHandoffFallback = fallback;
        worldHandoffTicks = 0;
        worldHandoffDetachedTicks = 0;
        shellRevealTicks = 0;
        state = State.LEAVING;
        shellLevel = null;
        DAI_SafeLoadingVeil.beginWorldTransition(
                label == null || label.isBlank() ? "PREPARING WORLD" : label
        );

        if (!disconnectForWorldHandoff(minecraft)) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not start a clean shell disconnect before world handoff."
            );
            clearPendingWorldHandoff();
            DAI_SafeLoadingVeil.cancel();
            return false;
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Clean shell detach started before target world launch."
        );
        return true;
    }

    /** Called by the experience launcher before Minecraft starts switching saves. */
    public static void prepareExperienceTransition(String label) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        shellRevealTicks = 0;
        if (state == State.ACTIVE) state = State.LEAVING;
        DAI_SafeLoadingVeil.beginWorldTransition(label == null ? "PREPARING EXPERIENCE" : label);
    }

    /** Arms the safe veil while Minecraft saves/disconnects back to the shell. */
    public static void prepareReturnToShell() {
        shellRevealTicks = 0;
        state = State.LEAVING;
        shellLevel = null;
        DAI_SafeLoadingVeil.beginBootstrap("RETURNING TO DAI");
    }

    /**
     * Leaves the current gameplay world and returns to the currently-owned
     * Experience title shell. Unlike returnToDaiUniverse(), this deliberately
     * preserves datapack shell ownership, so a full-takeover Experience lands
     * back on its own title screen after Minecraft completes save/disconnect.
     */
    public static boolean returnToExperienceTitle() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) return false;

        shellRevealTicks = 0;
        state = State.LEAVING;
        shellLevel = null;
        DAI_SafeLoadingVeil.beginBootstrap("RETURNING TO TITLE");

        boolean connected = minecraft.level != null
                || hasIntegratedServer(minecraft)
                || invokeNoArg(minecraft, "getConnection") != null;

        if (!connected) {
            minecraft.gui.setScreen(new TitleScreen());
            DAI_Core.LOGGER.info("<DAI>: Returning to Experience title from an already-detached client.");
            return true;
        }

        if (disconnectForWorldHandoff(minecraft)) {
            DAI_Core.LOGGER.info("<DAI>: Return to Experience title disconnect started.");
            return true;
        }

        DAI_Core.LOGGER.warn("<DAI>: Return to Experience title could not disconnect the active world.");
        DAI_SafeLoadingVeil.cancel();
        state = State.IDLE;
        shellLevel = null;
        return false;
    }

    /**
     * Leaves the current world/session and explicitly returns to DAI's built-in
     * Universe shell, even when a MAIN datapack currently owns the application
     * shell. This is the client-side primitive datapacks should invoke instead
     * of trying to kick the local player from an integrated server.
     */
    public static boolean returnToDaiUniverse() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) return false;

        prepareReturnToShell();
        DAI_ShellPresentationRepository.enterDaiUniverseMode();

        /*
         * Re-resolve title/presentation data immediately so the technical
         * TitleScreen waypoint cannot bounce back into the just-suspended
         * Experience shell while disconnect teardown is still completing.
         */
        DAI_TitleScreenRepository.reload();
        DAI_ClientDataBootstrap.reloadLocalData();

        boolean connected = minecraft.level != null
                || hasIntegratedServer(minecraft)
                || invokeNoArg(minecraft, "getConnection") != null;

        if (!connected) {
            minecraft.gui.setScreen(new TitleScreen());
            DAI_Core.LOGGER.info("<DAI>: Returning to DAI Universe from an already-detached client.");
            return true;
        }

        if (disconnectForWorldHandoff(minecraft)) {
            DAI_Core.LOGGER.info("<DAI>: Return to DAI Universe disconnect started.");
            return true;
        }

        DAI_Core.LOGGER.warn("<DAI>: Return to DAI Universe could not disconnect the active world.");
        DAI_ShellPresentationRepository.cancelDaiUniverseMode();
        DAI_TitleScreenRepository.reload();
        DAI_ClientDataBootstrap.reloadLocalData();
        DAI_SafeLoadingVeil.cancel();
        return false;
    }

    /** Restores the shell if a Create World / launch flow was cancelled or rejected. */
    public static void resumeAfterCancelledTransition() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && shellLevel != null && minecraft.level == shellLevel) {
            state = State.ACTIVE;
        }
        DAI_SafeLoadingVeil.cancel();
    }

    /**
     * Handles a real user cancellation after the reserved shell has already
     * been disconnected for a world handoff. Unlike resumeAfterCancelledTransition,
     * this intentionally returns through TitleScreen so the shell can bootstrap
     * again instead of leaving DAI UI attached to a client with no world.
     */
    public static void restartShellAfterDetachedCancellation() {
        restartShellAfterDetachedCancellation(null);
    }

    /**
     * Rebuilds the reserved shell after a detached world flow is cancelled and
     * restores the DAI screen that originally launched that flow. This keeps
     * Cancel/Back navigation inside the DAI hierarchy rather than dumping the
     * player onto TitleScreen after the technical shell reconstruction.
     */
    public static void restartShellAfterDetachedCancellation(Screen returnScreen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (returnScreen != null) pendingDetachedReturnScreen = returnScreen;
        if (minecraft == null) {
            DAI_SafeLoadingVeil.cancel();
            return;
        }

        if (shellLevel != null && minecraft.level == shellLevel) {
            state = State.ACTIVE;
            DAI_SafeLoadingVeil.cancel();
            Screen target = pendingDetachedReturnScreen;
            pendingDetachedReturnScreen = null;
            if (target != null && minecraft.gui != null) minecraft.gui.setScreen(target);
            return;
        }

        DAI_SafeLoadingVeil.cancel();
        if (minecraft.level != null || hasIntegratedServer(minecraft)
                || invokeNoArg(minecraft, "getConnection") != null) {
            return;
        }

        clearPendingWorldHandoff();
        shellLevel = null;
        state = State.IDLE;
        if (minecraft.gui != null && !(minecraft.gui.screen() instanceof TitleScreen)) {
            minecraft.gui.setScreen(new TitleScreen());
        }
        DAI_Core.LOGGER.info(
                "<DAI>: Detached world flow was cancelled; rebuilding the DAI shell before restoring '{}'.",
                returnScreen == null ? "DAI title" : returnScreen.getClass().getSimpleName()
        );
    }

    private static void startShell(Minecraft minecraft, Screen parent) {
        DAI_SafeLoadingVeil.beginBootstrap("INITIALIZING DAI");
        // The first shell-world server start happens before its persistent
        // marker can be written. Arm the common-side bridge so worldgen knows
        // this is infrastructure, not an ordinary standalone playthrough.
        DAI_ShellSessionState.arm();
        rebuildAttemptedThisBoot = false;

        String existing = findExistingShellSave();
        if (existing != null) {
            expectedSaveId = existing;
            ensureShellMarker(existing);

            if (openExisting(minecraft, parent, existing)) {
                state = State.WAITING_FOR_WORLD;
                readyTicks = 0;
                waitTicks = 0;
                waitingFromExisting = true;
                DAI_Core.LOGGER.info("<DAI>: Loading reserved DAI shell world '{}'.", existing);
                return;
            }

            // Never create a sibling hub merely because the load flow could not
            // be invoked. The existing reserved save remains authoritative.
            DAI_Core.LOGGER.warn(
                    "<DAI>: Existing reserved DAI shell world '{}' could not be opened; preserving it and falling back to the 2-D shell for this session.",
                    existing
            );
            DAI_ShellSessionState.clearArm();
            failedThisSession = true;
            DAI_SafeLoadingVeil.cancel();
            reset(false);
            return;
        }

        expectedSaveId = SHELL_SAVE_BASE;
        prepareReservedSaveSlot();
        if (openFresh(minecraft, parent)) {
            state = State.OPENING_CREATE;
            createTicks = 0;
            readyTicks = 0;
            waitTicks = 0;
            createInvoked = false;
            shellConfirmationAccepted = false;
            waitingFromExisting = false;
            DAI_Core.LOGGER.info("<DAI>: Creating reserved DAI shell world '{}'.", SHELL_SAVE_BASE);
            return;
        }

        DAI_Core.LOGGER.warn("<DAI>: Could not open DAI shell world flow; falling back to the 2-D title shell.");
        DAI_ShellSessionState.clearArm();
        failedThisSession = true;
        DAI_SafeLoadingVeil.cancel();
        reset(false);
    }

    private static void tickCreateScreen(Minecraft minecraft, Screen screen) {
        if (screen == null) return;
        if (!isCreateWorldScreen(screen)) {
            if (createInvoked) {
                state = State.WAITING_FOR_WORLD;
                return;
            }
            // The Create screen may take a tick to replace TitleScreen.
            if (++createTicks < 12) return;
            DAI_Core.LOGGER.warn("<DAI>: DAI shell Create World screen disappeared before creation.");
            DAI_ShellSessionState.clearArm();
            failedThisSession = true;
            DAI_SafeLoadingVeil.cancel();
            reset(false);
            return;
        }

        state = State.CONFIGURING_CREATE;
        createTicks++;
        if (createTicks < 2 || createInvoked) return;

        Object uiState = invokeNoArg(screen, "getUiState");
        if (uiState != null) {
            invokeCompatibleSetter(uiState, "setName", SHELL_SAVE_BASE);
            invokeEnumSetter(uiState, "setGameMode", "CREATIVE");
        }

        if (invokeCreate(screen)) {
            createInvoked = true;
            state = State.WAITING_FOR_WORLD;
            readyTicks = 0;
            waitTicks = 0;
            waitingFromExisting = false;
        } else {
            DAI_Core.LOGGER.warn("<DAI>: Could not invoke Create for the reserved DAI shell world.");
            DAI_ShellSessionState.clearArm();
            failedThisSession = true;
            DAI_SafeLoadingVeil.cancel();
            reset(false);
        }
    }

    private static void tickWorldReady(Minecraft minecraft, Screen screen) {
        // As soon as Minecraft materializes level.dat, persist DAI ownership.
        // This happens before ClientLevel attachment so a crash/stall cannot
        // make the already-created hub look like an unrelated or stale world
        // on the next launch.
        ensureShellMarker(expectedSaveId);

        // Minecraft 26.2 can insert confirmation UI while either creating or
        // reopening the reserved shell. BackupConfirmScreen is a sibling of
        // ConfirmScreen rather than a subclass, which previously let an
        // existing shell stall behind the Safe Veil and trigger an unnecessary
        // rebuild. The shell is DAI-owned infrastructure, so accept its safe
        // affirmative path once for both fresh and existing shell launches.
        if (minecraft.level == null
                && (createInvoked || waitingFromExisting)
                && !shellConfirmationAccepted
                && DAI_WorldLaunchConfirmation.isAffirmativePrompt(screen)) {
            waitTicks = 0;
            if (acceptReservedShellConfirmation(screen)) {
                shellConfirmationAccepted = true;
                DAI_Core.LOGGER.info(
                        "<DAI>: Accepted reserved shell-world confirmation '{}'; continuing shell bootstrap.",
                        screen.getTitle().getString()
                );
                return;
            }
            // Screen widgets/callbacks can exist one tick before they are ready
            // to invoke. Leave the flag false so the next tick retries instead
            // of permanently deadlocking behind the veil.
            DAI_Core.LOGGER.debug(
                    "<DAI>: Reserved shell-world confirmation '{}' is not invokable yet; retrying.",
                    screen.getTitle().getString()
            );
            return;
        }

        if (minecraft.level == null || minecraft.player == null || isWorldLoadingScreen(screen)) {
            readyTicks = 0;

            // A real Mojang loading screen/overlay is positive evidence that the
            // world-open flow is still advancing. Do not race it with a retry.
            if (isWorldLoadingScreen(screen) || hasBlockingOverlay(minecraft)) {
                waitTicks = 0;
                return;
            }

            // 46% is the Safe Veil's terminal target when no ClientLevel has
            // attached and Minecraft is no longer presenting a recognized
            // loading/create screen. Previously WAITING_FOR_WORLD had no escape
            // from this state, so a rejected/stale shell open could hang forever.
            waitTicks++;
            int timeout = hasIntegratedServer(minecraft)
                    ? SERVER_START_GRACE_TICKS
                    : STALLED_OPEN_TICKS;
            if (waitTicks >= timeout) {
                recoverStalledShellOpen(minecraft, screen);
            }
            return;
        }

        waitTicks = 0;
        if (++readyTicks < 6) return;

        DAI_SceneRenderSafety.markReady();
        if (!DAI_SceneRenderSafety.registryModelsReady()) {
            readyTicks = 0;
            return;
        }

        shellLevel = minecraft.level;
        failedThisSession = false;
        writeShellMarker();
        state = State.ACTIVE;
        DAI_SafeLoadingVeil.complete();

        Screen detachedReturn = pendingDetachedReturnScreen;
        pendingDetachedReturnScreen = null;
        try {
            if (detachedReturn != null) {
                minecraft.gui.setScreen(detachedReturn);
                DAI_Core.LOGGER.info(
                        "<DAI>: Restored '{}' after detached world-flow cancellation.",
                        detachedReturn.getClass().getSimpleName()
                );
            } else {
                DAI_TitleScreenDefinition definition = DAI_TitleScreenRepository.current();
                if (definition.enabled()) {
                    minecraft.gui.setScreen(DAI_ShellScreenRouter.resolve(
                            DAI_ShellScreenRouter.TITLE,
                            null,
                            () -> new DAI_TitleScreen(definition),
                            TitleScreen::new
                    ));
                }
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.error("<DAI>: Shell world loaded, but the DAI shell screen could not be opened.", exception);
        }

        DAI_Core.LOGGER.info("<DAI>: DAI shell world is ready; full registry-backed 3-D presentation enabled.");
    }


    private static boolean acceptReservedShellConfirmation(Screen screen) {
        return DAI_WorldLaunchConfirmation.acceptAffirmative(screen);
    }


    private static void tickPendingWorldHandoff(Minecraft minecraft, Screen screen) {
        worldHandoffTicks++;

        boolean levelGone = minecraft.level == null && minecraft.player == null;
        boolean serverGone = !hasIntegratedServer(minecraft);
        boolean connectionGone = invokeNoArg(minecraft, "getConnection") == null;

        if (!levelGone || !serverGone || !connectionGone) {
            worldHandoffDetachedTicks = 0;
            if (worldHandoffTicks % 100 == 0) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Waiting for shell detach before world handoff ({} ticks, levelGone={}, serverGone={}, connectionGone={}, screen='{}').",
                        worldHandoffTicks,
                        levelGone,
                        serverGone,
                        connectionGone,
                        screen == null ? "<none>" : screen.getClass().getName()
                );
            }
            if (worldHandoffTicks >= WORLD_HANDOFF_TIMEOUT_TICKS) {
                failPendingWorldHandoff(minecraft, "Timed out waiting for the DAI shell connection to close.");
            }
            return;
        }

        worldHandoffDetachedTicks++;
        if (worldHandoffDetachedTicks < WORLD_HANDOFF_SETTLE_TICKS) return;

        Runnable launch = pendingWorldHandoff;
        clearPendingWorldHandoff();
        // Keep LEAVING armed while the target flow is opening so the title
        // controller cannot immediately bootstrap another shell in the gap.
        state = State.LEAVING;
        try {
            launch.run();
            DAI_Core.LOGGER.info(
                    "<DAI>: Target world flow started after clean shell detach."
            );
        } catch (Throwable exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Target world flow failed after clean shell detach.",
                    exception
            );
            failPendingWorldHandoff(minecraft, "The target world flow could not be started.");
        }
    }

    private static boolean disconnectForWorldHandoff(Minecraft minecraft) {
        if (minecraft == null) return false;
        try {
            for (Method method : Minecraft.class.getMethods()) {
                if (!method.getName().equals("disconnect")) continue;
                Class<?>[] types = method.getParameterTypes();
                Object[] args = new Object[types.length];
                boolean compatible = true;
                int booleanIndex = 0;
                for (int i = 0; i < types.length; i++) {
                    Class<?> type = types[i];
                    if (Screen.class.isAssignableFrom(type)) {
                        args[i] = new TitleScreen();
                    } else if (type == boolean.class || type == Boolean.class) {
                        // 26.2: first boolean is keepResourcePacks, optional
                        // second boolean is stopSound. A world-to-world handoff
                        // must clear the old server pack context completely.
                        args[i] = booleanIndex++ > 0;
                    } else {
                        compatible = false;
                        break;
                    }
                }
                if (!compatible) continue;
                method.invoke(minecraft, args);
                return true;
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Minecraft disconnect rejected clean world handoff.",
                    exception
            );
        }
        return false;
    }

    private static void failPendingWorldHandoff(Minecraft minecraft, String reason) {
        Screen fallback = pendingWorldHandoffFallback;
        clearPendingWorldHandoff();
        DAI_SafeLoadingVeil.cancel();
        state = State.IDLE;
        shellLevel = null;
        DAI_Core.LOGGER.warn("<DAI>: {}", reason);
        if (minecraft != null && minecraft.gui != null && minecraft.level == null) {
            minecraft.gui.setScreen(fallback == null ? new TitleScreen() : fallback);
        }
    }

    private static void clearPendingWorldHandoff() {
        pendingWorldHandoff = null;
        pendingWorldHandoffFallback = null;
        worldHandoffTicks = 0;
        worldHandoffDetachedTicks = 0;
    }

    private static boolean hasIntegratedServer(Minecraft minecraft) {
        return invokeNoArg(minecraft, "getSingleplayerServer") != null;
    }

    private static void recoverStalledShellOpen(Minecraft minecraft, Screen screen) {
        String screenName = screen == null ? "<none>" : screen.getClass().getName();

        // There is exactly one authoritative shell save. If an existing shell
        // cannot even start an integrated server, it is safe to rebuild that
        // same reserved slot once. We never create DAI_Engine_Shell_2/3/etc.
        //
        // If an integrated server *has* started, do not delete files underneath
        // it. Fall back for this session and retry the same shell next launch.
        if (waitingFromExisting
                && !rebuildAttemptedThisBoot
                && !hasIntegratedServer(minecraft)) {
            rebuildAttemptedThisBoot = true;
            String stalledSave = expectedSaveId;

            DAI_Core.LOGGER.warn(
                    "<DAI>: Reserved shell world '{}' stalled before server startup (screen='{}'); rebuilding the same internal shell slot.",
                    stalledSave,
                    screenName
            );

            DAI_ShellSessionState.clearArm();
            if (!deleteReservedShellSave(stalledSave)) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Could not safely clear stalled reserved shell '{}'; refusing to create a sibling shell world.",
                        stalledSave
                );
                failToTwoDimensionalShell(minecraft);
                return;
            }

            DAI_ShellSessionState.arm();
            DAI_SafeLoadingVeil.beginBootstrap("REBUILDING DAI");

            Screen parent = bootParent == null ? new TitleScreen() : bootParent;
            waitingFromExisting = false;
            waitTicks = 0;
            readyTicks = 0;
            createTicks = 0;
            createInvoked = false;
            shellConfirmationAccepted = false;
            expectedSaveId = SHELL_SAVE_BASE;
            prepareReservedSaveSlot();

            if (openFresh(minecraft, parent)) {
                state = State.OPENING_CREATE;
                DAI_Core.LOGGER.info(
                        "<DAI>: Rebuilding reserved DAI shell in canonical slot '{}'.",
                        SHELL_SAVE_BASE
                );
                return;
            }
        }

        DAI_Core.LOGGER.warn(
                "<DAI>: DAI shell bootstrap stalled without a usable ClientLevel (screen='{}'); preserving the single reserved shell and disabling it for this session.",
                screenName
        );
        failToTwoDimensionalShell(minecraft);
    }

    private static void failToTwoDimensionalShell(Minecraft minecraft) {
        Screen fallback = bootParent == null ? new TitleScreen() : bootParent;
        DAI_ShellSessionState.clearArm();
        failedThisSession = true;
        DAI_SafeLoadingVeil.cancel();
        reset(false);

        try {
            if (minecraft != null && minecraft.gui != null && minecraft.level == null) {
                minecraft.gui.setScreen(fallback);
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not restore 2-D title after shell bootstrap fallback: {}", exception.toString());
        }
    }

    private static boolean openExisting(Minecraft minecraft, Screen parent, String saveId) {
        try {
            Object flows = invokeNoArg(minecraft, "createWorldOpenFlows");
            if (flows == null) return false;
            for (Method method : flows.getClass().getMethods()) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("openworld") && !name.contains("loadworld")) continue;
                Object[] args = resolve(method.getParameterTypes(), parent, minecraft, saveId, null);
                if (args == null) continue;
                try {
                    method.invoke(flows, args);
                    return true;
                } catch (Throwable ignored) {
                    // Try another mapped signature.
                }
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.debug("<DAI>: Reserved shell load flow rejected '{}': {}", saveId, exception.toString());
        }
        return false;
    }

    private static boolean openFresh(Minecraft minecraft, Screen parent) {
        try {
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.worldselection.CreateWorldScreen");
            Runnable cancel = () -> {
                DAI_ShellSessionState.clearArm();
                DAI_SafeLoadingVeil.cancel();
                reset(false);
                if (minecraft.player == null) minecraft.gui.setScreen(parent);
            };
            for (Method method : screenClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("openfresh") && !name.equals("open")) continue;
                Object[] args = resolve(method.getParameterTypes(), parent, minecraft, "", cancel);
                if (args == null) continue;
                if (!method.canAccess(null) && !method.trySetAccessible()) continue;
                try {
                    Object result = method.invoke(null, args);
                    if (result instanceof Screen returned) minecraft.gui.setScreen(returned);
                    return true;
                } catch (Throwable ignored) {
                    // Try another mapped signature.
                }
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.debug("<DAI>: Reserved shell fresh-world flow unavailable: {}", exception.toString());
        }
        return false;
    }

    private static String findExistingShellSave() {
        Path root = savesDirectory();
        if (!Files.isDirectory(root)) return null;

        Path canonical = root.resolve(SHELL_SAVE_BASE);

        // The canonical folder is authoritative even if an earlier interrupted
        // bootstrap never got far enough to write dai/shell.json.
        if (Files.isDirectory(canonical) && Files.isRegularFile(canonical.resolve("level.dat"))) {
            ensureShellMarker(SHELL_SAVE_BASE);
            return SHELL_SAVE_BASE;
        }

        // A marker-only/partial canonical directory from a failed first create
        // would force Minecraft to invent DAI_Engine_Shell_2. Remove only that
        // incomplete internal directory before considering legacy siblings.
        if (Files.isDirectory(canonical) && !Files.isRegularFile(canonical.resolve("level.dat"))) {
            deleteReservedShellSave(SHELL_SAVE_BASE);
        }

        try (var stream = Files.list(root)) {
            Path legacy = stream
                    .filter(Files::isDirectory)
                    .filter(path -> isReservedShellName(path.getFileName().toString()))
                    .filter(path -> !path.getFileName().toString().equals(SHELL_SAVE_BASE))
                    .filter(path -> Files.isRegularFile(path.resolve("level.dat")))
                    .sorted((left, right) -> {
                        boolean leftMarked = isCompatibleShellSave(left);
                        boolean rightMarked = isCompatibleShellSave(right);
                        if (leftMarked != rightMarked) return leftMarked ? -1 : 1;
                        return Long.compare(modified(right), modified(left));
                    })
                    .findFirst()
                    .orElse(null);

            if (legacy == null) return null;

            // Older recovery builds may already have produced a suffixed shell.
            // Adopt it instead of creating another one. When possible migrate it
            // into the one canonical reserved directory.
            Path migrated = migrateLegacyShellToCanonical(legacy, canonical);
            String id = migrated.getFileName().toString();
            ensureShellMarker(id);
            return id;
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not scan for the reserved shell world: {}", exception.toString());
            return null;
        }
    }

    private static boolean isCompatibleShellSave(Path path) {
        if (path == null) return false;
        Path marker = path.resolve(MARKER);
        if (!Files.isRegularFile(marker)) return false;
        try {
            var parsed = JsonParser.parseString(Files.readString(marker, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return false;
            JsonObject object = parsed.getAsJsonObject();
            boolean shell = object.has("dai_shell")
                    && !object.get("dai_shell").isJsonNull()
                    && object.get("dai_shell").getAsBoolean();
            int schema = object.has("schema")
                    ? object.get("schema").getAsInt()
                    : 0;
            return shell && schema >= SHELL_MARKER_SCHEMA;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void writeShellMarker() {
        Path candidate = savesDirectory().resolve(expectedSaveId);
        if (!Files.isRegularFile(candidate.resolve("level.dat"))) {
            return;
        }
        writeShellMarker(candidate);
    }

    private static void ensureShellMarker(String saveId) {
        if (!isReservedShellName(saveId)) return;
        Path candidate = savesDirectory().resolve(saveId);
        if (!Files.isRegularFile(candidate.resolve("level.dat"))) return;

        if (isCompatibleShellSave(candidate)) {
            expectedSaveId = saveId;
            return;
        }

        writeShellMarker(candidate);
    }

    private static void writeShellMarker(Path candidate) {
        if (candidate == null || !Files.isRegularFile(candidate.resolve("level.dat"))) return;
        try {
            Path marker = candidate.resolve(MARKER);
            Files.createDirectories(marker.getParent());
            JsonObject rootJson = new JsonObject();
            rootJson.addProperty("schema", SHELL_MARKER_SCHEMA);
            rootJson.addProperty("dai_shell", true);
            rootJson.addProperty("display_name", SHELL_DISPLAY_NAME);
            Files.writeString(marker, rootJson.toString(), StandardCharsets.UTF_8);
            expectedSaveId = candidate.getFileName().toString();
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not write shell-world marker: {}", exception.toString());
        }
    }

    private static Path migrateLegacyShellToCanonical(Path legacy, Path canonical) {
        if (legacy == null || canonical == null) return legacy;
        if (legacy.equals(canonical)) return canonical;
        if (Files.exists(canonical)) return legacy;

        try {
            Path moved = Files.move(legacy, canonical);
            DAI_Core.LOGGER.info(
                    "<DAI>: Migrated legacy reserved shell '{}' to canonical internal slot '{}'.",
                    legacy.getFileName(),
                    canonical.getFileName()
            );
            return moved;
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not migrate legacy reserved shell '{}' to '{}'; reusing it in place: {}",
                    legacy.getFileName(),
                    canonical.getFileName(),
                    exception.toString()
            );
            return legacy;
        }
    }

    private static void prepareReservedSaveSlot() {
        Path canonical = savesDirectory().resolve(SHELL_SAVE_BASE);
        if (!Files.exists(canonical)) return;
        if (Files.isRegularFile(canonical.resolve("level.dat"))) return;

        // Only incomplete DAI infrastructure is removed here. A real level.dat
        // is never deleted merely to make room for world creation.
        deleteReservedShellSave(SHELL_SAVE_BASE);
    }

    private static boolean deleteReservedShellSave(String saveId) {
        if (!isReservedShellName(saveId)) return false;
        Path root = savesDirectory().toAbsolutePath().normalize();
        Path target = root.resolve(saveId).toAbsolutePath().normalize();
        if (!target.getParent().equals(root) || !Files.exists(target)) return true;

        try {
            java.util.List<Path> paths;
            try (var walk = Files.walk(target)) {
                paths = walk.sorted(Comparator.reverseOrder()).toList();
            }
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
            return true;
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not clear reserved shell save '{}': {}",
                    saveId,
                    exception.toString()
            );
            return false;
        }
    }

    private static boolean isReservedShellName(String saveId) {
        if (saveId == null || saveId.isBlank()) return false;

        /*
         * Minecraft may disambiguate a save directory with either underscore
         * or parenthetical suffixes depending on the creation path/build.
         * The entire prefix is DAI-reserved infrastructure. Recognizing all of
         * it lets the persistent-shell lifecycle adopt/migrate old
         * DAI_Engine_Shell (N) saves instead of ever creating another hub.
         */
        return saveId.regionMatches(
                true,
                0,
                SHELL_SAVE_BASE,
                0,
                SHELL_SAVE_BASE.length()
        );
    }

    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (Exception ignored) { return Long.MIN_VALUE; }
    }

    private static Path savesDirectory() {
        Path game = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        return game.resolve("saves");
    }

    private static boolean isWorldLoadingScreen(Screen screen) {
        if (screen == null) return false;
        String name = screen.getClass().getSimpleName();
        return "LevelLoadingScreen".equals(name) || "ReceivingLevelScreen".equals(name);
    }

    private static boolean isCreateWorldScreen(Screen screen) {
        return screen != null && screen.getClass().getName().equals(
                "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen"
        );
    }

    private static boolean invokeCreate(Screen screen) {
        for (Method method : screen.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() != 0) continue;
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!name.equals("oncreate") && !name.equals("createworld") && !name.equals("create")) continue;
            try {
                if (!method.canAccess(screen) && !method.trySetAccessible()) continue;
                method.invoke(screen);
                return true;
            } catch (Throwable exception) {
                DAI_Core.LOGGER.debug("<DAI>: Shell Create invocation rejected: {}", exception.toString());
                return false;
            }
        }
        return false;
    }

    private static boolean invokeEnumSetter(Object target, String preferredName, String enumName) {
        if (target == null) return false;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equalsIgnoreCase(preferredName) || method.getParameterCount() != 1) continue;
            Class<?> type = method.getParameterTypes()[0];
            if (!type.isEnum()) continue;
            Object[] constants = type.getEnumConstants();
            if (constants == null) continue;
            for (Object constant : constants) {
                if (constant instanceof Enum<?> value && value.name().equalsIgnoreCase(enumName)) {
                    try { method.invoke(target, constant); return true; }
                    catch (Throwable ignored) { return false; }
                }
            }
        }
        return false;
    }

    private static boolean invokeCompatibleSetter(Object target, String preferredName, Object value) {
        if (target == null || value == null) return false;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equalsIgnoreCase(preferredName) || method.getParameterCount() != 1) continue;
            Class<?> type = method.getParameterTypes()[0];
            if (type == String.class || type.isInstance(value)) {
                try { method.invoke(target, value); return true; }
                catch (Throwable ignored) { return false; }
            }
        }
        return false;
    }

    private static Object[] resolve(
            Class<?>[] types,
            Screen parent,
            Minecraft minecraft,
            String saveId,
            Runnable runnable
    ) {
        Object[] values = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (Screen.class.isAssignableFrom(type)) values[i] = parent;
            else if (Minecraft.class.isAssignableFrom(type)) values[i] = minecraft;
            else if (type == String.class) values[i] = saveId;
            else if (type == boolean.class || type == Boolean.class) values[i] = false;
            else if (Runnable.class.isAssignableFrom(type)) values[i] = runnable == null ? (Runnable) () -> {} : runnable;
            else return null;
        }
        return values;
    }

    private static boolean hasBlockingOverlay(Minecraft minecraft) {
        if (minecraft == null) return false;
        Object overlay = invokeNoArg(minecraft, "getOverlay");
        if (overlay != null) return true;
        try {
            for (var field : minecraft.getClass().getDeclaredFields()) {
                if (!field.getType().getSimpleName().endsWith("Overlay")) continue;
                if (!field.canAccess(minecraft) && !field.trySetAccessible()) continue;
                if (field.get(minecraft) != null) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) { }
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            if (!method.canAccess(target) && !method.trySetAccessible()) return null;
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void reset(boolean keepLevel) {
        if (pendingWorldHandoff != null) clearPendingWorldHandoff();
        state = State.IDLE;
        bootParent = null;
        createTicks = 0;
        readyTicks = 0;
        waitTicks = 0;
        shellRevealTicks = 0;
        createInvoked = false;
        shellConfirmationAccepted = false;
        waitingFromExisting = false;
        rebuildAttemptedThisBoot = false;
        if (!keepLevel) shellLevel = null;
    }
}
