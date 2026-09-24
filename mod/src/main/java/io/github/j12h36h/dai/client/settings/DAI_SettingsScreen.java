package io.github.j12h36h.dai.client.settings;

import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.client.config.DAI_ClientConfig;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.core.DAI_Position;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Player-facing DAI Engine configuration editor introduced in 4.0; current feature level is supplied by DAI_Core. */
public final class DAI_SettingsScreen extends Screen {

    private enum Page { GENERAL, AUTOMATION, PRESENTATION, MODULES }

    private final Screen parent;
    private final Page page;
    private final int modulePage;
    private String status;

    public DAI_SettingsScreen(Screen parent) {
        this(parent, Page.GENERAL, 0, "");
    }

    private DAI_SettingsScreen(Screen parent, Page page, int modulePage, String status) {
        super(Component.literal("DAI Engine Settings"));
        this.parent = parent;
        this.page = page == null ? Page.GENERAL : page;
        this.modulePage = Math.max(0, modulePage);
        this.status = status == null ? "" : status;
    }

    @Override
    protected void init() {
        super.init();
        int center = width / 2;
        int tabWidth = Math.max(72, Math.min(105, (width - 40) / 4));
        int total = tabWidth * 4;
        int tabX = center - total / 2;
        Page[] pages = Page.values();
        for (int i = 0; i < pages.length; i++) {
            Page value = pages[i];
            addRenderableWidget(shellButton(
                    tabX + i * tabWidth, 42, tabWidth - 2, 20,
                    Component.literal((value == page ? "◆ " : "") + display(value)),
                    button -> open(value, 0, ""),
                    value == page
            ));
        }

        switch (page) {
            case GENERAL -> buildGeneral();
            case AUTOMATION -> buildAutomation();
            case PRESENTATION -> buildPresentation();
            case MODULES -> buildModules();
        }

        addRenderableWidget(shellButton(center - 50, height - 28, 100, 20,
                Component.literal("BACK"), button -> onClose(), false));
    }

    private void buildGeneral() {
        int y = contentTop();
        y = toggle(y, "Full DAI game shell", DAI_ClientConfig.fullGameShell(), () ->
                setClient(DAI_ClientConfig.FULL_GAME_SHELL, !DAI_ClientConfig.fullGameShell(), "DAI shell policy updated."));
        y = toggle(y, "Debugging", DAI_Config.isDebuggingEnabled(), () ->
                setCommon(DAI_Config.DEBUGGING, !DAI_Config.isDebuggingEnabled(), "Debugging updated."));
        y = toggle(y, "Disable vanilla keybinds", get(DAI_Config.TOGGLE_KEYBINDS, false), () ->
                setCommon(DAI_Config.TOGGLE_KEYBINDS, !get(DAI_Config.TOGGLE_KEYBINDS, false), "Keybind policy updated."));
        y = toggle(y, "Auto-enable DAI addons", DAI_Config.autoEnableAddons(), () ->
                setCommon(DAI_Config.AUTO_ENABLE_ADDONS, !DAI_Config.autoEnableAddons(), "Addon layering updated."));
        y = toggle(y, "Auto-enable managed resource packs", DAI_Config.autoEnableManagedResourcePacks(), () ->
                setCommon(DAI_Config.AUTO_ENABLE_MANAGED_RESOURCE_PACKS, !DAI_Config.autoEnableManagedResourcePacks(), "Managed pack policy updated."));
        y = cyclePosition(y, "System menu position", DAI_Config.SYSTEM_MENU_POSITION);
        y = cyclePosition(y, "Action menu position", DAI_Config.ACTION_MENU_POSITION);

        double opacity = DAI_Config.overlayOpacity();
        rowButton(y, "Overlay opacity", Math.round(opacity * 100.0D) + "%", () -> {
            double next = opacity >= 0.99D ? 0.25D : Math.min(1.0D, opacity + 0.25D);
            setCommon(DAI_Config.OVERLAY_OPACITY, next, "Overlay opacity updated.");
        });
    }

