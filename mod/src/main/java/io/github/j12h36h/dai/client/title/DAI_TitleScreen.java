package io.github.j12h36h.dai.client.title;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/** JSON-driven replacement for Minecraft's vanilla title screen. */
public final class DAI_TitleScreen extends Screen {

    private static final int COMPACT_BOTTOM_MARGIN = 8;
    private static final int COMPACT_TEXT_GAP = 2;
    private static final int COMPACT_BUTTON_GAP = 6;
    private static final int MIN_COMPACT_BUTTON_GAP = 2;
    private static final int MIN_BUTTON_HEIGHT = 18;

    private static final int SAVE_LAYOUT_GAP = 20;
    private static final int SAVE_LAYOUT_MARGIN = 22;
    private static final int SAVE_ROW_HEIGHT = 42;
    private static final int SAVE_ROW_GAP = 6;
    private static final int SAVE_DELETE_WIDTH = 28;
    private static final int DECORATION_SCREEN_MARGIN = 8;
    private static final int DECORATION_BUTTON_GAP = 18;
    private static final float MIN_DECORATION_SCALE = 0.58F;

    /** Prevent accidental click-through while replacing vanilla's title screen. */
    private static final long TRANSITION_CLICK_GUARD_NANOS = 650_000_000L;

    private final DAI_TitleScreenDefinition definition;
    private long acceptClicksAfterNanos;
    private long animationStartNanos;
    private boolean wideSaveLayout;
    private List<DAI_ExperienceLauncher.ExperienceSave> browserSaves = List.of();
    private BrowserBounds browserBounds;
    private ContentBounds titleButtonBounds;

    public DAI_TitleScreen(DAI_TitleScreenDefinition definition) {
        super(Component.literal("Decisions & Impulses"));
        this.definition = definition == null
                ? DAI_TitleScreenDefinition.fallback("decisions_and_impulses:fallback")
                : definition;
    }

    @Override
    protected void init() {
        super.init();

        animationStartNanos = System.nanoTime();
        acceptClicksAfterNanos = animationStartNanos + TRANSITION_CLICK_GUARD_NANOS;
        titleButtonBounds = null;
        DAI_TitleScreenDefinition.SaveBrowserDefinition saveBrowser = definition.saveBrowser();
        if (saveBrowser.enabled() && !saveBrowser.experience().isBlank()) {
            browserSaves = DAI_ExperienceLauncher.listSaves(saveBrowser.experience());
        } else {
            browserSaves = List.of();
        }

        wideSaveLayout = canShowSideSaveBrowser();
        browserBounds = wideSaveLayout ? resolveBrowserBounds() : null;

        CompactLayout compact = buildCompactLayout();
        int centeredIndex = 0;

        for (DAI_TitleScreenDefinition.ButtonDefinition button : definition.buttons()) {
            if (compact != null && isCentered(button)) {
                DAI_TitleScreenDefinition.ButtonDefinition fitted = withHeight(
                        button,
                        compact.buttonHeight()
                );

                int x = resolveX(fitted);
                int y = compact.firstY()
                        + centeredIndex * (compact.buttonHeight() + compact.gap());

                centeredIndex++;
                addTitleButton(x, y, fitted);
                continue;
            }

            addTitleButton(
                    resolveX(button),
                    resolveY(button),
                    button
            );
        }

        if (wideSaveLayout) {
            addSaveBrowserWidgets();
        } else if (saveBrowser.enabled() && !saveBrowser.experience().isBlank()) {
            addCompactSaveBrowserButton();
        }

        DAI_Core.debug(
                "<DAI>: Initialized JSON title screen '{}' with {} button(s), compactLayout={}, saveBrowser={}, sideBySide={}.",
                definition.id(),
                definition.buttons().size(),
                compact != null,
                saveBrowser.enabled(),
                wideSaveLayout
        );
    }

