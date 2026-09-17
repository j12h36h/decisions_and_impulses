package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.packs.DAI_DatapackMetadata;
import io.github.j12h36h.dai.runtime.DAI_StandaloneLaunchState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight addon selector for ordinary Minecraft worlds. The final vanilla
 * world-options screen remains available for name/seed/gamerules, but DAI's
 * pack selection is decided here with one click per addon.
 */
public final class DAI_MinecraftDaiCreateScreen extends Screen {

    private final Screen parent;
    private final int page;
    private final Set<String> selected;
    private final boolean seedDefaults;
    private List<Path> addons = List.of();

    public DAI_MinecraftDaiCreateScreen(Screen parent) {
        this(parent, 0, null);
    }

    private DAI_MinecraftDaiCreateScreen(Screen parent, int page, Set<String> selected) {
        super(Component.literal("Minecraft + DAI"));
        this.parent = parent;
        this.page = Math.max(0, page);
        this.seedDefaults = selected == null;
        this.selected = selected == null ? new LinkedHashSet<>() : new LinkedHashSet<>(selected);
    }

    @Override protected void init() {
        super.init();
        // If the vanilla Create World screen returned here through Cancel,
        // discard the one-shot selection so another unrelated world cannot
        // accidentally inherit it.
        DAI_StandaloneLaunchState.clear();
        addons = DAI_DatapackMetadata.globalAddons();
        if (seedDefaults && DAI_Config.autoEnableAddons()) {
            for (Path addon : addons) selected.add(key(addon));
        }

        int rows = Math.max(2, Math.min(6, (height - 138) / 28));
        int maxPage = Math.max(0, (addons.size() - 1) / rows);
        int current = Math.min(page, maxPage);
        int start = Math.min(current * rows, addons.size());
        int end = Math.min(start + rows, addons.size());
        int rowWidth = Math.min(480, Math.max(230, width - 36));
        int left = width / 2 - rowWidth / 2;
        int y = 58;

        for (int i = start; i < end; i++) {
            Path addon = addons.get(i);
            String key = key(addon);
            boolean enabled = selected.contains(key);
            addRenderableWidget(button(left, y, rowWidth, 22,
                    (enabled ? "✓ " : "□ ") + label(addon), b -> toggle(key, current), enabled));
            y += 28;
        }

        if (maxPage > 0) {
            int navY = height - 76;
            addRenderableWidget(button(width / 2 - 106, navY, 62, 20, "◀ PREV",
                    b -> reopen(Math.max(0, current - 1)), false));
            addRenderableWidget(button(width / 2 - 39, navY, 78, 20,
                    (current + 1) + " / " + (maxPage + 1), b -> {}, false));
            addRenderableWidget(button(width / 2 + 44, navY, 62, 20, "NEXT ▶",
                    b -> reopen(Math.min(maxPage, current + 1)), false));
        }

        addRenderableWidget(button(width / 2 - 118, height - 28, 108, 20,
                "BACK", b -> onClose(), false));
        addRenderableWidget(button(width / 2 + 10, height - 28, 108, 20,
                "WORLD OPTIONS ▶", b -> continueToWorldOptions(), true));
    }

    private void toggle(String id, int currentPage) {
        if (!selected.remove(id)) selected.add(id);
        Minecraft.getInstance().gui.setScreen(new DAI_MinecraftDaiCreateScreen(parent, currentPage, selected));
    }

    private void reopen(int nextPage) {
        Minecraft.getInstance().gui.setScreen(new DAI_MinecraftDaiCreateScreen(parent, nextPage, selected));
    }

    private void continueToWorldOptions() {
        DAI_StandaloneLaunchState.prepare(selected);
        DAI_VanillaWorldScreens.openCreate(this);
    }

    private DAI_UniverseButton button(int x, int y, int w, int h, String text, Button.OnPress press, boolean primary) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        return new DAI_UniverseButton(x, y, w, h, Component.literal(text), press,
                DAI_UniverseButton.Shape.CHIP,
                primary ? profile.primary() : profile.secondary());
    }

    private static String key(Path path) {
        return path == null || path.getFileName() == null
                ? ""
                : path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
    }

    private static String label(Path path) {
        if (path == null || path.getFileName() == null) return "DAI Addon";
        String value = path.getFileName().toString();
        int dot = value.lastIndexOf('.');
        if (dot > 0) value = value.substring(0, dot);
        value = value.replace('_', ' ').replace('-', ' ').trim();
        return value.isBlank() ? "DAI Addon" : value;
    }

    @Override public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        graphics.centeredText(font, Component.literal("MINECRAFT + DAI"), width / 2, 14, profile.text());
        graphics.centeredText(font, Component.literal("Choose the DAI addons for this new world"), width / 2, 28, 0xFF9FB2BE);
        graphics.centeredText(font, Component.literal(selected.size() + " selected · " + addons.size() + " available"), width / 2, 42, 0xFFFFD782);
        if (addons.isEmpty()) graphics.centeredText(font, Component.literal("No global DAI ADDON datapacks are installed."), width / 2, 86, 0xFFB7C7D0);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void onClose() { DAI_StandaloneLaunchState.clear(); Minecraft.getInstance().gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }
}
