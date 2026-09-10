package io.github.j12h36h.dai.client.logics.core;

import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.comiclife.ComicLifeRuntime;
import io.github.j12h36h.dai.client.logics.DAI_AutomationLogic;
import io.github.j12h36h.dai.client.learning.DAI_LearningRuntime;
import io.github.j12h36h.dai.client.logics.DAI_CreativeInputState;
import io.github.j12h36h.dai.client.logics.input.DAI_InputReactionBridge;
import io.github.j12h36h.dai.client.logics.input.DAI_VehicleInputBridge;
import io.github.j12h36h.dai.client.logics.input.DAI_KeybindStateTracker;
import io.github.j12h36h.dai.client.logics.input.DAI_RawKeyStateTracker;
import io.github.j12h36h.dai.client.logics.input.DAI_MouseState;
import io.github.j12h36h.dai.client.animations.DAI_AnimationRuntime;
import io.github.j12h36h.dai.client.animations.eras.DAI_ErasCinematicRuntime;
import io.github.j12h36h.dai.client.content.DAI_ContentRuntime;
import io.github.j12h36h.dai.client.combat.DAI_MusashiDirectionalCombat;
import io.github.j12h36h.dai.client.customization.DAI_GameCustomizationLogic;
import io.github.j12h36h.dai.client.experience.DAI_ExperienceRuntime;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionGovernor;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.condition.DAI_ConditionMemory;
import io.github.j12h36h.dai.client.logics.controller.DAI_ApproachController;
import io.github.j12h36h.dai.client.logics.controller.DAI_BreakController;
import io.github.j12h36h.dai.client.logics.controller.DAI_BuildController;
import io.github.j12h36h.dai.client.logics.controller.DAI_CombatController;
import io.github.j12h36h.dai.client.logics.controller.DAI_CreativeBuildController;
import io.github.j12h36h.dai.client.logics.controller.DAI_CreativeFlightController;
import io.github.j12h36h.dai.client.logics.controller.DAI_ExploreController;
import io.github.j12h36h.dai.client.logics.controller.DAI_InteractionController;
import io.github.j12h36h.dai.client.logics.controller.DAI_ItemController;
import io.github.j12h36h.dai.client.logics.controller.DAI_LookController;
import io.github.j12h36h.dai.client.logics.controller.DAI_MoveController;
import io.github.j12h36h.dai.client.logics.controller.DAI_PathController;
import io.github.j12h36h.dai.client.logics.controller.DAI_ScaffoldController;
import io.github.j12h36h.dai.client.logics.controller.DAI_UseController;
import io.github.j12h36h.dai.client.menus.system.DAI_ClientRuntime;
import io.github.j12h36h.dai.client.menus.DAI_ScreenManager;
import io.github.j12h36h.dai.client.overlays.DAI_OverlayManager;
import io.github.j12h36h.dai.logics.action.DAI_ActionLibrary;
import io.github.j12h36h.dai.registry.DAI_RegistryPreflight;
import io.github.j12h36h.dai.client.registry.DAI_RegistryClientNotice;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Main DAI client tick dispatcher with feature-module gating. */
public final class DAI_ClientTick {

    private static final Identifier COMIC_LIFE_MARKER =
            Identifier.fromNamespaceAndPath("comiclife", "open");

    private static boolean sessionActive;
    private static boolean comicLifeDispatched;

    private DAI_ClientTick() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean playerAvailable = minecraft.player != null && minecraft.level != null;

        if (sessionActive && !playerAvailable) {
            resetSessionState();
            sessionActive = false;
            DAI_Core.debug("<DAI>: Client gameplay session ended.");
            return;
        }

        if (!playerAvailable) {
            DAI_ClientRuntime.tick();
            return;
        }

        sessionActive = true;

