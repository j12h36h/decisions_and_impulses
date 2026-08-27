package io.github.j12h36h.dai.client.experience;

import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.experience.DAI_ExperienceLaunchState;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionResolver;
import io.github.j12h36h.dai.client.overlays.DAI_OverlayManager;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.menus.DAI_MenuCore;
import io.github.j12h36h.dai.client.menus.system.DAI_ClientRuntime;
import io.github.j12h36h.dai.registry.DAI_RegistryPreflight;
import io.github.j12h36h.dai.worldgen.DAI_WorldgenDefinition;
import io.github.j12h36h.dai.worldgen.DAI_WorldgenRepository;
import net.minecraft.client.Minecraft;
import io.github.j12h36h.dai.client.screens.data.DAI_DataScreen;
import net.minecraft.resources.Identifier;

/** Client/session ownership for a launched JSON experience. */
public final class DAI_ExperienceRuntime {

    private static final int ACTION_HANDOFF_GRACE_TICKS = 200;

    private static volatile DAI_ExperienceDefinition active;
    private static boolean clientReady;
    private static int activationWaitTicks;
    private static boolean missingActionLogged;

    private DAI_ExperienceRuntime() {}

    public static void prepare(DAI_ExperienceDefinition definition, boolean firstJoin) {
        prepare(definition, firstJoin, null);
    }

    public static void prepare(
            DAI_ExperienceDefinition definition,
            boolean firstJoin,
            java.nio.file.Path sourcePack
    ) {
        prepare(definition, firstJoin, sourcePack, "");
    }

    public static void prepare(
            DAI_ExperienceDefinition definition,
            boolean firstJoin,
            java.nio.file.Path sourcePack,
            String worldgenOverride
    ) {
        if (definition == null) return;
        DAI_ExperienceLaunchState.prepare(definition, firstJoin, sourcePack, worldgenOverride);
        active = null;
        clientReady = false;
        activationWaitTicks = 0;
        missingActionLogged = false;
        DAI_Core.LOGGER.info(
                "<DAI>: Prepared experience '{}' (firstJoin={}, sourcePack={}).",
                definition.id(),
                firstJoin,
                sourcePack == null ? "<none>" : sourcePack
        );
    }

    /** Called after DAI resets its normal per-world client state. */
    public static void onClientReady(Minecraft minecraft) {
        clientReady = true;
        tryActivate(minecraft);
    }

    /** Retries experience activation while a freshly-installed datapack reload finishes. */
    public static void tick() {
        if (!clientReady || active != null) return;
        tryActivate(Minecraft.getInstance());
    }