    private void addTitleButton(
            int x,
            int y,
            DAI_TitleScreenDefinition.ButtonDefinition button
    ) {
        DAI_TitleButton widget = new DAI_TitleButton(
                x,
                y,
                button,
                pressed -> {
                    if (!acceptTitleClick(button.id())) return;
                    DAI_TitleActionDispatcher.run(this, button);
                }
        );

        addRenderableWidget(widget);
        trackTitleButtonBounds(x, y, button.width(), button.height());
    }

    private void trackTitleButtonBounds(int x, int y, int width, int height) {
        ContentBounds bounds = new ContentBounds(x, y, x + width, y + height);
        titleButtonBounds = titleButtonBounds == null ? bounds : titleButtonBounds.include(bounds);
    }

    private boolean acceptTitleClick(String id) {
        if (System.nanoTime() >= acceptClicksAfterNanos) return true;
        DAI_Core.debug(
                "<DAI>: Ignored title-screen button '{}' during transition click guard.",
                id
        );
        return false;
    }

    private void addSaveBrowserWidgets() {
        DAI_TitleScreenDefinition.SaveBrowserDefinition browser = definition.saveBrowser();
        BrowserBounds bounds = browserBounds;
        if (bounds == null) return;

        int maxRowsByHeight = Math.max(1, (bounds.height() - 58) / (SAVE_ROW_HEIGHT + SAVE_ROW_GAP));
        int rows = Math.min(browser.rows(), maxRowsByHeight);
        int shown = Math.min(rows, browserSaves.size());
        int rowX = bounds.x() + 8;
        int rowY = bounds.y() + 31;
        int entryWidth = bounds.width() - 16 - SAVE_DELETE_WIDTH - 5;

        for (int i = 0; i < shown; i++) {
            DAI_ExperienceLauncher.ExperienceSave save = browserSaves.get(i);
            int y = rowY + i * (SAVE_ROW_HEIGHT + SAVE_ROW_GAP);

            addRenderableWidget(new DAI_ExperienceSaveButton(
                    rowX,
                    y,
                    entryWidth,
                    SAVE_ROW_HEIGHT,
                    browser,
                    save,
                    button -> {
                        if (!acceptTitleClick("save:" + save.saveId())) return;
                        DAI_ExperienceLauncher.continueSave(this, browser.experience(), save.saveId());
                    }
            ));

            addRenderableWidget(new DAI_ExperienceDeleteButton(
                    rowX + entryWidth + 5,
                    y,
                    SAVE_DELETE_WIDTH,
                    SAVE_ROW_HEIGHT,
                    browser,
                    button -> {
                        if (!acceptTitleClick("delete:" + save.saveId())) return;
                        confirmDelete(save);
                    }
            ));
        }

        if (browserSaves.size() > shown) {
            int y = bounds.y() + bounds.height() - 25;
            DAI_TitleScreenDefinition.ButtonDefinition viewAll = browserButtonDefinition(
                    "view_all_saves",
                    "VIEW ALL (" + browserSaves.size() + ")",
                    bounds.width() - 16,
                    19
            );
            addRenderableWidget(new DAI_TitleButton(
                    bounds.x() + 8,
                    y,
                    viewAll,
                    button -> {
                        if (!acceptTitleClick("view_all_saves")) return;
                        openSaveBrowser();
                    }
            ));
        }
    }

    private void addCompactSaveBrowserButton() {
        DAI_TitleScreenDefinition.SaveBrowserDefinition browser = definition.saveBrowser();
        int width = 116;
        int height = 20;
        DAI_TitleScreenDefinition.ButtonDefinition compact = browserButtonDefinition(
                "compact_save_browser",
                browser.title(),
                width,
                height
        );
        addRenderableWidget(new DAI_TitleButton(
                this.width - width - 8,
                8,
                compact,
                button -> {
                    if (!acceptTitleClick("compact_save_browser")) return;
                    openSaveBrowser();
                }
        ));
    }

