package io.github.j12h36h.dai.packs;

import io.github.j12h36h.dai.title.DAI_TitleActionDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/** Curated in-game browser for official D.A.I. datapacks/resource packs. */
public final class DAI_PackBrowserScreen extends Screen {

    private static final int PAGE_SIZE = 3;

    private final Screen parent;
    private final boolean allowRefresh;
    private boolean dirty;
    private boolean working;
    private String operationStatus = "";
    private int page;
    private int worldIndex;
    private DAI_OfficialPackCatalog catalog;
    private List<String> worlds;

    public DAI_PackBrowserScreen(Screen parent) {
        this(parent, false, true, 0, 0, "");
    }

    private DAI_PackBrowserScreen(
            Screen parent,
            boolean dirty,
            boolean allowRefresh,
            int page,
            int worldIndex,
            String operationStatus
    ) {
        super(Component.literal("D.A.I. Official Packs"));
        this.parent = parent;
        this.dirty = dirty;
        this.allowRefresh = allowRefresh;
        this.page = Math.max(0, page);
        this.worldIndex = Math.max(0, worldIndex);
        this.operationStatus = operationStatus == null ? "" : operationStatus;
        this.catalog = DAI_OfficialPackService.cachedOrFallback();
        this.worlds = DAI_PackInstallManager.worlds();
    }

