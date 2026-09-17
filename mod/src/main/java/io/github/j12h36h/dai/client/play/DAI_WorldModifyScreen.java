package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;

/** Per-world editor. Experience identity is immutable; metadata, rules and allowed ADDON selection remain editable. */
public final class DAI_WorldModifyScreen extends Screen {

    private final Screen parent;
    private final DAI_PlayWorldAccess.WorldEntry world;
    private final int page;
    private List<DAI_PlayWorldAccess.AddonEntry> addons = List.of();
    private DAI_PlayWorldAccess.WorldSettings settings;
    private EditBox nameBox;
    private boolean nameSaved;

    public DAI_WorldModifyScreen(Screen parent, DAI_PlayWorldAccess.WorldEntry world) {
        this(parent, world, 0);
    }

    private DAI_WorldModifyScreen(Screen parent, DAI_PlayWorldAccess.WorldEntry world, int page) {
        super(Component.literal("Modify World"));
        this.parent = parent;
        this.world = world;
        this.page = Math.max(0, page);
    }

    @Override protected void init() {
        super.init();
        settings = DAI_PlayWorldAccess.settings(world);
        addons = DAI_PlayWorldAccess.addons(world);

        int rowWidth = Math.max(220, Math.min(500, width - 20));
        int left = width / 2 - rowWidth / 2;

        int nameY = 54;
        int saveWidth = Math.min(92, Math.max(72, rowWidth / 5));
        nameBox = new EditBox(font, left, nameY, rowWidth - saveWidth - 6, 22, Component.literal("World name"));
        nameBox.setMaxLength(96);
        nameBox.setValue(DAI_PlayWorldAccess.displayName(world));
        nameBox.setBordered(false);
        nameBox.setTextShadow(true);
        addRenderableWidget(nameBox);
        addRenderableWidget(button(left + rowWidth - saveWidth, nameY, saveWidth, 22,
                "SAVE NAME", b -> saveName(), true, true));

        int addonTop = 106;
        int rows = Math.max(1, Math.min(5, (height - addonTop - 86) / 28));
        int maxPage = Math.max(0, (addons.size() - 1) / rows);
        int current = Math.min(page, maxPage);
        int start = Math.min(current * rows, addons.size());
        int end = Math.min(start + rows, addons.size());
        int y = addonTop;

        for (int i = start; i < end; i++) {
            DAI_PlayWorldAccess.AddonEntry addon = addons.get(i);
            String prefix = !addon.allowed() ? "× " : (addon.selected() ? "✓ " : "□ ");
            addRenderableWidget(button(left, y, rowWidth, 22,
                    prefix + addon.label(),
                    b -> toggle(addon, current),
                    addon.selected() && addon.allowed(),
                    addon.allowed()));
            y += 28;
        }

        if (maxPage > 0) {
            int navY = height - 54;
            addRenderableWidget(button(width / 2 - 106, navY, 62, 20, "◀ PREV",
                    b -> reopen(Math.max(0, current - 1)), false, true));
            addRenderableWidget(button(width / 2 - 39, navY, 78, 20,
                    (current + 1) + " / " + (maxPage + 1), b -> {}, false, true));
            addRenderableWidget(button(width / 2 + 44, navY, 62, 20, "NEXT ▶",
                    b -> reopen(Math.min(maxPage, current + 1)), false, true));
        }

        int footerW = Math.max(86, Math.min(122, (rowWidth - 12) / 3));
        int footerGap = 6;
        int footerTotal = footerW * 3 + footerGap * 2;
        int footerX = width / 2 - footerTotal / 2;
        addRenderableWidget(button(footerX, height - 28, footerW, 20,
                "GAME RULES", b -> Minecraft.getInstance().gui.setScreen(new DAI_WorldGameRulesScreen(this, world)), false, true));
        addRenderableWidget(button(footerX + footerW + footerGap, height - 28, footerW, 20,
                "RESOURCE PACKS", b -> Minecraft.getInstance().gui.setScreen(new DAI_WorldResourcePackScreen(this, world)), false, true));
        addRenderableWidget(button(footerX + (footerW + footerGap) * 2, height - 28, footerW, 20,
                "DONE", b -> onClose(), true, true));
    }

    private void saveName() {
        if (nameBox == null) return;
        String value = nameBox.getValue() == null ? "" : nameBox.getValue().trim();
        if (value.isBlank()) {
            nameBox.setValue(DAI_PlayWorldAccess.displayName(world));
            return;
        }
        nameSaved = DAI_PlayWorldAccess.rename(world, value);
        if (nameSaved) nameBox.setValue(DAI_PlayWorldAccess.displayName(world));
    }

    private void toggle(DAI_PlayWorldAccess.AddonEntry addon, int currentPage) {
        if (addon == null || !addon.allowed()) return;
        DAI_PlayWorldAccess.setAddonSelected(world, addon.stableId(), !addon.selected());
        Minecraft.getInstance().gui.setScreen(new DAI_WorldModifyScreen(parent, world, currentPage));
    }

    private void reopen(int next) {
        Minecraft.getInstance().gui.setScreen(new DAI_WorldModifyScreen(parent, world, next));
    }

    private DAI_UniverseButton button(
            int x, int y, int w, int h, String text, Button.OnPress press, boolean primary, boolean active
    ) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseButton button = new DAI_UniverseButton(x, y, w, h, Component.literal(text), press,
                DAI_UniverseButton.Shape.CHIP,
                primary ? profile.primary() : profile.secondary());
        button.active = active;
        return button;
    }

    @Override public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        graphics.centeredText(font, Component.literal("MODIFY · " + DAI_PlayWorldAccess.displayName(world)), width / 2, 10, profile.text());

        String experience = settings == null ? "" : settings.experienceId();
        if (!experience.isBlank()) {
            graphics.centeredText(font, Component.literal("EXPERIENCE: " + experience + "  🔒"), width / 2, 25, 0xFFFFD782);
            graphics.centeredText(font, Component.literal("Experience identity is locked; name, rules, addons and auto resource packs remain editable."), width / 2, 38, 0xFF9FB2BE);
        } else {
            graphics.centeredText(font, Component.literal("STANDARD WORLD · NAME, RULES, ADDONS & RESOURCE PACKS MAY BE CHANGED"), width / 2, 28, 0xFF9FB2BE);
        }

        if (nameBox != null) {
            int x = nameBox.getX();
            int y = nameBox.getY();
            int w = nameBox.getWidth();
            int h = nameBox.getHeight();
            graphics.fill(x, y, x + w, y + h, 0xBC080A10);
            graphics.outline(x, y, w, h, nameBox.isHoveredOrFocused() ? profile.primary() : 0x885F4A73);
            graphics.text(font, Component.literal("WORLD NAME"), x + 4, y - 10, 0xFFB7C7D0, false);
        }

        String policy = settings == null ? "" : settings.policyText();
        if (!policy.isBlank()) graphics.centeredText(font, Component.literal(policy), width / 2, 85, 0xFFB7C7D0);
        if (nameSaved) graphics.text(font, Component.literal("SAVED"), width / 2 + 118, 58, profile.primary(), false);
        if (addons.isEmpty()) graphics.centeredText(font, Component.literal("No installed DAI ADDON packs are available."), width / 2, 116, 0xFFB7C7D0);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }
}
