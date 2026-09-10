package io.github.j12h36h.dai.client.presentation.screen;

import io.github.j12h36h.dai.client.presentation.scene.DAI_SceneRenderer;
import io.github.j12h36h.dai.client.screens.data.DAI_DataScreen;
import io.github.j12h36h.dai.client.screens.data.DAI_DataScreenDefinition;
import io.github.j12h36h.dai.client.screens.data.DAI_DataScreenRegistry;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.presentation.screen.DAI_ScreenOverrideDefinition;
import io.github.j12h36h.dai.presentation.screen.DAI_ScreenOverrideRegistry;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Applies pack-defined replacement/skin rules to arbitrary vanilla or modded screens. */
public final class DAI_ScreenOverrideRuntime {
    private static final Set<Screen> BYPASS = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean initialized;

    private DAI_ScreenOverrideRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Opening.class, DAI_ScreenOverrideRuntime::onOpening);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Background.class, DAI_ScreenOverrideRuntime::onBackground);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, DAI_ScreenOverrideRuntime::onForeground);
    }

    private static void onOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (next == null || BYPASS.remove(next) || DAI_ScreenOverrideRegistry.isEmpty()) return;
        DAI_ScreenOverrideRegistry.Match match = DAI_ScreenOverrideRegistry.resolve(next);
        if (match == null) return;
        DAI_ScreenOverrideDefinition definition = match.definition();
        String mode = definition.mode();
        if (!mode.equals("replace")) return;

        String screenId = definition.replacementScreen();
        DAI_DataScreenDefinition screen = DAI_DataScreenRegistry.get(screenId);
        if (screen == null) {
            DAI_Core.LOGGER.warn("<DAI>: Screen override '{}' requested missing data screen '{}'.", match.id(), screenId);
            return;
        }
        Screen replacement = new DAI_DataScreen(screenId, screen);
        BYPASS.add(replacement);
        event.setNewScreen(replacement);
    }

    private static void onBackground(ScreenEvent.Render.Background event) {
        DAI_ScreenOverrideRegistry.Match match = DAI_ScreenOverrideRegistry.resolve(event.getScreen());
        if (match == null || match.definition().mode().equals("replace")) return;
        String scene = match.definition().backgroundScene();
        if (scene.isBlank()) return;
        Screen screen = event.getScreen();
        DAI_SceneRenderer.render(event.getGuiGraphics(), scene, 0, 0, screen.width, screen.height,
                event.getPartialTick(), screenVariables(screen));
    }

    private static void onForeground(ScreenEvent.Render.Post event) {
        DAI_ScreenOverrideRegistry.Match match = DAI_ScreenOverrideRegistry.resolve(event.getScreen());
        if (match == null || match.definition().mode().equals("replace")) return;
        String scene = match.definition().foregroundScene();
        if (scene.isBlank()) return;
        Screen screen = event.getScreen();
        DAI_SceneRenderer.render(event.getGuiGraphics(), scene, 0, 0, screen.width, screen.height,
                event.getPartialTick(), screenVariables(screen));
    }

    private static Map<String, Object> screenVariables(Screen screen) {
        String title = "";
        try { if (screen.getTitle() != null) title = screen.getTitle().getString(); }
        catch (RuntimeException ignored) {}
        return Map.of(
                "screen.class", screen.getClass().getName(),
                "screen.simple_class", screen.getClass().getSimpleName(),
                "screen.title", title,
                "screen.width", screen.width,
                "screen.height", screen.height
        );
    }
}
