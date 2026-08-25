package io.github.j12h36h.dai.client.bootstrap;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.j12h36h.dai.client.entity.DAI_EntityClientBootstrap;
import io.github.j12h36h.dai.client.network.DAI_ClientNetworkBootstrap;
import io.github.j12h36h.dai.client.particle.DAI_ParticleClientBootstrap;
import io.github.j12h36h.dai.client.combat.DAI_MusashiDirectionalCombat;
import io.github.j12h36h.dai.client.physics.DAI_ClientPhysicsRuntime;
import io.github.j12h36h.dai.client.physics.DAI_PhysicsRenderRuntime;
import io.github.j12h36h.dai.client.branding.DAI_ClientBranding;
import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import io.github.j12h36h.dai.client.experience.DAI_ExperienceRuntime;
import io.github.j12h36h.dai.client.logics.core.DAI_ClientTick;
import io.github.j12h36h.dai.client.learning.DAI_LearningRuntime;
import io.github.j12h36h.dai.client.menus.learning.DAI_CompanionChatScreen;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.logics.input.DAI_KeybindRegistry;
import io.github.j12h36h.dai.client.logics.input.DAI_KeyMappings;
import io.github.j12h36h.dai.client.logics.input.DAI_VehicleInputBridge;
import io.github.j12h36h.dai.client.menus.DAI_MenuCore;
import io.github.j12h36h.dai.client.menus.DAI_ScreenManager;
import io.github.j12h36h.dai.client.menus.system.DAI_ClientRuntime;
import io.github.j12h36h.dai.client.overlays.DAI_OverlayManager;
import io.github.j12h36h.dai.client.packs.DAI_ManagedResourcePackBootstrap;
import io.github.j12h36h.dai.client.title.DAI_TitleScreenController;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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
            new KeyMapping.Category(
                    Identifier.fromNamespaceAndPath(
                            DAI_Core.MODID,
                            "general"
                    )
            );

    private static final KeyMapping MENU_KEY =
            new KeyMapping(
                    "key.decisions_and_impulses.menu",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_GRAVE_ACCENT,
                    DAI_CATEGORY
            );

    private static final KeyMapping COMPANION_CHAT_KEY =
            new KeyMapping(
                    "key.decisions_and_impulses.companion_chat",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_BACKSLASH,
                    DAI_CATEGORY
            );

    private DAI_ClientBootstrap() {
        // Utility class.
    }

    public static void initialize(
            IEventBus modBus,
            ModContainer container
    ) {

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                ConfigurationScreen::new
        );

        DAI_ManagedResourcePackBootstrap.initialize(modBus);

        modBus.addListener(
                DAI_ClientBootstrap::onClientSetup
        );

        DAI_EntityClientBootstrap.initialize(modBus);
        DAI_ParticleClientBootstrap.initialize(modBus);
        DAI_PhysicsRenderRuntime.initialize(modBus);

        modBus.addListener(
                DAI_ClientBootstrap::registerKeyMappings
        );

        modBus.addListener(
                DAI_ClientBootstrap::registerGuiLayers
        );

        modBus.addListener(DAI_ClientNetworkBootstrap::register);

        DAI_Core.LOGGER.info(
                "<DAI>: Client bootstrap initialized."
        );
    }


    private static void registerGuiLayers(
            RegisterGuiLayersEvent event
    ) {

        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(
                        DAI_Core.MODID,
                        "custom_overlays"
                ),
                DAI_OverlayManager::extractHud
        );
    }

    private static void registerKeyMappings(
            RegisterKeyMappingsEvent event
    ) {

        event.registerCategory(
                DAI_CATEGORY
        );

        event.register(
                MENU_KEY
        );

        event.register(
                COMPANION_CHAT_KEY
        );

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

        DAI_Core.debug(
                "<DAI>: Registered DAI menu keybind."
        );
    }

    private static void onClientSetup(
            FMLClientSetupEvent event
    ) {

        NeoForge.EVENT_BUS.addListener(
                ClientTickEvent.Post.class,
                DAI_ClientBootstrap::onClientTick
        );

        NeoForge.EVENT_BUS.addListener(
                DAI_MusashiDirectionalCombat::onRenderHand
        );

        NeoForge.EVENT_BUS.addListener(
                ViewportEvent.ComputeCameraAngles.class,
                DAI_ClientPhysicsRuntime::onCameraAngles
        );

        DAI_Core.debug(
                "<DAI>: Registered post-client-tick listener."
        );
    }

    private static void onClientTick(
            ClientTickEvent.Post event
    ) {

        DAI_ExperienceLauncher.tickFreshLaunch();
        DAI_TitleScreenController.tick();
        DAI_ClientBranding.tick();

        while (COMPANION_CHAT_KEY.consumeClick()) {

            Minecraft minecraft = Minecraft.getInstance();

            /* Open-only while in gameplay. Once the screen is open, the key
             * belongs to the EditBox instead of acting as a close toggle. */
            if (minecraft.player != null
                    && minecraft.gui.screen() == null
                    && DAI_LearningRuntime.available()) {
                minecraft.gui.setScreen(new DAI_CompanionChatScreen());
                DAI_Core.debug("<DAI>: Companion learning chat opened.");
            }
        }

        while (MENU_KEY.consumeClick()) {

            Minecraft minecraft =
                    Minecraft.getInstance();

            if (minecraft.player != null && DAI_ExperienceRuntime.handleGraveKey()) {
                continue;
            }

            if (
                    minecraft.player != null
                            && !(minecraft.gui.screen()
                            instanceof DAI_MenuCore)
            ) {

                minecraft.gui.setScreen(
                        new DAI_MenuCore()
                );

                DAI_InputState.setCursorReleased(
                        true
                );

                DAI_ClientRuntime.updateMouseCapture();

                DAI_Core.debug(
                        "<DAI>: DAI menu opened."
                );
            }
        }

        DAI_ClientPhysicsRuntime.tick();
        DAI_ClientTick.tick();
    }
}