    private void buildAutomation() {
        int y = contentTop();
        y = toggle(y, "Automation enabled", DAI_Config.automationEnabled(), () ->
                setCommon(DAI_Config.AUTOMATION_ENABLED, !DAI_Config.automationEnabled(), "Automation updated."));
        y = toggle(y, "Automation movement", DAI_Config.automationMovement(), () ->
                setCommon(DAI_Config.AUTOMATION_MOVEMENT, !DAI_Config.automationMovement(), "Movement permission updated."));
        y = toggle(y, "Automation combat", DAI_Config.automationCombat(), () ->
                setCommon(DAI_Config.AUTOMATION_COMBAT, !DAI_Config.automationCombat(), "Combat permission updated."));
        y = toggle(y, "Automation world editing", DAI_Config.automationWorldEditing(), () ->
                setCommon(DAI_Config.AUTOMATION_WORLD_EDITING, !DAI_Config.automationWorldEditing(), "World-edit permission updated."));

        int actions = DAI_Config.maxActionsPerSecond();
        y = numberRow(y, "Max new actions / second", actions, 1, 20, 1, DAI_Config.MAX_ACTIONS_PER_SECOND);
        int queue = DAI_Config.maxActionQueueSize();
        numberRow(y, "Max action queue", queue, 16, 2048, 16, DAI_Config.MAX_ACTION_QUEUE_SIZE);
    }

    private void buildPresentation() {
        int y = contentTop();
        y = rowButton(y, "DAI bootstrap", "2-D SPLASH → 3-D SHELL", () -> {});
        y = toggle(y, "Refresh ERAS Worlds catalog", DAI_ClientConfig.worldCatalogRefresh(), () ->
                setClient(DAI_ClientConfig.WORLD_CATALOG_REFRESH, !DAI_ClientConfig.worldCatalogRefresh(), "Catalog refresh policy updated."));
        y = toggle(y, "DAI Creator", DAI_ClientConfig.creatorEnabled(), () ->
                setClient(DAI_ClientConfig.CREATOR_ENABLED, !DAI_ClientConfig.creatorEnabled(), "Creator preference updated."));
        y = toggle(y, "Automation Creator", DAI_ClientConfig.automationCreatorEnabled(), () ->
                setClient(DAI_ClientConfig.AUTOMATION_CREATOR_ENABLED, !DAI_ClientConfig.automationCreatorEnabled(), "Automation Creator preference updated."));

        DAI_PresentationProfileService.Profile selected = DAI_PresentationProfileService.selected();
        y = rowButton(y, "Presentation pack", selected.name(), this::cyclePresentation);
        rowButton(y, "Presentation discovery", DAI_PresentationProfileService.available().size() + " profile(s)", () -> {
            DAI_PresentationProfileService.reload();
            open(Page.PRESENTATION, 0, "Rescanned resource packs for DAI presentation definitions.");
        });
    }

    private void buildModules() {
        List<Map.Entry<String, Boolean>> modules = new ArrayList<>(DAI_Config.featureModuleSnapshot().entrySet());
        int rows = Math.max(4, Math.min(8, (height - contentTop() - 58) / rowStride()));
        int maxPage = Math.max(0, (modules.size() - 1) / rows);
        int current = Math.min(modulePage, maxPage);
        int from = Math.min(current * rows, modules.size());
        int to = Math.min(from + rows, modules.size());
        int y = contentTop();
        for (int i = from; i < to; i++) {
            Map.Entry<String, Boolean> entry = modules.get(i);
            String id = entry.getKey();
            y = toggle(y, human(id), entry.getValue(), () -> {
                boolean next = !DAI_Config.featureModuleEnabled(id);
                if (DAI_Config.setFeatureModule(id, next)) {
                    open(Page.MODULES, current, "Module saved. Startup/registry modules may require a game restart.");
                }
            });
        }

        if (maxPage > 0) {
            int center = width / 2;
            int py = Math.min(height - 54, y + 2);
            addRenderableWidget(shellButton(center - 88, py, 40, 20, Component.literal("◀"),
                    button -> open(Page.MODULES, Math.max(0, current - 1), status), false));
            addRenderableWidget(shellButton(center - 43, py, 86, 20,
                    Component.literal((current + 1) + " / " + (maxPage + 1)), button -> {}, false));
            addRenderableWidget(shellButton(center + 48, py, 40, 20, Component.literal("▶"),
                    button -> open(Page.MODULES, Math.min(maxPage, current + 1), status), false));
        }
    }

    private int toggle(int y, String label, boolean value, Runnable action) {
        return rowButton(y, label, value ? "ON" : "OFF", action);
    }

    private int cyclePosition(int y, String label, ModConfigSpec.EnumValue<DAI_Position> config) {
        DAI_Position current;
        try { current = config.get(); } catch (IllegalStateException ignored) { current = DAI_Position.BOT_LEFT; }
        DAI_Position selected = current;
        return rowButton(y, label, current.displayName(), () -> {
            DAI_Position[] values = DAI_Position.values();
            DAI_Position next = values[(selected.ordinal() + 1) % values.length];
            setCommon(config, next, label + " updated.");
        });
    }

