package io.github.j12h36h.dai.client.navigation;

import io.github.j12h36h.dai.client.play.DAI_ExperienceCreateScreen;
import io.github.j12h36h.dai.client.play.DAI_MinecraftDaiCreateScreen;
import io.github.j12h36h.dai.client.play.DAI_WorldLibraryScreen;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.client.title.DAI_TitleActionDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/**
 * DAI 4.3's engine-owned launcher navigation.
 *
 * Experiences never replace these screens. They are selectable world/runtime
 * templates under PLAY -> SOLO -> START -> LOADED and take ownership only
 * after Minecraft attaches the selected gameplay world.
 */
public final class DAI_ShellHubScreen extends Screen {

    public enum Hub {
        PLAY("PLAY", "Choose local or connected play"),
        SOLO("SOLO", "Create or continue a local save"),
        START("START", "Choose a world runtime"),
        CONNECT("CONNECT", "Join a public or saved server"),
        CREATE("CREATE", "Create DAI content and assets"),
        CONTENT("CONTENT", "Author gameplay objects and logic"),
        ASSET("ASSET", "Author visual and core assets"),
        SETTINGS("SETTINGS", "Controls and Minecraft options"),
        OPTIONS("OPTIONS", "Audio and display settings");

        private final String title;
        private final String subtitle;

        Hub(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private final Screen parent;
    private final Hub hub;

    public DAI_ShellHubScreen(Screen parent, Hub hub) {
        super(Component.literal(hub == null ? "DAI" : hub.title));
        this.parent = parent;
        this.hub = hub == null ? Hub.PLAY : hub;
    }

    @Override
    protected void init() {
        super.init();
        int nodeW = Math.max(112, Math.min(158, width / 4));
        int nodeH = Math.max(44, Math.min(66, height / 5));
        int gap = Math.max(18, Math.min(46, width / 18));
        int cx = width / 2;
        int cy = Math.max(94, height / 2 - 6);

        Entry left;
        Entry right;
        switch (hub) {
            case PLAY -> {
                left = new Entry("SOLO", () -> openHub(Hub.SOLO));
                right = new Entry("CONNECT", () -> openHub(Hub.CONNECT));
            }
            case SOLO -> {
                left = new Entry("START", () -> openHub(Hub.START));
                right = new Entry("CONTINUE", () -> Minecraft.getInstance().gui.setScreen(new DAI_WorldLibraryScreen(this)));
            }
            case START -> {
                left = new Entry("VANILLA", () -> Minecraft.getInstance().gui.setScreen(new DAI_MinecraftDaiCreateScreen(this)));
                right = new Entry("LOADED", () -> Minecraft.getInstance().gui.setScreen(new DAI_ExperienceCreateScreen(this)));
            }
            case CONNECT -> {
                left = new Entry("PUBLIC", () -> Minecraft.getInstance().gui.setScreen(new DAI_ServerPortalScreen(this, DAI_ServerPortalScreen.Mode.PUBLIC)));
                right = new Entry("SAVED", () -> Minecraft.getInstance().gui.setScreen(new DAI_ServerPortalScreen(this, DAI_ServerPortalScreen.Mode.SAVED)));
            }
            case CREATE -> {
                left = new Entry("CONTENT", () -> openHub(Hub.CONTENT));
                right = new Entry("ASSET", () -> openHub(Hub.ASSET));
            }
            case CONTENT -> {
                left = new Entry("OBJECT", () -> Minecraft.getInstance().gui.setScreen(new DAI_CreatorCategoryScreen(this, DAI_CreatorCategoryScreen.Category.OBJECT)));
                right = new Entry("LOGIC", () -> Minecraft.getInstance().gui.setScreen(new DAI_CreatorCategoryScreen(this, DAI_CreatorCategoryScreen.Category.LOGIC)));
            }
            case ASSET -> {
                left = new Entry("VISUAL", () -> Minecraft.getInstance().gui.setScreen(new DAI_CreatorCategoryScreen(this, DAI_CreatorCategoryScreen.Category.VISUAL)));
                right = new Entry("CORE", () -> Minecraft.getInstance().gui.setScreen(new DAI_CreatorCategoryScreen(this, DAI_CreatorCategoryScreen.Category.CORE)));
            }
            case SETTINGS -> {
                left = new Entry("CONTROLS", () -> Minecraft.getInstance().gui.setScreen(new DAI_ControlsMapScreen(this)));
                right = new Entry("OPTIONS", () -> openHub(Hub.OPTIONS));
            }
            case OPTIONS -> {
                left = new Entry("AUDIO", () -> DAI_TitleActionDispatcher.openReflective(this, "net.minecraft.client.gui.screens.options.SoundOptionsScreen"));
                right = new Entry("DISPLAY", () -> DAI_TitleActionDispatcher.openReflective(this, "net.minecraft.client.gui.screens.options.VideoSettingsScreen"));
            }
            default -> throw new IllegalStateException("Unexpected hub " + hub);
        }

        addRenderableWidget(node(cx - gap / 2 - nodeW, cy - nodeH / 2, nodeW, nodeH, left.label(), b -> left.action().run(), true));
        addRenderableWidget(node(cx + gap / 2, cy - nodeH / 2, nodeW, nodeH, right.label(), b -> right.action().run(), false));
        addRenderableWidget(chip(cx - 48, height - 30, 96, 20, "BACK", b -> onClose()));
    }

    private DAI_UniverseButton node(int x, int y, int w, int h, String label, Button.OnPress press, boolean primary) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        return new DAI_UniverseButton(
                x, y, w, h, Component.literal(label), press,
                DAI_UniverseButton.Shape.NODE,
                primary ? profile.primary() : profile.secondary()
        );
    }

    private DAI_UniverseButton chip(int x, int y, int w, int h, String label, Button.OnPress press) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        return new DAI_UniverseButton(
                x, y, w, h, Component.literal(label), press,
                DAI_UniverseButton.Shape.CHIP, profile.secondary()
        );
    }

    private void openHub(Hub next) {
        Minecraft.getInstance().gui.setScreen(new DAI_ShellHubScreen(this, next));
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        int cx = width / 2;
        int cy = Math.max(94, height / 2 - 6);
        int connection = (profile.secondary() & 0x00FFFFFF) | 0x55000000;
        DAI_UniverseShellRenderer.drawConnection(graphics, cx - 18, cy, cx + 18, cy, connection);
        graphics.centeredText(font, Component.literal(hub.title), cx, 18, profile.text());
        graphics.centeredText(font, Component.literal(hub.subtitle), cx, 32, 0xFF9FB2BE);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The persistent 3-D shell world / universe renderer is the background.
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return DAI_ShellWorldRuntime.isShellActive();
    }

    private record Entry(String label, Runnable action) {}
}