        /* Core UI/screen/registry coordination remains intentionally small and
         * always available. Feature work below is skipped by module. */
        DAI_ClientRuntime.tick();
        if (enabled("experience")) DAI_ExperienceRuntime.tick();
        DAI_ScreenManager.tick();
        if (enabled("overlays")) DAI_OverlayManager.tick();
        DAI_RegistryClientNotice.tick();

        /* Do not even resolve/load ComicLifeRuntime unless a ComicLife-capable
         * pack has contributed its marker action. This keeps the module's
         * archive/compiler/milestone statics unallocated on projects that do
         * not use ComicLife. */
        boolean comicLifeRequested = enabled("comic_life")
                && DAI_ActionLibrary.contains(COMIC_LIFE_MARKER);
        if (comicLifeRequested) {
            comicLifeDispatched = true;
            ComicLifeRuntime.tick();
        } else if (comicLifeDispatched) {
            ComicLifeRuntime.shutdown();
            comicLifeDispatched = false;
        }

        if (DAI_RegistryPreflight.restartRequired()) return;

        if (enabled("animations")) DAI_AnimationRuntime.tick();
        if (enabled("cinematics")) DAI_ErasCinematicRuntime.tick();
        if (enabled("content")) DAI_ContentRuntime.tick();
        if (enabled("customization")) DAI_GameCustomizationLogic.tick();
        if (enabled("combat")) DAI_MusashiDirectionalCombat.tick();

        /* Input state used by core authored actions stays available; only
         * optional reaction/vehicle bridges are removed from the tick path. */
        DAI_MouseState.tick();
        DAI_KeybindStateTracker.tick();
        DAI_RawKeyStateTracker.tick();
        if (enabled("reactions")) DAI_InputReactionBridge.tick();
        if (enabled("vehicles")) DAI_VehicleInputBridge.tick();

        if (enabled("learning")) DAI_LearningRuntime.tick();

        if (enabled("navigation")) {
            DAI_PathController.tick();
            DAI_ExploreController.tick();
        }

        if (enabled("world_editing")) DAI_ScaffoldController.tick();

        if (enabled("creative")) {
            DAI_CreativeFlightController.tick();
            if (enabled("world_editing")) DAI_CreativeBuildController.tick();
        }

        DAI_ConditionMemory.tick();
        DAI_ActionGovernor.tick();

        if (enabled("automation")) DAI_AutomationLogic.tickWatchdog();

        if (!DAI_BreakController.isActive()) DAI_ActionQueue.tick();

        if (enabled("navigation")) DAI_MoveController.tick();
        if (enabled("combat")) DAI_CombatController.tick();
        if (enabled("interaction")) {
            DAI_UseController.tick();
            DAI_InteractionController.tick();
        }
        if (enabled("inventory")) DAI_ItemController.tick();
        if (enabled("world_editing")) {
            DAI_BreakController.tick();
            DAI_BuildController.tick();
        }

        if (enabled("navigation")) {
            DAI_ApproachController.tick();
            if (!DAI_BreakController.isActive()) DAI_LookController.tick();
        }

        DAI_RuntimeTelemetry.tick();
        DAI_Debug.tick();

        if (enabled("creative")) DAI_CreativeInputState.tick();
        DAI_MouseState.finishTick();
    }

    public static void reset() {
        sessionActive = false;
        resetSessionState();
    }

    private static void resetSessionState() {
        DAI_ClientRuntime.resetSession();
        DAI_OverlayManager.clear();
        DAI_ErasCinematicRuntime.clear();
        DAI_GameCustomizationLogic.clearState();
        DAI_MusashiDirectionalCombat.reset();
        DAI_KeybindStateTracker.reset();
        DAI_RawKeyStateTracker.reset();
        DAI_MouseState.reset();
        DAI_LearningRuntime.resetSession();
        if (comicLifeDispatched) {
            ComicLifeRuntime.shutdown();
            comicLifeDispatched = false;
        }
    }

    private static boolean enabled(String module) {
        return DAI_Config.featureModuleEnabled(module);
    }
}