    private static void tryActivate(Minecraft minecraft) {
        DAI_ExperienceLaunchState.Pending value = DAI_ExperienceLaunchState.pending();
        if (value == null || minecraft == null || minecraft.player == null || minecraft.level == null) return;

        if (!DAI_ExperienceLaunchState.packReady()) {
            return;
        }

        // Never consume/complete an experience startup while DAI has armed its
        // registry safety gate. The pending launch remains repairable after a
        // restart or a corrected reload.
        if (DAI_RegistryPreflight.restartRequired()) {
            return;
        }

        // First-join gameplay must not outrun server-side platform/structure
        // bootstrap. This closes the pack-reload/worldgen/client activation race.
        if (value.firstJoin() && !DAI_ExperienceLaunchState.worldReady()) {
            return;
        }

        DAI_ExperienceDefinition definition = value.definition();
        String action = value.firstJoin() && !definition.onFirstJoin().isBlank()
                ? definition.onFirstJoin()
                : definition.onJoin();

        // A fresh experience may have copied its authoring datapack into the
        // new save after the server's initial resource pass. Do not resolve
        // the first-join action until that reload has populated DAI's action
        // library. This prevents the old "Unknown action" race.
        if (!action.isBlank() && !actionAvailable(action)) {
            activationWaitTicks++;
            if (activationWaitTicks < ACTION_HANDOFF_GRACE_TICKS) return;

            if (!missingActionLogged) {
                missingActionLogged = true;
                DAI_Core.LOGGER.error(
                        "<DAI>: Experience '{}' could not resolve startup action '{}' after datapack handoff{}.",
                        definition.id(),
                        action,
                        DAI_ExperienceLaunchState.packReloadFailed() ? " (pack reload failed)" : ""
                );
            }
            return;
        }

        DAI_ExperienceLaunchState.consumeClient();
        active = definition;
        activationWaitTicks = 0;
        missingActionLogged = false;

        // Experience lifecycle actions are gameplay, not UI. UI auto-enable
        // must never suppress first-join/on-join logic.
        String activeWorldgen = value.worldgenOverride().isBlank() ? active.worldgen() : value.worldgenOverride();
        if (value.firstJoin() && !activeWorldgen.isBlank()) {
            DAI_WorldgenRepository.reload();
            DAI_WorldgenDefinition worldgen = DAI_WorldgenRepository.get(activeWorldgen);
            if (worldgen != null) {
                for (String actionId : worldgen.bootstrapActions()) {
                    DAI_ActionQueue.enqueueAll(DAI_ActionResolver.resolve(actionId));
                }
            }
        }

        if (!action.isBlank()) {
            DAI_ActionQueue.enqueueAll(DAI_ActionResolver.resolve(action));
        }

        if (value.firstJoin()) {
            DAI_ActionQueue.enqueue(new DAI_ActionDefinition(
                    "server_mark_experience_started",
                    definition.id(),
                    java.util.List.of(),
                    java.util.List.of(),
                    "", "",
                    0.0F, 0.0F,
                    "", 0, 0
            ));
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Activated experience '{}' with experience UI mode={}.",
                active.id(),
                active.ui().graveCursorToggle()
        );
    }

    private static boolean actionAvailable(String raw) {
        Identifier id = Identifier.tryParse(raw);
        return id != null && DAI_ActionLibrary.get(id) != null;
    }

    public static boolean hasActiveExperience() {
        return active != null;
    }

    public static DAI_ExperienceDefinition active() {
        return active;
    }

    public static boolean interceptsGraveKey() {
        DAI_ExperienceDefinition definition = active;
        if (definition == null || !definition.ui().graveCursorToggle()) {
            return false;
        }

        DAI_ExperienceDefinition.Ui ui = definition.ui();
        return !ui.openDaiMenuOnGrave() || hasTargetGraveMenu(ui);
    }

    /**
     * Handles the grave key for an active experience.
     *
     * Experiences may replace DAI's normal menu with datapack-authored UI by
     * declaring grave_open_action / grave_close_action. If those fields are
     * omitted, DAI also recognizes the conventional <namespace>:open and
     * <namespace>:close actions for backwards compatibility with existing
     * experience packs such as TamaCrafti.
     */
    public static boolean handleGraveKey() {
        if (!interceptsGraveKey()) {
            return false;
        }

        DAI_ExperienceDefinition definition = active;
        DAI_ExperienceDefinition.Ui ui = definition.ui();

        if (ui.openDaiMenuOnGrave() && hasTargetGraveMenu(ui)) {
            return openTargetedDaiMenu(definition, ui);
        }

        String openAction = resolveUiAction(ui.graveOpenAction(), "open");
        String closeAction = resolveUiAction(ui.graveCloseAction(), "close");

        if (!openAction.isBlank() && !closeAction.isBlank()) {
            boolean visible = isExperienceUiVisible(ui);
            String action = visible ? closeAction : openAction;
            java.util.List<DAI_ActionDefinition> resolved = DAI_ActionResolver.resolve(action);

            if (!resolved.isEmpty()) {
                // A UI toggle is explicit player control. Dispatch it ahead of
                // queued gameplay so opening/closing the interface is instant.
                DAI_ActionQueue.interruptAndDispatch(resolved);

                DAI_InputState.setCursorReleased(!visible);
                DAI_ClientRuntime.updateMouseCapture();

                DAI_Core.LOGGER.info(
                        "<DAI>: Experience '{}' {} UI from grave key using action '{}'.",
                        definition.id(),
                        visible ? "closed" : "opened",
                        action
                );
                return true;
            }
        }

        // Legacy behavior for experiences that only request cursor release.
        toggleCursorMode();
        return true;
    }


