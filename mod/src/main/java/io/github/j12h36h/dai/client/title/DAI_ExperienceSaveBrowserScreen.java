package io.github.j12h36h.dai.client.title;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;

/** Full-screen fallback/view-all browser for JSON title experience saves. */
public final class DAI_ExperienceSaveBrowserScreen extends Screen {

    private static final int ROW_HEIGHT = 42;
    private static final int ROW_GAP = 7;

    private final Screen parent;
    private final DAI_TitleScreenDefinition titleDefinition;
    private final DAI_TitleScreenDefinition.SaveBrowserDefinition browser;
    private final int requestedPage;
    private List<DAI_ExperienceLauncher.ExperienceSave> saves = List.of();
    private int page;
    private int pageSize;

    public DAI_ExperienceSaveBrowserScreen(
            Screen parent,
            DAI_TitleScreenDefinition titleDefinition
    ) {
        this(parent, titleDefinition, 0);
    }

    private DAI_ExperienceSaveBrowserScreen(
            Screen parent,
            DAI_TitleScreenDefinition titleDefinition,
            int page
    ) {
        super(Component.literal(
                titleDefinition == null
                        ? "Experience Saves"
                        : titleDefinition.saveBrowser().title()
        ));
        this.parent = parent;
        this.titleDefinition = titleDefinition == null
                ? DAI_TitleScreenDefinition.fallback("decisions_and_impulses:save_browser")
                : titleDefinition;
        this.browser = this.titleDefinition.saveBrowser();
        this.requestedPage = Math.max(0, page);
    }

    @Override
    protected void init() {
        if (!browser.enabled() || browser.experience().isBlank()) {
            return;
        }

        saves = DAI_ExperienceLauncher.listSaves(browser.experience());
        pageSize = Math.max(1, Math.min(8, (height - 124) / (ROW_HEIGHT + ROW_GAP)));
        int pages = Math.max(1, (saves.size() + pageSize - 1) / pageSize);
        page = Math.min(requestedPage, pages - 1);

        int panelWidth = Math.min(Math.max(320, browser.width() + 74), Math.max(320, width - 56));
        int left = width / 2 - panelWidth / 2;
        int top = 58;
        int start = page * pageSize;
        int end = Math.min(saves.size(), start + pageSize);

        for (int i = start; i < end; i++) {
            DAI_ExperienceLauncher.ExperienceSave save = saves.get(i);
            int row = i - start;
            int y = top + 34 + row * (ROW_HEIGHT + ROW_GAP);
            int deleteWidth = 30;
            int entryWidth = panelWidth - 28 - deleteWidth - 6;

            addRenderableWidget(new DAI_ExperienceSaveButton(
                    left + 12,
                    y,
                    entryWidth,
                    ROW_HEIGHT,
                    browser,
                    save,
                    button -> DAI_ExperienceLauncher.continueSave(this, browser.experience(), save.saveId())
            ));

            addRenderableWidget(new DAI_ExperienceDeleteButton(
                    left + 12 + entryWidth + 6,
                    y,
                    deleteWidth,
                    ROW_HEIGHT,
                    browser,
                    button -> confirmDelete(save)
            ));
        }

        int navY = height - 42;
        addRenderableWidget(Button.builder(
                        Component.literal("BACK"),
                        button -> Minecraft.getInstance().gui.setScreen(parent)
                )
                .bounds(width / 2 - 56, navY, 112, 24)
                .build());

        if (pages > 1) {
            addRenderableWidget(Button.builder(
                            Component.literal("< PREV"),
                            button -> Minecraft.getInstance().gui.setScreen(
                                    new DAI_ExperienceSaveBrowserScreen(parent, titleDefinition, Math.max(0, page - 1))
                            )
                    )
                    .bounds(width / 2 - 154, navY, 86, 24)
                    .build());
            addRenderableWidget(Button.builder(
                            Component.literal("NEXT >"),
                            button -> Minecraft.getInstance().gui.setScreen(
                                    new DAI_ExperienceSaveBrowserScreen(parent, titleDefinition, Math.min(pages - 1, page + 1))
                            )
                    )
                    .bounds(width / 2 + 68, navY, 86, 24)
                    .build());
        }
    }

    private void confirmDelete(DAI_ExperienceLauncher.ExperienceSave save) {
        String display = browser.entryPrefix() + " #" + save.sequence();
        Screen refreshed = new DAI_ExperienceSaveBrowserScreen(parent, titleDefinition, page);
        Minecraft.getInstance().gui.setScreen(new DAI_ExperienceDeleteConfirmScreen(
                this,
                refreshed,
                titleDefinition,
                browser.experience(),
                save,
                display
        ));
    }

    @Override
    public void extractRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderPackBackground(graphics);

        int panelWidth = Math.min(Math.max(320, browser.width() + 74), Math.max(320, width - 56));
        int left = width / 2 - panelWidth / 2;
        int top = 50;
        int bottom = height - 54;
        graphics.fill(left, top, left + panelWidth, bottom, browser.background());
        graphics.outline(left, top, panelWidth, bottom - top, browser.border());
        graphics.centeredText(font, Component.literal(browser.title()), width / 2, top + 12, browser.titleColor());

        if (saves.isEmpty()) {
            graphics.centeredText(
                    font,
                    Component.literal(browser.emptyTitle()),
                    width / 2,
                    top + 72,
                    browser.mutedColor()
            );
            graphics.centeredText(
                    font,
                    Component.literal(browser.emptySubtitle()),
                    width / 2,
                    top + 90,
                    browser.mutedColor()
            );
        } else {
            int pages = Math.max(1, (saves.size() + Math.max(1, pageSize) - 1) / Math.max(1, pageSize));
            graphics.centeredText(
                    font,
                    Component.literal("Newest first · Page " + (page + 1) + "/" + pages),
                    width / 2,
                    bottom - 17,
                    browser.mutedColor()
            );
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPackBackground(GuiGraphicsExtractor graphics) {
        String theme = titleDefinition.theme();
        if ("mineshaft".equals(theme) || "mine".equals(theme)) {
            DAI_TitleMineShaftRenderer.render(
                    graphics,
                    width,
                    height,
                    titleDefinition.backgroundTop(),
                    titleDefinition.backgroundBottom()
            );
            return;
        }

        graphics.fillGradient(
                0,
                0,
                width,
                height,
                titleDefinition.backgroundTop(),
                titleDefinition.backgroundBottom()
        );
    }

    @Override
    public void extractBackground(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // This screen draws the owning pack's JSON-configured background.
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