    private int numberRow(int y, String label, int value, int min, int max, int step, ModConfigSpec.IntValue config) {
        int center = width / 2;
        int left = Math.max(12, center - Math.min(310, width - 28) / 2);
        int rowWidth = Math.min(310, width - 28);
        addRenderableWidget(shellButton(left, y, rowWidth - 86, rowHeight(),
                Component.literal(label + ": " + value), button -> {}, false));
        addRenderableWidget(shellButton(left + rowWidth - 82, y, 38, rowHeight(), Component.literal("−"),
                button -> setCommon(config, Math.max(min, value - step), label + " updated."), false));
        addRenderableWidget(shellButton(left + rowWidth - 40, y, 38, rowHeight(), Component.literal("+"),
                button -> setCommon(config, Math.min(max, value + step), label + " updated."), false));
        return y + rowStride();
    }

    private int rowButton(int y, String label, String value, Runnable action) {
        int rowWidth = Math.min(420, width - 28);
        int left = width / 2 - rowWidth / 2;
        addRenderableWidget(shellButton(left, y, rowWidth, rowHeight(),
                Component.literal(label + ": " + value), button -> action.run(), false));
        return y + rowStride();
    }

    private int contentTop() {
        return height < 340 ? 66 : 74;
    }

    private int rowHeight() {
        return height < 340 ? 16 : 20;
    }

    private int rowStride() {
        return height < 340 ? 18 : 26;
    }

    private void cyclePresentation() {
        List<DAI_PresentationProfileService.Profile> profiles = DAI_PresentationProfileService.available();
        if (profiles.isEmpty()) return;
        String current = DAI_ClientConfig.presentationProfile();
        int index = 0;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id().equalsIgnoreCase(current)) { index = i; break; }
        }
        DAI_PresentationProfileService.Profile next = profiles.get((index + 1) % profiles.size());
        setClient(DAI_ClientConfig.PRESENTATION_PROFILE, next.id(), "Presentation changed to " + next.name() + ".");
    }

    private <T> void setCommon(ModConfigSpec.ConfigValue<T> value, T next, String message) {
        try {
            value.set(next);
            DAI_Config.save();
            reopen(message);
        } catch (RuntimeException exception) {
            reopen("Could not save common config: " + exception.getClass().getSimpleName());
        }
    }

    private <T> void setClient(ModConfigSpec.ConfigValue<T> value, T next, String message) {
        try {
            value.set(next);
            DAI_ClientConfig.save();
            reopen(message);
        } catch (RuntimeException exception) {
            reopen("Could not save client config: " + exception.getClass().getSimpleName());
        }
    }

    private boolean get(ModConfigSpec.BooleanValue value, boolean fallback) {
        try { return value.get(); } catch (IllegalStateException ignored) { return fallback; }
    }

    private void reopen(String message) {
        open(page, modulePage, message);
    }

    private void open(Page next, int nextModulePage, String message) {
        Minecraft.getInstance().gui.setScreen(new DAI_SettingsScreen(parent, next, nextModulePage, message));
    }

    private DAI_UniverseButton shellButton(
            int x, int y, int width, int height, Component label, Button.OnPress press, boolean selected
    ) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        int accent = selected ? profile.primary() : profile.secondary();
        return new DAI_UniverseButton(x, y, width, height, label, press, DAI_UniverseButton.Shape.CHIP, accent);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        graphics.fill(Math.max(8, width / 2 - 226), 36, Math.min(width - 8, width / 2 + 226), height - 34, 0x7205070C);
        graphics.centeredText(font, Component.literal("DAI ENGINE " + DAI_Core.FEATURE_LEVEL + " SETTINGS"), width / 2, 14, profile.text());
        graphics.centeredText(font, Component.literal("Player-facing config · changes save immediately"), width / 2, 27, 0xFF9FB2BE);
        if (!status.isBlank()) {
            graphics.centeredText(font, Component.literal(status), width / 2, height - 42, 0xFFFFD782);
        }
        if (page == Page.MODULES) {
            graphics.text(font, Component.literal("Module changes that affect startup registration may require restart."), 8, height - 16, 0xFFFFB66E);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Custom background above.
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }

    private static String display(Page page) {
        return switch (page) {
            case GENERAL -> "GENERAL";
            case AUTOMATION -> "AUTOMATION";
            case PRESENTATION -> "PRESENTATION";
            case MODULES -> "MODULES";
        };
    }

    private static String human(String id) {
        if (id == null || id.isBlank()) return "Module";
        String[] words = id.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