    private static boolean hasTargetGraveMenu(DAI_ExperienceDefinition.Ui ui) {
        return ui != null
                && !ui.graveMenu().isBlank()
                && !ui.graveMenuOpen().isBlank();
    }

    /**
     * Opens DAI's menu shell directly at an experience-owned menu definition.
     * This keeps the familiar DAI menu renderer/layout while allowing a game
     * experience to bypass the generic System/Actions navigation path.
     */
    private static boolean openTargetedDaiMenu(
            DAI_ExperienceDefinition definition,
            DAI_ExperienceDefinition.Ui ui
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return false;
        }

        DAI_MenuCore menu = new DAI_MenuCore();
        minecraft.gui.setScreen(menu);
        menu.updateMenu(ui.graveMenu(), ui.graveMenuOpen());

        DAI_InputState.setCursorReleased(true);
        DAI_ClientRuntime.updateMouseCapture();

        DAI_Core.LOGGER.info(
                "<DAI>: Experience '{}' opened targeted grave menu '{}:{}'.",
                definition.id(),
                ui.graveMenu(),
                ui.graveMenuOpen()
        );

        return true;
    }

    private static boolean isExperienceUiVisible(DAI_ExperienceDefinition.Ui ui) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gui.screen() instanceof DAI_DataScreen) {
            return true;
        }
        if (ui != null && !ui.graveAnchorOverlay().isBlank()) {
            return DAI_OverlayManager.contains(ui.graveAnchorOverlay());
        }

        // Backwards-compatible fallback for 1.8/1.8.1 experience packs that
        // predate grave_anchor_overlay. Their UI is normally composed entirely
        // of DAI overlays, so an empty overlay manager means it is closed.
        return DAI_OverlayManager.size() > 0;
    }

    private static String resolveUiAction(String configured, String conventionalPath) {
        if (configured != null && !configured.isBlank() && actionAvailable(configured)) {
            return configured.trim().toLowerCase();
        }

        DAI_ExperienceDefinition definition = active;
        if (definition == null || definition.id().isBlank()) {
            return "";
        }

        int colon = definition.id().indexOf(':');
        if (colon <= 0) {
            return "";
        }

        String conventional = definition.id().substring(0, colon) + ":" + conventionalPath;
        return actionAvailable(conventional) ? conventional : "";
    }

    /**
     * Keeps cursor capture synchronized when an experience UI opens or closes
     * itself through one of its clickable overlays.
     */
    public static void onOverlayActionDispatched(String actionId) {
        if (!interceptsGraveKey() || actionId == null || actionId.isBlank()) {
            return;
        }

        DAI_ExperienceDefinition.Ui ui = active.ui();
        String normalized = actionId.trim().toLowerCase();
        String openAction = resolveUiAction(ui.graveOpenAction(), "open");
        String closeAction = resolveUiAction(ui.graveCloseAction(), "close");

        if (!closeAction.isBlank() && normalized.equals(closeAction)) {
            DAI_InputState.setCursorReleased(false);
            DAI_ClientRuntime.updateMouseCapture();
            DAI_Core.debug("<DAI>: Experience UI close action recaptured the cursor.");
        } else if (!openAction.isBlank() && normalized.equals(openAction)) {
            DAI_InputState.setCursorReleased(true);
            DAI_ClientRuntime.updateMouseCapture();
            DAI_Core.debug("<DAI>: Experience UI open action released the cursor.");
        }
    }

    public static void toggleCursorMode() {
        if (!interceptsGraveKey()) return;
        DAI_InputState.setCursorReleased(!DAI_InputState.isCursorReleased());
        DAI_ClientRuntime.updateMouseCapture();
        DAI_Core.debug("<DAI>: Experience cursor mode toggled; released={}.", DAI_InputState.isCursorReleased());
    }

    public static void clearActive() {
        active = null;
        clientReady = false;
        activationWaitTicks = 0;
        missingActionLogged = false;
    }
}