    @Override
    protected void init() {
        super.init();

        worlds = DAI_PackInstallManager.worlds();
        if (!worlds.isEmpty()) {
            worldIndex = Math.min(worldIndex, worlds.size() - 1);
        } else {
            worldIndex = 0;
        }

        int center = width / 2;
        int cardWidth = Math.min(520, width - 32);
        int left = center - cardWidth / 2;

        addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> changeWorld(-1)
                )
                .bounds(left, 48, 24, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> changeWorld(1)
                )
                .bounds(left + cardWidth - 24, 48, 24, 20)
                .build());

        List<DAI_OfficialPackCatalog.PackEntry> visible = visiblePacks();
        int y = 84;
        for (DAI_OfficialPackCatalog.PackEntry pack : visible) {
            int installWidth = 94;
            int infoWidth = 70;
            int buttonY = y + 61;

            addRenderableWidget(Button.builder(
                            Component.literal("INFO"),
                            button -> DAI_TitleActionDispatcher.openExternal(pack.infoUrl())
                    )
                    .bounds(left + cardWidth - installWidth - infoWidth - 14, buttonY, infoWidth, 20)
                    .build());

            String installText = installText(pack);
            addRenderableWidget(Button.builder(
                            Component.literal(installText),
                            button -> toggleInstall(pack)
                    )
                    .bounds(left + cardWidth - installWidth - 8, buttonY, installWidth, 20)
                    .build());

            y += 96;
        }

        int maxPage = maxPage();
        addRenderableWidget(Button.builder(
                        Component.literal("PREV"),
                        button -> changePage(-1)
                )
                .bounds(center - 110, height - 54, 70, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.literal((page + 1) + " / " + (maxPage + 1)),
                        button -> { }
                )
                .bounds(center - 35, height - 54, 70, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.literal("NEXT"),
                        button -> changePage(1)
                )
                .bounds(center + 40, height - 54, 70, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.literal("BACK"),
                        button -> onClose()
                )
                .bounds(center - 50, height - 28, 100, 20)
                .build());

        if (allowRefresh) {
            DAI_OfficialPackService.refresh().thenAccept(updated -> {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.execute(() -> {
                    if (minecraft.gui.screen() == this) {
                        minecraft.gui.setScreen(new DAI_PackBrowserScreen(
                                parent,
                                dirty,
                                false,
                                page,
                                worldIndex,
                                operationStatus
                        ));
                    }
                });
            });
        }
    }

    @Override
    public void extractRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.fillGradient(0, 0, width, height, 0xFF071018, 0xFF142532);
        graphics.centeredText(font, Component.literal("OFFICIAL D.A.I. PACKS"), width / 2, 16, 0xFFFFFFFF);
        graphics.centeredText(
                font,
                Component.literal("Datapacks install to the selected world · resource packs auto-enable after restart"),
                width / 2,
                30,
                0xFFA9C0CF
        );

        int cardWidth = Math.min(520, width - 32);
        int left = width / 2 - cardWidth / 2;
        String world = selectedWorld();
        graphics.centeredText(
                font,
                Component.literal("Target world: " + (world.isBlank() ? "<no local world found>" : world)),
                width / 2,
                54,
                world.isBlank() ? 0xFFFF9F6B : 0xFFB9E6B0
        );

        List<DAI_OfficialPackCatalog.PackEntry> visible = visiblePacks();
        int y = 84;
        for (DAI_OfficialPackCatalog.PackEntry pack : visible) {
            drawCard(graphics, pack, left, y, cardWidth);
            y += 96;
        }

        String status = working
                ? operationStatus
                : (!operationStatus.isBlank() ? operationStatus : DAI_OfficialPackService.status());
        graphics.centeredText(
                font,
                Component.literal(status),
                width / 2,
                height - 72,
                working ? 0xFFFFD782 : 0xFF91A8B7
        );

        if (dirty) {
            graphics.text(
                    font,
                    Component.literal("Restart pending"),
                    8,
                    height - 18,
                    0xFFFFB66E
            );
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // Custom background above.
    }

    @Override
    public void onClose() {
        Minecraft minecraft = Minecraft.getInstance();
        if (working) return;

        if (dirty) {
            minecraft.gui.setScreen(new DAI_RestartRequiredScreen(this, parent));
        } else {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawCard(
            GuiGraphicsExtractor graphics,
            DAI_OfficialPackCatalog.PackEntry pack,
            int x,
            int y,
            int width
    ) {
        boolean installed = DAI_PackInstallManager.installed(pack.id(), selectedWorld()).isPresent();
        graphics.fill(x, y, x + width, y + 88, 0xB8162732);
        graphics.outline(x, y, width, 88, installed ? 0xFF6EA76A : 0xFF35566B);

        ItemStack icon = icon(pack.iconItem());
        if (!icon.isEmpty()) {
            graphics.fakeItem(icon, x + 10, y + 10);
        }

        graphics.text(font, Component.literal(pack.name()), x + 36, y + 10, 0xFFFFFFFF);
        graphics.text(
                font,
                Component.literal(pack.type().toUpperCase() + " · v" + pack.version()),
                x + 36,
                y + 24,
                installed ? 0xFF9ED694 : 0xFF8FAABD
        );
        graphics.textWithWordWrap(
                font,
                Component.literal(pack.summary()),
                x + 10,
                y + 39,
                Math.max(80, width - 190),
                0xFFD5E0E6
        );
    }

    private void toggleInstall(DAI_OfficialPackCatalog.PackEntry pack) {
        if (working) return;

        Optional<DAI_PackInstallManager.InstalledPack> installed =
                DAI_PackInstallManager.installed(pack.id(), selectedWorld());
        boolean currentVersion = installed.isPresent()
                && installed.get().version().equals(pack.version());

        working = true;
        operationStatus = currentVersion
                ? "Uninstalling " + pack.name() + "..."
                : (installed.isPresent()
                    ? "Updating " + pack.name() + "..."
                    : "Installing " + pack.name() + "...");

        var future = currentVersion
                ? DAI_PackInstallManager.uninstallAsync(pack, selectedWorld())
                : DAI_PackInstallManager.installAsync(pack, selectedWorld());

        future.thenAccept(result -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                boolean nextDirty = dirty || result.success();
                minecraft.gui.setScreen(new DAI_PackBrowserScreen(
                        parent,
                        nextDirty,
                        false,
                        page,
                        worldIndex,
                        result.message()
                ));
            });
        });
    }

    private String installText(DAI_OfficialPackCatalog.PackEntry pack) {
        Optional<DAI_PackInstallManager.InstalledPack> installed =
                DAI_PackInstallManager.installed(pack.id(), selectedWorld());
        if (installed.isEmpty()) return "INSTALL";
        if (!installed.get().version().equals(pack.version())) return "UPDATE";
        return "UNINSTALL";
    }

    private void changeWorld(int delta) {
        if (worlds.isEmpty() || working) return;
        int next = Math.floorMod(worldIndex + delta, worlds.size());
        Minecraft.getInstance().gui.setScreen(new DAI_PackBrowserScreen(
                parent,
                dirty,
                false,
                page,
                next,
                operationStatus
        ));
    }

    private void changePage(int delta) {
        if (working) return;
        int next = Math.max(0, Math.min(maxPage(), page + delta));
        if (next == page) return;
        Minecraft.getInstance().gui.setScreen(new DAI_PackBrowserScreen(
                parent,
                dirty,
                false,
                next,
                worldIndex,
                operationStatus
        ));
    }

    private List<DAI_OfficialPackCatalog.PackEntry> visiblePacks() {
        List<DAI_OfficialPackCatalog.PackEntry> packs = catalog.packs();
        int from = Math.min(page * PAGE_SIZE, packs.size());
        int to = Math.min(from + PAGE_SIZE, packs.size());
        return packs.subList(from, to);
    }

    private int maxPage() {
        int size = catalog.packs().size();
        if (size <= 1) return 0;
        return Math.max(0, (size - 1) / PAGE_SIZE);
    }

    private String selectedWorld() {
        if (worlds.isEmpty()) return "";
        return worlds.get(Math.min(worldIndex, worlds.size() - 1));
    }

    private static ItemStack icon(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
