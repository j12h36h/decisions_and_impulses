package io.github.j12h36h.dai.client.packs;

import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.settings.DAI_SettingsScreen;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellScreenRouter;
import io.github.j12h36h.dai.client.title.DAI_TitleActionDispatcher;
import io.github.j12h36h.dai.client.title.DAI_TitleIconTextures;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** DAI Engine 4.3 Worlds hub: Discover, Library, install/update, offline cache and launch. */
public final class DAI_PackBrowserScreen extends Screen {

    private enum View { EXPERIENCE_PACKS, ADDONS, LIBRARY }

    private static final int MAX_PAGE_SIZE = 3;
    private static final int CARD_TOP = 78;
    private static final int CARD_HEIGHT = 92;
    private static final int CARD_STRIDE = 100;
    private static final int FOOTER_RESERVED = 78;

    private final Screen parent;
    private final boolean allowRefresh;
    private final View view;
    private boolean dirty;
    private boolean working;
    private String operationStatus;
    private int page;
    private DAI_OfficialPackCatalog catalog;

    public DAI_PackBrowserScreen(Screen parent) {
        this(parent, false, true, View.EXPERIENCE_PACKS, 0, "");
    }

    public static DAI_PackBrowserScreen library(Screen parent) {
        return new DAI_PackBrowserScreen(parent, false, true, View.LIBRARY, 0, "");
    }

    private DAI_PackBrowserScreen(
            Screen parent,
            boolean dirty,
            boolean allowRefresh,
            View view,
            int page,
            String operationStatus
    ) {
        super(Component.literal("DAI Worlds"));
        this.parent = parent;
        this.dirty = dirty;
        this.allowRefresh = allowRefresh;
        this.view = view == null ? View.EXPERIENCE_PACKS : view;
        this.page = Math.max(0, page);
        this.operationStatus = operationStatus == null ? "" : operationStatus;
        this.catalog = DAI_OfficialPackService.cachedOrFallback();
    }

    @Override
    protected void init() {
        super.init();
        int center = width / 2;
        int cardWidth = Math.min(580, width - 24);
        int left = center - cardWidth / 2;

        int gap = 5;
        int tabWidth = Math.max(78, Math.min(120, (width - 30 - gap * 3) / 4));
        int tabsWidth = tabWidth * 4 + gap * 3;
        int tabsLeft = center - tabsWidth / 2;

        addRenderableWidget(shellButton(tabsLeft, 42, tabWidth, 20,
                view == View.EXPERIENCE_PACKS ? "◆ EXPERIENCES" : "EXPERIENCES",
                button -> switchView(View.EXPERIENCE_PACKS), view == View.EXPERIENCE_PACKS));
        addRenderableWidget(shellButton(tabsLeft + tabWidth + gap, 42, tabWidth, 20,
                view == View.ADDONS ? "◆ ADDONS" : "ADDONS",
                button -> switchView(View.ADDONS), view == View.ADDONS));
        addRenderableWidget(shellButton(tabsLeft + (tabWidth + gap) * 2, 42, tabWidth, 20,
                view == View.LIBRARY ? "◆ LIBRARY" : "LIBRARY",
                button -> switchView(View.LIBRARY), view == View.LIBRARY));
        addRenderableWidget(shellButton(tabsLeft + (tabWidth + gap) * 3, 42, tabWidth, 20, "SETTINGS", button -> {
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
        }, false));

        page = Math.min(page, maxPage());
        int y = CARD_TOP;
        for (DAI_OfficialPackCatalog.PackEntry pack : visiblePacks()) {
            addCardButtons(pack, left, y, cardWidth);
            y += CARD_STRIDE;
        }

        int maxPage = maxPage();
        addRenderableWidget(shellButton(center - 110, height - 54, 70, 20, "◀ PREV",
                button -> changePage(-1), false));
        addRenderableWidget(shellButton(center - 35, height - 54, 70, 20,
                (page + 1) + " / " + (maxPage + 1), button -> {}, false));
        addRenderableWidget(shellButton(center + 40, height - 54, 70, 20, "NEXT ▶",
                button -> changePage(1), false));
        addRenderableWidget(shellButton(center - 50, height - 28, 100, 20, "BACK",
                button -> onClose(), false));

        if (allowRefresh) {
            DAI_OfficialPackService.refresh().thenAccept(updated -> {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.execute(() -> {
                    if (minecraft.gui.screen() == this) {
                        minecraft.gui.setScreen(new DAI_PackBrowserScreen(
                                parent, dirty, false, view, page, operationStatus
                        ));
                    }
                });
            });
        }
    }

    private void addCardButtons(DAI_OfficialPackCatalog.PackEntry pack, int left, int y, int cardWidth) {
        boolean installed = DAI_PackInstallManager.installed(pack.id(), "").isPresent();
        int buttonY = y + 65;
        int right = left + cardWidth - 8;

        addRenderableWidget(shellButton(right - 224, buttonY, 62, 20, "INFO",
                button -> DAI_TitleActionDispatcher.openExternal(pack.infoUrl()), false));

        if (installed && pack.playable()) {
            addRenderableWidget(shellButton(right - 157, buttonY, 62, 20, "PLAY", button -> {
                        if (!working) DAI_ExperienceLauncher.launch(this, pack.experienceId());
                    }, true));
        }

        String installText = installText(pack);
        addRenderableWidget(shellButton(right - 90, buttonY, 90, 20, installText,
                button -> toggleInstall(pack), installText.equals("UPDATE") || installText.equals("INSTALL")));
    }

