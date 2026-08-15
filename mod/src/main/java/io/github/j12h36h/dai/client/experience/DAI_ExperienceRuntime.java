package io.github.j12h36h.dai.client.experience;

import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.experience.DAI_ExperienceLaunchState;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionResolver;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.menus.system.DAI_ClientRuntime;
import io.github.j12h36h.dai.registry.DAI_RegistryPreflight;
import io.github.j12h36h.dai.worldgen.DAI_WorldgenDefinition;
import io.github.j12h36h.dai.worldgen.DAI_WorldgenRepository;
import net.minecraft.client.Minecraft;
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
        if (definition == null) return;
        DAI_ExperienceLaunchState.prepare(definition, firstJoin, sourcePack);
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
        if (value.firstJoin() && !active.worldgen().isBlank()) {
            DAI_WorldgenRepository.reload();
            DAI_WorldgenDefinition worldgen = DAI_WorldgenRepository.get(active.worldgen());
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
        return definition != null
                && definition.ui().graveCursorToggle()
                && !definition.ui().openDaiMenuOnGrave();
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