    private DAI_TitleScreenDefinition.ButtonDefinition browserButtonDefinition(
            String id,
            String label,
            int width,
            int height
    ) {
        DAI_TitleScreenDefinition.SaveBrowserDefinition browser = definition.saveBrowser();
        return new DAI_TitleScreenDefinition.ButtonDefinition(
                id,
                label,
                "browser_internal",
                "",
                browser.experience(),
                "center",
                0,
                0,
                width,
                height,
                DAI_TitleScreenDefinition.IconDefinition.NONE,
                new DAI_TitleScreenDefinition.StyleDefinition(
                        browser.entryBackground(),
                        browser.entryHover(),
                        browser.entryBorder(),
                        browser.textColor()
                ),
                DAI_TitleScreenDefinition.HoverAnimation.NONE
        );
    }

    private void confirmDelete(DAI_ExperienceLauncher.ExperienceSave save) {
        DAI_TitleScreenDefinition.SaveBrowserDefinition browser = definition.saveBrowser();
        String display = browser.entryPrefix() + " #" + save.sequence();
        Minecraft.getInstance().gui.setScreen(new DAI_ExperienceDeleteConfirmScreen(
                this,
                new DAI_TitleScreen(definition),
                definition,
                browser.experience(),
                save,
                display
        ));
    }

    private void openSaveBrowser() {
        Minecraft.getInstance().gui.setScreen(new DAI_ExperienceSaveBrowserScreen(
                this,
                definition
        ));
    }

    @Override
    public void extractRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if ("mineshaft".equals(definition.theme()) || "mine".equals(definition.theme())) {
            DAI_TitleMineShaftRenderer.render(
                    graphics,
                    width,
                    height,
                    definition.backgroundTop(),
                    definition.backgroundBottom()
            );
        } else {
            graphics.fillGradient(
                    0,
                    0,
                    width,
                    height,
                    definition.backgroundTop(),
                    definition.backgroundBottom()
            );
        }

        renderButtonPanel(graphics);
        renderSaveBrowserPanel(graphics);
        renderDecorations(graphics);

        graphics.centeredText(
                font,
                Component.literal(definition.title()),
                width / 2,
                titleY(),
                definition.titleColor()
        );

        graphics.centeredText(
                font,
                Component.literal(definition.subtitle()),
                width / 2,
                subtitleY(),
                definition.subtitleColor()
        );

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderButtonPanel(GuiGraphicsExtractor graphics) {
        if (!("mineshaft".equals(definition.theme()) || "mine".equals(definition.theme()))) return;
        int column = buttonColumnWidth();
        int panelX = wideSaveLayout
                ? width / 2 - (definition.saveBrowser().width() + SAVE_LAYOUT_GAP) / 2 - column / 2 - 10
                : width / 2 - column / 2 - 10;
        int panelY = Math.max(subtitleY() + font.lineHeight + 7, height / 2 - 64);
        int panelBottom = Math.min(height - 7, height / 2 + 139);
        graphics.fill(panelX, panelY, panelX + column + 20, panelBottom, 0xB20B0907);
        graphics.outline(panelX, panelY, column + 20, panelBottom - panelY, 0xFF765A34);
    }