    private DAI_UniverseButton shellButton(
            int x, int y, int width, int height, String label, Button.OnPress press, boolean primary
    ) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        return new DAI_UniverseButton(
                x, y, width, height, Component.literal(label), press, DAI_UniverseButton.Shape.CHIP,
                primary ? profile.primary() : profile.secondary()
        );
    }

    @Override
    public void extractRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        graphics.centeredText(font, Component.literal("DAI ENGINE " + DAI_Core.FEATURE_LEVEL + " · WORLDS"), width / 2, 14, profile.text());
        graphics.centeredText(
                font,
                Component.literal(switch (view) {
                    case EXPERIENCE_PACKS -> "Complete DAI experiences delivered through ERAS";
                    case ADDONS -> "Optional DAI addons that can layer onto compatible experiences";
                    case LIBRARY -> "Installed Experience Packs and Addons remain available offline";
                }),
                width / 2,
                27,
                0xFFA9C0CF
        );

        int cardWidth = Math.min(580, width - 24);
        int left = width / 2 - cardWidth / 2;
        int y = CARD_TOP;
        for (DAI_OfficialPackCatalog.PackEntry pack : visiblePacks()) {
            drawCard(graphics, pack, left, y, cardWidth, profile);
            y += CARD_STRIDE;
        }

        if (filteredPacks().isEmpty()) {
            String empty = switch (view) {
                case EXPERIENCE_PACKS -> "No public DAI Experience Packs are available.";
                case ADDONS -> "No public DAI Addons are available.";
                case LIBRARY -> "No catalog-backed DAI packs are installed yet.";
            };
            graphics.centeredText(font, Component.literal(empty),
                    width / 2, CARD_TOP + 30, 0xFFB7C7D0);
        }

        String status = working
                ? operationStatus
                : (!operationStatus.isBlank() ? operationStatus : DAI_OfficialPackService.status());
        graphics.centeredText(font, Component.literal(status), width / 2, height - 68,
                working ? 0xFFFFD782 : 0xFF91A8B7);

        graphics.text(font,
                Component.literal((DAI_OfficialPackService.online() ? "ONLINE" : "OFFLINE")
                        + " · source: " + DAI_OfficialPackService.source()
                        + " · installed: " + DAI_PackInstallManager.installedPacks().size()),
                8, height - 18, DAI_OfficialPackService.online() ? 0xFF9ED694 : 0xFFFFB66E);

        if (dirty) {
            graphics.text(font, Component.literal("Restart pending"), width - 92, height - 18, 0xFFFFB66E);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Custom background above.
    }

    @Override
    public void onClose() {
        Minecraft minecraft = Minecraft.getInstance();
        if (working) return;
        if (dirty) minecraft.gui.setScreen(new DAI_RestartRequiredScreen(this, parent));
        else minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }

    private void drawCard(
            GuiGraphicsExtractor graphics,
            DAI_OfficialPackCatalog.PackEntry pack,
            int x,
            int y,
            int width,
            DAI_PresentationProfileService.Profile profile
    ) {
        Optional<DAI_PackInstallManager.InstalledPack> installed = DAI_PackInstallManager.installed(pack.id(), "");
        boolean update = installed.isPresent() && DAI_Versioning.compare(installed.get().version(), pack.version()) < 0;
        int border = !pack.compatible() ? 0xFF9B4B4B
                : (update ? 0xFFE1A957 : (installed.isPresent() ? 0xFF6EA76A : profile.secondary()));
        graphics.fill(x, y, x + width, y + CARD_HEIGHT, 0xB20A0D15);
        graphics.outline(x, y, width, CARD_HEIGHT, border);

        Identifier icon = DAI_TitleIconTextures.item(pack.iconItem());
        if (icon != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x + 10, y + 10,
                    0.0F, 0.0F, 16, 16, 16, 16, 0xFFFFFFFF);
        }

        graphics.text(font, Component.literal(pack.name()), x + 36, y + 8, profile.text());
        String meta = pack.publicKind().label().toUpperCase() + " · v" + pack.version();
        if (!pack.category().isBlank()
                && !pack.category().equalsIgnoreCase(pack.publicKind().label())
                && !pack.category().equalsIgnoreCase("World")) {
            meta += " · " + pack.category();
        }
        if (!pack.creator().isBlank()) meta += " · " + pack.creator();
        graphics.text(font, Component.literal(meta), x + 36, y + 21,
                installed.isPresent() ? 0xFF9ED694 : 0xFF8FAABD);
        graphics.textWithWordWrap(font, Component.literal(pack.summary()), x + 10, y + 36,
                Math.max(100, width - 250), 0xFFD5E0E6);

        String state;
        int stateColor;
        if (!pack.compatible()) {
            state = pack.compatibilityLabel();
            stateColor = 0xFFFF8A8A;
        } else if (installed.isEmpty()) {
            state = "Not installed";
            stateColor = 0xFF91A8B7;
        } else if (DAI_Versioning.compare(installed.get().version(), pack.version()) < 0) {
            state = "Update: " + installed.get().version() + " → " + pack.version();
            stateColor = 0xFFFFD782;
        } else if (DAI_Versioning.compare(installed.get().version(), pack.version()) > 0) {
            state = "Installed " + installed.get().version() + " (newer than catalog)";
            stateColor = 0xFFB7A6E8;
        } else {
            state = "Installed · current";
            stateColor = 0xFF9ED694;
        }
        graphics.text(font, Component.literal(state), x + width - 235, y + 45, stateColor);
    }

    private void toggleInstall(DAI_OfficialPackCatalog.PackEntry pack) {
        if (working) return;
        if (!pack.compatible()) {
            operationStatus = pack.compatibilityLabel();
            return;
        }

        Optional<DAI_PackInstallManager.InstalledPack> installed = DAI_PackInstallManager.installed(pack.id(), "");
        boolean currentVersion = installed.isPresent()
                && DAI_Versioning.compare(installed.get().version(), pack.version()) == 0;
        boolean newerLocal = installed.isPresent()
                && DAI_Versioning.compare(installed.get().version(), pack.version()) > 0;

        if (newerLocal) {
            operationStatus = "Downgrade blocked; installed copy is newer than ERAS catalog.";
            return;
        }

        working = true;
        operationStatus = currentVersion
                ? "Uninstalling " + pack.name() + "..."
                : (installed.isPresent() ? "Updating " + pack.name() + "..." : "Installing " + pack.name() + "...");

        var future = currentVersion
                ? DAI_PackInstallManager.uninstallAsync(pack, "")
                : DAI_PackInstallManager.installAsync(pack, "");

        future.thenAccept(result -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                boolean nextDirty = dirty || result.success();
                DAI_PresentationProfileService.reload();
                minecraft.gui.setScreen(new DAI_PackBrowserScreen(
                        parent, nextDirty, false, view, page, result.message()
                ));
            });
        });
    }

    private String installText(DAI_OfficialPackCatalog.PackEntry pack) {
        if (!pack.compatible()) return "INCOMPATIBLE";
        Optional<DAI_PackInstallManager.InstalledPack> installed = DAI_PackInstallManager.installed(pack.id(), "");
        if (installed.isEmpty()) return "INSTALL";
        int compare = DAI_Versioning.compare(installed.get().version(), pack.version());
        if (compare < 0) return "UPDATE";
        if (compare > 0) return "LOCAL NEWER";
        return "UNINSTALL";
    }

    private void switchView(View next) {
        if (working || next == view) return;
        Minecraft.getInstance().gui.setScreen(new DAI_PackBrowserScreen(parent, dirty, false, next, 0, operationStatus));
    }

    private void changePage(int delta) {
        if (working) return;
        int next = Math.max(0, Math.min(maxPage(), page + delta));
        if (next == page) return;
        Minecraft.getInstance().gui.setScreen(new DAI_PackBrowserScreen(parent, dirty, false, view, next, operationStatus));
    }

    private List<DAI_OfficialPackCatalog.PackEntry> filteredPacks() {
        List<DAI_OfficialPackCatalog.PackEntry> result = new ArrayList<>(catalog.packs());
        switch (view) {
            case EXPERIENCE_PACKS -> result.removeIf(pack -> !pack.isExperiencePack());
            case ADDONS -> result.removeIf(pack -> !pack.isAddon());
            case LIBRARY -> result.removeIf(pack -> DAI_PackInstallManager.installed(pack.id(), "").isEmpty());
        }

        result.sort(Comparator
                .comparing((DAI_OfficialPackCatalog.PackEntry pack) -> !pack.featured())
                .thenComparing(pack -> pack.publicKind().ordinal())
                .thenComparing(DAI_OfficialPackCatalog.PackEntry::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private List<DAI_OfficialPackCatalog.PackEntry> visiblePacks() {
        List<DAI_OfficialPackCatalog.PackEntry> packs = filteredPacks();
        int pageSize = pageSize();
        int from = Math.min(page * pageSize, packs.size());
        int to = Math.min(from + pageSize, packs.size());
        return packs.subList(from, to);
    }

    private int maxPage() {
        int size = filteredPacks().size();
        return size <= 1 ? 0 : Math.max(0, (size - 1) / pageSize());
    }

    private int pageSize() {
        int usableBottom = Math.max(CARD_TOP + CARD_HEIGHT, height - FOOTER_RESERVED);
        int usableHeight = Math.max(CARD_HEIGHT, usableBottom - CARD_TOP);
        int count = Math.max(1, (usableHeight + (CARD_STRIDE - CARD_HEIGHT)) / CARD_STRIDE);
        return Math.min(MAX_PAGE_SIZE, count);
    }
}
