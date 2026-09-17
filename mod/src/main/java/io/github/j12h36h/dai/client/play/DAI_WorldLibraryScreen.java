package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;

/** Responsive local-save browser with row-level Play / Modify / Delete controls. */
public final class DAI_WorldLibraryScreen extends Screen {

    private final Screen parent;
    private final int page;
    private List<DAI_PlayWorldAccess.WorldEntry> worlds = List.of();

    public DAI_WorldLibraryScreen(Screen parent) { this(parent, 0); }
    private DAI_WorldLibraryScreen(Screen parent, int page) {
        super(Component.literal("My Worlds"));
        this.parent = parent;
        this.page = Math.max(0, page);
    }

    @Override protected void init() {
        super.init();
        worlds = DAI_PlayWorldAccess.worlds();
        int rows = Math.max(2, Math.min(6, (height - 118) / 30));
        int maxPage = Math.max(0, (worlds.size() - 1) / rows);
        int current = Math.min(page, maxPage);
        int start = Math.min(current * rows, worlds.size());
        int end = Math.min(start + rows, worlds.size());
        int rowWidth = Math.max(220, Math.min(520, width - 20));
        int left = width / 2 - rowWidth / 2;
        int y = 58;

        int gap = 4;
        int deleteWidth = Math.min(72, Math.max(54, rowWidth / 7));
        int modifyWidth = Math.min(82, Math.max(62, rowWidth / 6));
        int playWidth = Math.max(120, rowWidth - deleteWidth - modifyWidth - gap * 2);

        for (int i = start; i < end; i++) {
            DAI_PlayWorldAccess.WorldEntry entry = worlds.get(i);
            int rowY = y;
            addRenderableWidget(button(left, rowY, playWidth, 24,
                    DAI_PlayWorldAccess.displayName(entry),
                    b -> DAI_PlayWorldAccess.open(this, entry), i == start));
            addRenderableWidget(button(left + playWidth + gap, rowY, modifyWidth, 24,
                    "MODIFY", b -> Minecraft.getInstance().gui.setScreen(new DAI_WorldModifyScreen(this, entry)), false));
            addRenderableWidget(button(left + playWidth + gap + modifyWidth + gap, rowY, deleteWidth, 24,
                    "DELETE", b -> confirmDelete(entry, current), false));
            y += 30;
        }

        if (maxPage > 0) {
            int navY = height - 54;
            addRenderableWidget(button(width / 2 - 106, navY, 62, 20, "◀ PREV",
                    b -> reopen(Math.max(0, current - 1)), false));
            addRenderableWidget(button(width / 2 - 39, navY, 78, 20,
                    (current + 1) + " / " + (maxPage + 1), b -> {}, false));
            addRenderableWidget(button(width / 2 + 44, navY, 62, 20, "NEXT ▶",
                    b -> reopen(Math.min(maxPage, current + 1)), false));
        }
        addRenderableWidget(button(width / 2 - 48, height - 28, 96, 20, "BACK", b -> onClose(), false));
    }

    private void confirmDelete(DAI_PlayWorldAccess.WorldEntry entry, int currentPage) {
        Minecraft.getInstance().gui.setScreen(new DAI_WorldDeleteScreen(
                this,
                DAI_PlayWorldAccess.displayName(entry),
                () -> {
                    DAI_PlayWorldAccess.delete(entry);
                    Minecraft.getInstance().gui.setScreen(new DAI_WorldLibraryScreen(parent, currentPage));
                }
        ));
    }

    private void reopen(int next) { Minecraft.getInstance().gui.setScreen(new DAI_WorldLibraryScreen(parent, next)); }

    private DAI_UniverseButton button(int x, int y, int w, int h, String text, Button.OnPress press, boolean primary) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        return new DAI_UniverseButton(x, y, w, h, Component.literal(text), press,
                DAI_UniverseButton.Shape.CHIP,
                primary ? profile.primary() : profile.secondary());
    }

    @Override public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        graphics.centeredText(font, Component.literal("MY WORLDS"), width / 2, 14, profile.text());
        graphics.centeredText(font, Component.literal(worlds.size() + " local save" + (worlds.size() == 1 ? "" : "s") + " · PLAY / MODIFY / DELETE"), width / 2, 28, 0xFF9FB2BE);
        if (worlds.isEmpty()) graphics.centeredText(font, Component.literal("No playable local saves yet."), width / 2, 88, 0xFFB7C7D0);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }
}
