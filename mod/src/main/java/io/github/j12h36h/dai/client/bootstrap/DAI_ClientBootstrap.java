package io.github.j12h36h.dai.client.bootstrap;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.j12h36h.dai.client.entity.DAI_EntityClientBootstrap;
import io.github.j12h36h.dai.animations.eras.DAI_ErasCinematicLoader;
import io.github.j12h36h.dai.client.animations.eras.DAI_ErasCinematicRuntime;
import io.github.j12h36h.dai.client.network.DAI_ClientNetworkBootstrap;
import io.github.j12h36h.dai.client.particle.DAI_ParticleClientBootstrap;
import io.github.j12h36h.dai.client.input.DAI_InputProfileRuntime;
import io.github.j12h36h.dai.client.combat.indicator.DAI_DamageIndicatorRenderRuntime;
import io.github.j12h36h.dai.client.physics.DAI_ClientPhysicsRuntime;
import io.github.j12h36h.dai.client.physics.DAI_PhysicsRenderRuntime;
import io.github.j12h36h.dai.client.branding.DAI_ClientBranding;
import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import io.github.j12h36h.dai.client.experience.DAI_ExperienceRuntime;
import io.github.j12h36h.dai.client.logics.core.DAI_ClientTick;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.logics.input.DAI_KeybindRegistry;
import io.github.j12h36h.dai.client.logics.input.DAI_KeyMappings;
import io.github.j12h36h.dai.client.menus.DAI_MenuCore;
import io.github.j12h36h.dai.client.menus.system.DAI_ClientRuntime;
import io.github.j12h36h.dai.client.overlays.DAI_OverlayManager;
import io.github.j12h36h.dai.client.packs.DAI_ManagedResourcePackBootstrap;
import io.github.j12h36h.dai.client.presentation.screen.DAI_ScreenOverrideRuntime;
import io.github.j12h36h.dai.presentation.scene.DAI_SceneLoader;
import io.github.j12h36h.dai.input.DAI_InputProfileLoader;
import io.github.j12h36h.dai.client.title.DAI_TitleScreenController;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public final class DAI_ClientBootstrap {

    private static final KeyMapping.Category DAI_CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(DAI_Core.MODID, "general"));

    private static final KeyMapping MENU_KEY =
            new KeyMapping("key.decisions_and_impulses.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, DAI_CATEGORY);

    private DAI_ClientBootstrap() {}

    public static void initialize(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        if (DAI_Config.featureModuleEnabled("managed_packs")) {
            DAI_ManagedResourcePackBootstrap.initialize(modBus);
        }

        modBus.addListener(DAI_ClientBootstrap::onClientSetup);

        if (DAI_Config.featureModuleEnabled("entities")) DAI_EntityClientBootstrap.initialize(modBus);
        if (DAI_Config.featureModuleEnabled("particles")) DAI_ParticleClientBootstrap.initialize(modBus);
        if (DAI_Config.featureModuleEnabled("physics")) DAI_PhysicsRenderRuntime.initialize(modBus);
        if (DAI_Config.featureModuleEnabled("combat")) DAI_DamageIndicatorRenderRuntime.initialize(modBus);

        modBus.addListener(DAI_ClientBootstrap::registerKeyMappings);
        modBus.addListener(DAI_ClientBootstrap::registerGuiLayers);

        if (DAI_Config.featureModuleEnabled("cinematics")) {
            modBus.addListener(DAI_ClientBootstrap::registerCinematicReloadListeners);
        }
        if (DAI_Config.featureModuleEnabled("scene_environments")) {
            modBus.addListener(DAI_ClientBootstrap::registerSceneReloadListeners);
        }
        if (DAI_Config.featureModuleEnabled("input_profiles")) {
            modBus.addListener(DAI_ClientBootstrap::registerInputProfileReloadListeners);
        }

        modBus.addListener(DAI_ClientNetworkBootstrap::register);
        DAI_Core.LOGGER.info("<DAI>: Client bootstrap initialized with feature-module gating.");
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        if (DAI_Config.featureModuleEnabled("overlays")) {
            event.registerAboveAll(
                    Identifier.fromNamespaceAndPath(DAI_Core.MODID, "custom_overlays"),
                    DAI_OverlayManager::extractHud
            );
        }
        if (DAI_Config.featureModuleEnabled("cinematics")) {
            event.registerAboveAll(DAI_ErasCinematicRuntime.GUI_LAYER, DAI_ErasCinematicRuntime::extractHud);
        }
    }

    private static void registerSceneReloadListeners(AddClientReloadListenersEvent event) {
        if (!DAI_Config.featureModuleEnabled("scene_environments")) return;
        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "scene_environments"),
                new DAI_SceneLoader()
        );
    }

    private static void registerInputProfileReloadListeners(AddClientReloadListenersEvent event) {
        if (!DAI_Config.featureModuleEnabled("input_profiles")) return;
        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "input_profiles"),
                new DAI_InputProfileLoader()
        );
    }

    private static void registerCinematicReloadListeners(AddClientReloadListenersEvent event) {
        if (!DAI_Config.featureModuleEnabled("cinematics")) return;
        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "eras_cinematics"),
                new DAI_ErasCinematicLoader(DAI_ErasCinematicLoader.Source.CLIENT_RESOURCES)
        );
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(DAI_CATEGORY);
        event.register(MENU_KEY);

        Set<String> registeredCategories = new HashSet<>();
        for (var entry : DAI_KeybindRegistry.snapshot().entrySet()) {
            String id = entry.getKey();
            var definition = entry.getValue();
            int colon = id.indexOf(':');
            String namespace = colon > 0 ? id.substring(0, colon) : DAI_Core.MODID;
            String path = colon > 0 ? id.substring(colon + 1) : id;
            String categoryPath = definition.category().replace(':', '/').replace('.', '/');
            KeyMapping.Category category = new KeyMapping.Category(
                    Identifier.fromNamespaceAndPath(namespace, categoryPath)
            );
            String categoryId = namespace + ":" + categoryPath;
            if (registeredCategories.add(categoryId)) {
                try { event.registerCategory(category); } catch (RuntimeException ignored) {}
            }
            String translation = "key." + namespace + "." + path.replace('/', '.');
            KeyMapping mapping = new KeyMapping(
                    translation, InputConstants.Type.KEYSYM, definition.keyCode(), category
            );
            event.register(mapping);
            DAI_KeyMappings.registerDynamic(id, mapping);
        }

        DAI_Core.debug("<DAI>: Registered DAI key mappings with module filtering.");
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, DAI_ClientBootstrap::onClientTick);
        if (DAI_Config.featureModuleEnabled("screen_overrides")) DAI_ScreenOverrideRuntime.initialize();

        if (DAI_Config.featureModuleEnabled("input_profiles")) {
            NeoForge.EVENT_BUS.addListener(DAI_InputProfileRuntime::onRenderHand);
        }
        if (DAI_Config.featureModuleEnabled("physics")) {
            NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeCameraAngles.class, DAI_ClientPhysicsRuntime::onCameraAngles);
        }
        if (DAI_Config.featureModuleEnabled("cinematics")) {
            NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeCameraAngles.class, DAI_ErasCinematicRuntime::onCameraAngles);
            NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeFov.class, DAI_ErasCinematicRuntime::onFov);
            NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, DAI_ErasCinematicRuntime::onRenderGuiLayer);
        }

        DAI_Core.debug("<DAI>: Registered post-client-tick listener with module filtering.");
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (DAI_Config.featureModuleEnabled("experience")) DAI_ExperienceLauncher.tickFreshLaunch();

        if (DAI_Config.featureModuleEnabled("title_branding")) {
            DAI_TitleScreenController.tick();
            DAI_ClientBranding.tick();
        }

        while (MENU_KEY.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();

            if (DAI_Config.featureModuleEnabled("experience")
                    && minecraft.player != null
                    && DAI_ExperienceRuntime.handleGraveKey()) {
                continue;
            }

            if (minecraft.player != null && !(minecraft.gui.screen() instanceof DAI_MenuCore)) {
                minecraft.gui.setScreen(new DAI_MenuCore());
                DAI_InputState.setCursorReleased(true);
                DAI_ClientRuntime.updateMouseCapture();
                DAI_Core.debug("<DAI>: DAI menu opened.");
            }
        }

        if (DAI_Config.featureModuleEnabled("physics")) DAI_ClientPhysicsRuntime.tick();
        DAI_ClientTick.tick();
    }
}