    private void renderDecorations(GuiGraphicsExtractor graphics) {
        if (definition.decorations().isEmpty()) return;

        double seconds = Math.max(0L, System.nanoTime() - animationStartNanos) / 1_000_000_000.0D;
        long animationTick = (long) Math.floor(seconds * 20.0D);

        for (DAI_TitleScreenDefinition.DecorationDefinition decoration : definition.decorations()) {
            if (wideSaveLayout && decoration.hideWhenSaveBrowserWide()) continue;

            Identifier texture = DAI_TitleIconTextures.resolve("texture", decoration.texture());
            if (texture == null) continue;

            DecorationPlacement placement = resolveDecorationPlacement(decoration, seconds);
            if (placement == null) continue;

            int frames = Math.max(1, decoration.frames());
            int columns = Math.max(1, decoration.columns());
            int frame = frames <= 1
                    ? 0
                    : (int) ((animationTick / decoration.frameTicks()) % frames);

            if (!decoration.loop() && frames > 1) {
                frame = Math.min(frames - 1, (int) (animationTick / decoration.frameTicks()));
            }

            int column = frame % columns;
            int row = frame / columns;
            int sheetRows = Math.max(1, (frames + columns - 1) / columns);
            int sheetWidth = decoration.frameWidth() * columns;
            int sheetHeight = decoration.frameHeight() * sheetRows;

            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    placement.x(),
                    placement.y(),
                    column * (float) decoration.frameWidth(),
                    row * (float) decoration.frameHeight(),
                    placement.width(),
                    placement.height(),
                    decoration.frameWidth(),
                    decoration.frameHeight(),
                    sheetWidth,
                    sheetHeight,
                    decoration.tint()
            );
        }
    }

    private DecorationPlacement resolveDecorationPlacement(
            DAI_TitleScreenDefinition.DecorationDefinition decoration,
            double seconds
    ) {
        int renderWidth = decoration.width();
        int renderHeight = decoration.height();

        DecorationPlacement placement = rawDecorationPlacement(decoration, renderWidth, renderHeight, seconds);
        if (placement == null) return null;

        if (titleButtonBounds != null && overlapsButtons(placement)) {
            int maxWidth = maxDecorationWidthWithoutButtonOverlap(decoration);
            if (maxWidth <= 0) {
                return null;
            }

            float scale = Math.min(1.0F, maxWidth / (float) decoration.width());
            if (scale < MIN_DECORATION_SCALE) {
                return null;
            }

            renderWidth = Math.max(1, Math.round(decoration.width() * scale));
            renderHeight = Math.max(1, Math.round(decoration.height() * scale));
            placement = rawDecorationPlacement(decoration, renderWidth, renderHeight, seconds);
            if (placement == null || overlapsButtons(placement)) {
                return null;
            }
        }

        return placement;
    }

    private DecorationPlacement rawDecorationPlacement(
            DAI_TitleScreenDefinition.DecorationDefinition decoration,
            int renderWidth,
            int renderHeight,
            double seconds
    ) {
        int x = resolveDecorationX(decoration.anchor(), decoration.x(), renderWidth);
        int y = resolveDecorationY(decoration.anchor(), decoration.y(), renderHeight);

        if (decoration.bobAmount() != 0.0F && decoration.bobSpeed() > 0.0F) {
            double phase = seconds * decoration.bobSpeed() * Math.PI * 2.0D;
            y += Math.round((float) (Math.sin(phase) * decoration.bobAmount()));
        }

        x = clamp(x, DECORATION_SCREEN_MARGIN, Math.max(DECORATION_SCREEN_MARGIN, width - renderWidth - DECORATION_SCREEN_MARGIN));
        int minY = subtitleY() + font.lineHeight + 6;
        y = clamp(y, minY, Math.max(minY, height - renderHeight - DECORATION_SCREEN_MARGIN));
        return new DecorationPlacement(x, y, renderWidth, renderHeight);
    }

    private int resolveDecorationX(String anchor, int offset, int renderWidth) {
        return switch (anchor) {
            case "top_left", "left", "bottom_left" -> offset;
            case "top_right", "right", "bottom_right" -> width - renderWidth - offset;
            default -> width / 2 - renderWidth / 2 + offset;
        };
    }

    private int resolveDecorationY(String anchor, int offset, int renderHeight) {
        return switch (anchor) {
            case "top_left", "top_right", "top" -> offset;
            case "bottom_left", "bottom_right", "bottom" -> height - renderHeight - offset;
            default -> height / 2 + offset;
        };
    }

    private boolean overlapsButtons(DecorationPlacement placement) {
        if (titleButtonBounds == null) return false;
        return placement.x() < titleButtonBounds.right() + DECORATION_BUTTON_GAP
                && placement.x() + placement.width() > titleButtonBounds.left() - DECORATION_BUTTON_GAP
                && placement.y() < titleButtonBounds.bottom() + DECORATION_BUTTON_GAP
                && placement.y() + placement.height() > titleButtonBounds.top() - DECORATION_BUTTON_GAP;
    }

    private int maxDecorationWidthWithoutButtonOverlap(DAI_TitleScreenDefinition.DecorationDefinition decoration) {
        if (titleButtonBounds == null) return decoration.width();

        return switch (decoration.anchor()) {
            case "top_right", "right", "bottom_right" ->
                    width - decoration.x() - titleButtonBounds.right() - DECORATION_BUTTON_GAP;
            case "top_left", "left", "bottom_left" ->
                    titleButtonBounds.left() - decoration.x() - DECORATION_BUTTON_GAP;
            default -> 0;
        };
    }

    private void renderSaveBrowserPanel(GuiGraphicsExtractor graphics) {
        if (!wideSaveLayout || browserBounds == null) return;
        DAI_TitleScreenDefinition.SaveBrowserDefinition browser = definition.saveBrowser();
        BrowserBounds bounds = browserBounds;

        graphics.fill(
                bounds.x(),
                bounds.y(),
                bounds.x() + bounds.width(),
                bounds.y() + bounds.height(),
                browser.background()
        );
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), browser.border());
        graphics.text(
                font,
                Component.literal(browser.title()),
                bounds.x() + 9,
                bounds.y() + 10,
                browser.titleColor()
        );

        Component count = Component.literal(browserSaves.size() + " SAVE" + (browserSaves.size() == 1 ? "" : "S"));
        int countWidth = font.width(count);
        graphics.text(
                font,
                count,
                bounds.x() + bounds.width() - countWidth - 9,
                bounds.y() + 10,
                browser.mutedColor()
        );

        if (browserSaves.isEmpty()) {
            graphics.centeredText(
                    font,
                    Component.literal(browser.emptyTitle()),
                    bounds.x() + bounds.width() / 2,
                    bounds.y() + 69,
                    browser.mutedColor()
            );
            graphics.centeredText(
                    font,
                    Component.literal(browser.emptySubtitle()),
                    bounds.x() + bounds.width() / 2,
                    bounds.y() + 86,
                    browser.mutedColor()
            );
        }
    }

    @Override
    public void extractBackground(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // This screen draws its own JSON-configured background.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int titleY() {
        return Math.max(20, height / 2 - 132);
    }

    private int subtitleY() {
        return Math.max(35, height / 2 - 112);
    }

    private int resolveX(DAI_TitleScreenDefinition.ButtonDefinition button) {
        int base = switch (button.anchor()) {
            case "top_left", "left", "bottom_left" -> button.x();
            case "top_right", "right", "bottom_right" -> width - button.width() - button.x();
            default -> width / 2 - button.width() / 2 + button.x();
        };

        if (wideSaveLayout && isCentered(button)) {
            base -= (definition.saveBrowser().width() + SAVE_LAYOUT_GAP) / 2;
        }
        return base;
    }

    private int resolveY(DAI_TitleScreenDefinition.ButtonDefinition button) {
        return switch (button.anchor()) {
            case "top_left", "top_right", "top" -> button.y();
            case "bottom_left", "bottom_right", "bottom" -> height - button.height() - button.y();
            default -> height / 2 + button.y();
        };
    }

    private boolean canShowSideSaveBrowser() {
        DAI_TitleScreenDefinition.SaveBrowserDefinition browser = definition.saveBrowser();
        if (!browser.enabled() || browser.experience().isBlank()) return false;
        int required = buttonColumnWidth() + SAVE_LAYOUT_GAP + browser.width() + SAVE_LAYOUT_MARGIN * 2;
        return width >= required && height >= Math.max(250, browser.height() + 72);
    }

    private BrowserBounds resolveBrowserBounds() {
        DAI_TitleScreenDefinition.SaveBrowserDefinition browser = definition.saveBrowser();
        int column = buttonColumnWidth();
        int total = column + SAVE_LAYOUT_GAP + browser.width();
        int left = width / 2 - total / 2;
        int x = left + column + SAVE_LAYOUT_GAP;
        int preferredY = height / 2 + browser.y();
        int minY = subtitleY() + font.lineHeight + 8;
        int maxY = Math.max(minY, height - browser.height() - 8);
        int y = Math.max(minY, Math.min(maxY, preferredY));
        return new BrowserBounds(x, y, browser.width(), browser.height());
    }

    private int buttonColumnWidth() {
        int max = 210;
        for (DAI_TitleScreenDefinition.ButtonDefinition button : definition.buttons()) {
            if (isCentered(button)) max = Math.max(max, button.width());
        }
        return max;
    }

    /**
     * Builds a compact layout only when the JSON-defined centered stack would
     * run off the bottom of the current GUI-scaled screen.
     */
    private CompactLayout buildCompactLayout() {
        List<DAI_TitleScreenDefinition.ButtonDefinition> centered = new ArrayList<>();

        for (DAI_TitleScreenDefinition.ButtonDefinition button : definition.buttons()) {
            if (isCentered(button)) {
                centered.add(button);
            }
        }

        if (centered.isEmpty()) {
            return null;
        }

        DAI_TitleScreenDefinition.ButtonDefinition first = centered.get(0);
        DAI_TitleScreenDefinition.ButtonDefinition last = centered.get(centered.size() - 1);

        int rawLastBottom = height / 2 + last.y() + last.height();
        int bottomLimit = height - COMPACT_BOTTOM_MARGIN;

        if (rawLastBottom <= bottomLimit) {
            return null;
        }

        int rawFirstY = height / 2 + first.y();
        int minimumFirstY = subtitleY() + font.lineHeight + COMPACT_TEXT_GAP;
        int firstY = Math.max(rawFirstY, minimumFirstY);

        int count = centered.size();
        int available = bottomLimit - firstY;
        if (available <= 0) {
            return null;
        }

        int originalMinHeight = Integer.MAX_VALUE;
        for (DAI_TitleScreenDefinition.ButtonDefinition button : centered) {
            originalMinHeight = Math.min(originalMinHeight, button.height());
        }

        int buttonHeight = Math.min(
                originalMinHeight,
                (available - COMPACT_BUTTON_GAP * Math.max(0, count - 1)) / count
        );
        buttonHeight = Math.max(MIN_BUTTON_HEIGHT, buttonHeight);

        int gap;
        if (count <= 1) {
            gap = 0;
        } else {
            gap = Math.min(
                    COMPACT_BUTTON_GAP,
                    (available - buttonHeight * count) / (count - 1)
            );
            gap = Math.max(MIN_COMPACT_BUTTON_GAP, gap);
        }

        int totalHeight = buttonHeight * count + gap * Math.max(0, count - 1);
        if (totalHeight > available) {
            firstY = Math.max(
                    minimumFirstY,
                    bottomLimit - totalHeight
            );
        }

        return new CompactLayout(
                firstY,
                buttonHeight,
                gap
        );
    }

    private static boolean isCentered(
            DAI_TitleScreenDefinition.ButtonDefinition button
    ) {
        return switch (button.anchor()) {
            case "top_left", "top_right", "top", "left", "right",
                 "bottom_left", "bottom_right", "bottom" -> false;
            default -> true;
        };
    }

    private static DAI_TitleScreenDefinition.ButtonDefinition withHeight(
            DAI_TitleScreenDefinition.ButtonDefinition button,
            int height
    ) {
        return new DAI_TitleScreenDefinition.ButtonDefinition(
                button.id(),
                button.label(),
                button.action(),
                button.url(),
                button.experience(),
                button.anchor(),
                button.x(),
                button.y(),
                button.width(),
                height,
                button.icon(),
                button.style(),
                button.hoverAnimation()
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record CompactLayout(
            int firstY,
            int buttonHeight,
            int gap
    ) {
    }

    private record DecorationPlacement(
            int x,
            int y,
            int width,
            int height
    ) {
    }

    private record ContentBounds(
            int left,
            int top,
            int right,
            int bottom
    ) {
        private ContentBounds include(ContentBounds other) {
            return new ContentBounds(
                    Math.min(left, other.left),
                    Math.min(top, other.top),
                    Math.max(right, other.right),
                    Math.max(bottom, other.bottom)
            );
        }
    }

    private record BrowserBounds(
            int x,
            int y,
            int width,
            int height
    ) {
    }
}
