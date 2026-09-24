package io.github.j12h36h.dai.client.menus;

import io.github.j12h36h.dai.client.config.DAI_ClientConfig;
import io.github.j12h36h.dai.client.creator.DAI_CreatorScreen;
import io.github.j12h36h.dai.client.packs.DAI_PackBrowserScreen;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellScreenRouter;
import io.github.j12h36h.dai.client.settings.DAI_SettingsScreen;
import io.github.j12h36h.dai.client.title.DAI_TitleActionDispatcher;
import io.github.j12h36h.dai.client.title.DAI_TitleScreen;
import io.github.j12h36h.dai.client.title.DAI_TitleScreenRepository;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Method;

/**
 * DAI Engine 4.3 in-game system shell. This replaces the default pause-menu
 * visual loop while leaving world save/disconnect behavior owned by Minecraft.
 */
public final class DAI_GameMenuScreen extends Screen {

    private static final int NODE_W = 112;
    private static final int NODE_H = 62;

    public DAI_GameMenuScreen() {
        super(Component.literal("DAI Engine"));
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int cy = height / 2;
        int dx = Math.max(118, Math.min(174, width / 5));
        int dy = Math.max(62, Math.min(86, height / 4));
        DAI_PresentationProfileService.Profile p = DAI_PresentationProfileService.selected();

        addNode(cx - NODE_W / 2, cy - dy - NODE_H / 2, "RESUME", button -> onClose(), p.primary());
        addNode(cx - dx - NODE_W / 2, cy - NODE_H / 2, "VANILLA", button ->
                Minecraft.getInstance().gui.setScreen(new DAI_VanillaPauseScreen(this)),
                p.secondary());
        addNode(cx + dx - NODE_W / 2, cy - NODE_H / 2, "CREATE", button -> {
            if (!DAI_ClientConfig.creatorEnabled()) return;
            if (DAI_ShellScreenRouter.vanilla(DAI_ShellScreenRouter.CREATOR)) {
                DAI_TitleActionDispatcher.openReflective(this, "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen");
            } else {
                DAI_ShellScreenRouter.open(
                        DAI_ShellScreenRouter.CREATOR,
                        this,
                        DAI_CreatorScreen::new,
                        () -> this
                );
            }
        }, p.primary());
        addNode(cx - NODE_W / 2, cy + dy - NODE_H / 2, "SETTINGS", button -> {
            if (DAI_ShellScreenRouter.vanilla(DAI_ShellScreenRouter.SETTINGS)) {
                DAI_TitleActionDispatcher.openReflective(this, "net.minecraft.client.gui.screens.options.OptionsScreen");
            } else {
                DAI_ShellScreenRouter.open(
                        DAI_ShellScreenRouter.SETTINGS,
                        this,
                        () -> new DAI_SettingsScreen(this),
                        () -> this
                );
            }
        }, p.secondary());

        int bottomY = Math.min(height - 30, cy + dy + 38);
        addRenderableWidget(new DAI_UniverseButton(
                Math.max(10, cx - 154), bottomY, 148, 22,
                Component.literal("RETURN TO TITLE"), button -> returnToTitle(),
                DAI_UniverseButton.Shape.CHIP, p.secondary()
        ));
        addRenderableWidget(new DAI_UniverseButton(
                Math.min(width - 158, cx + 6), bottomY, 148, 22,
                Component.literal("QUIT GAME"), button -> DAI_TitleActionDispatcher.stopMinecraft(),
                DAI_UniverseButton.Shape.CHIP, 0xFFE85B64
        ));
    }

    private void addNode(int x, int y, String label, Button.OnPress onPress, int accent) {
        addRenderableWidget(new DAI_UniverseButton(
                x, y, NODE_W, NODE_H, Component.literal(label), onPress,
                DAI_UniverseButton.Shape.NODE, accent
        ));
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile p = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, p, System.nanoTime());
        int cx = width / 2;
        int cy = height / 2;
        int dx = Math.max(118, Math.min(174, width / 5));
        int dy = Math.max(62, Math.min(86, height / 4));
        int connection = (p.secondary() & 0x00FFFFFF) | 0x44000000;
        DAI_UniverseShellRenderer.drawConnection(graphics, cx, cy, cx, cy - dy, connection);
        DAI_UniverseShellRenderer.drawConnection(graphics, cx, cy, cx - dx, cy, connection);
        DAI_UniverseShellRenderer.drawConnection(graphics, cx, cy, cx + dx, cy, connection);
        DAI_UniverseShellRenderer.drawConnection(graphics, cx, cy, cx, cy + dy, connection);
        graphics.centeredText(font, Component.literal("DAI ENGINE"), cx, 17, p.text());
        graphics.centeredText(font, Component.literal("SYSTEM UNIVERSE"), cx, 30, p.secondary());
        graphics.fill(cx - 3, cy - 3, cx + 4, cy + 4, p.primary());
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The universe renderer is the complete background.
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    /**
     * Disconnect through Minecraft's own method when available. Reflection
     * keeps this source compatible across adjacent mappings where the
     * disconnect signature changed. The DAI title shell is installed after
     * vanilla has completed its save/disconnect lifecycle.
     */
    public static void returnToTitle() {
        Minecraft minecraft = Minecraft.getInstance();
        DAI_ShellWorldRuntime.prepareReturnToShell();
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
                        // second boolean is stopSound. Returning to the DAI
                        // universe must not retain a previous world's pack
                        // context into the shell.
                        args[i] = booleanIndex++ > 0;
                    } else {
                        compatible = false;
                        break;
                    }
                }
                if (!compatible) continue;
                method.invoke(minecraft, args);
                return;
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not invoke Minecraft disconnect directly.", exception);
        }

        // Last-resort visual handoff; normal Esc/save-quit flows are still
        // intercepted by the shell controller and use Minecraft's own screen.
        minecraft.gui.setScreen(new TitleScreen());
    }
}
