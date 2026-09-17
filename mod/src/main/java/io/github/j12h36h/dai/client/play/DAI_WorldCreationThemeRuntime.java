package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.experience.DAI_ExperienceLaunchState;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * DAI presentation/layout layer for Minecraft's world-creation controllers.
 *
 * <p>Mojang continues to own world-creation state and callbacks. DAI moves the
 * existing controls into its own navigation/workspace/action-rail layout and
 * replaces only presentation. This keeps Minecraft 26.x validation, presets,
 * game rules and datapack behavior intact without visually falling back to the
 * vanilla Create World form.</p>
 */
public final class DAI_WorldCreationThemeRuntime {

    private static final Set<String> WORLD_CREATION_SCREENS = Set.of(
            "CreateWorldScreen",
            "WorldCreationGameRulesScreen",
            "ExperimentsScreen",
            "ConfirmExperimentalFeaturesScreen",
            "ExperimentalWarningScreen",
            "WarningScreen",
            "PackSelectionScreen",
            "CreateFlatWorldScreen",
            "PresetFlatWorldScreen",
            "CreateBuffetWorldScreen",
            "DatapackLoadFailureScreen",
            "GenericMessageScreen",
            "GenericWaitingScreen",
            "ConfirmScreen"
    );

    private static boolean initialized;

    private DAI_WorldCreationThemeRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Opening.class, DAI_WorldCreationThemeRuntime::onOpening);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, DAI_WorldCreationThemeRuntime::onScreenInit);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Background.class, DAI_WorldCreationThemeRuntime::onBackground);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, DAI_WorldCreationThemeRuntime::onForeground);
        DAI_Core.LOGGER.info("<DAI>: DAI world-creation presentation/layout layer initialized.");
    }

    public static boolean activeForCurrentScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.gui != null
                && isThemedScreen(minecraft.gui.screen());
    }

    public static boolean isThemedScreen(Screen screen) {
        if (screen == null || screen instanceof DAI_ExperimentalFeaturesScreen) return false;
        if (!daiOwnsCreationFlow()) return false;
        return WORLD_CREATION_SCREENS.contains(screen.getClass().getSimpleName());
    }

    private static boolean daiOwnsCreationFlow() {
        return DAI_VanillaWorldScreens.isCreateUiActive()
                || DAI_ExperienceLaunchState.pending() != null
                || DAI_ExperienceLauncher.hasPendingFreshLaunch();
    }

    /**
     * Experimental warnings need navigation semantics that Minecraft does not
     * know about: an Experience "No" should return to the Experience selector,
     * not reveal its underlying vanilla CreateWorldScreen. Retain the original
     * screen as controller and swap only its presentation.
     */
    private static void onOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (next == null || next instanceof DAI_ExperimentalFeaturesScreen || !daiOwnsCreationFlow()) return;
        if (!isExperimentalControllerScreen(next)) return;
        // Plain ConfirmScreen exposes its BooleanConsumer controller before init,
        // so it can be safely replaced at Opening. Dedicated warning screens
        // remain on Mojang's controller instance and receive the DAI layout below.
        if (!(next instanceof ConfirmScreen)) return;

        boolean experienceOwned = DAI_ExperienceLauncher.hasPendingFreshLaunch();
        event.setNewScreen(new DAI_ExperimentalFeaturesScreen(next, experienceOwned));
        DAI_Core.LOGGER.info(
                "<DAI>: Replaced Minecraft experimental warning '{}' with DAI presentation (experienceOwned={}).",
                next.getClass().getName(),
                experienceOwned
        );
    }

    public static boolean isExperimentalControllerScreen(Screen screen) {
        if (screen == null) return false;
        String simple = screen.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (simple.contains("experimental") || simple.equals("warningscreen")) return true;

        String title = "";
        try {
            if (screen.getTitle() != null) title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) {}
        if (title.contains("experimental")) return true;

        // Minecraft 26.2 currently presents fresh Experience experimental
        // confirmation as a plain ConfirmScreen. The pending-fresh ownership
        // makes this scope precise without changing unrelated confirmations.
        return screen instanceof ConfirmScreen && DAI_ExperienceLauncher.hasPendingFreshLaunch();
    }

    /**
     * Called by the AbstractButton mixin. Returning true suppresses Minecraft's
     * default sprite while preserving its original callback/input semantics.
     */
    public static boolean extractButtonBackground(AbstractButton button, GuiGraphicsExtractor graphics) {
        if (button == null || graphics == null || !activeForCurrentScreen()) return false;

        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        boolean highlighted = button.isHoveredOrFocused();
        boolean enabled = button.isActive();
        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();

        int fill = enabled
                ? (highlighted ? 0xE0221830 : 0xC20A0D14)
                : 0x8A07090D;
        int edge = enabled
                ? (highlighted ? profile.primary() : withAlpha(profile.secondary(), 0xA0))
                : withAlpha(profile.text(), 0x32);
        int rail = enabled ? withAlpha(profile.primary(), highlighted ? 0xFF : 0xB0) : withAlpha(profile.text(), 0x35);

        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + (highlighted ? 4 : 2), y + height, rail);
        graphics.outline(x, y, width, height, edge);
        if (highlighted && enabled) {
            graphics.outline(x - 1, y - 1, width + 2, height + 2, withAlpha(profile.primary(), 0x48));
        }
        return true;
    }

    /** Draws the DAI field frame before EditBox emits its text/cursor state. */
    public static void extractEditBoxBackground(EditBox box, GuiGraphicsExtractor graphics) {
        if (box == null || graphics == null || !activeForCurrentScreen() || !box.isVisible()) return;

        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        boolean highlighted = box.isHoveredOrFocused();
        int x = box.getX();
        int y = box.getY();
        int width = box.getWidth();
        int height = box.getHeight();

        graphics.fill(x, y, x + width, y + height, highlighted ? 0xDA11101A : 0xBC080A10);
        graphics.outline(x, y, width, height,
                highlighted ? profile.primary() : withAlpha(profile.secondary(), 0x88));
        graphics.fill(x, y, x + (highlighted ? 3 : 1), y + height,
                withAlpha(profile.primary(), highlighted ? 0xE8 : 0x80));
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!isThemedScreen(screen)) return;

        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        int text = profile.text() & 0x00FFFFFF;
        int inactive = withAlpha(profile.text(), 0x88) & 0x00FFFFFF;

        List<AbstractWidget> widgets = collectWidgets(event.getListenersList());
        for (AbstractWidget widget : widgets) {
            if (widget instanceof EditBox editBox) {
                editBox.setBordered(false);
                editBox.setTextColor(text);
                editBox.setTextColorUneditable(inactive);
                editBox.setTextShadow(true);
            }
        }

        if ("CreateWorldScreen".equals(screen.getClass().getSimpleName())) {
            layoutCreateWorld(screen, widgets);
        } else {
            layoutAuxiliary(screen, widgets);
        }
    }

    private static List<AbstractWidget> collectWidgets(List<? extends GuiEventListener> listeners) {
        Set<AbstractWidget> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<AbstractWidget> output = new ArrayList<>();
        Consumer<AbstractWidget> add = widget -> {
            if (widget != null && seen.add(widget)) output.add(widget);
        };

        for (GuiEventListener listener : listeners) {
            if (listener instanceof AbstractWidget widget) add.accept(widget);

            // TabNavigationBar and a few 26.x container widgets expose their
            // children only through visitWidgets. Probe that public layout
            // contract reflectively so this remains tolerant of minor mappings.
            try {
                Method visit = listener.getClass().getMethod("visitWidgets", Consumer.class);
                visit.invoke(listener, add);
            } catch (Throwable ignored) {
                // Direct AbstractWidget children were already captured.
            }
        }
        return output;
    }

    private static void layoutCreateWorld(Screen screen, List<AbstractWidget> widgets) {
        int margin = Math.max(10, Math.min(16, screen.width / 36));
        int top = 46;
        int bottom = screen.height - 38;
        int railWidth = Math.max(108, Math.min(156, screen.width / 4));
        int contentX = margin + railWidth + 14;
        int contentWidth = Math.max(120, screen.width - contentX - margin);

        List<WidgetSnapshot> navigation = new ArrayList<>();
        List<WidgetSnapshot> actions = new ArrayList<>();
        List<WidgetSnapshot> content = new ArrayList<>();

        for (AbstractWidget widget : widgets) {
            WidgetSnapshot snapshot = new WidgetSnapshot(widget);
            String label = label(widget);
            String simple = widget.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (isCreateAction(label) || isCancelAction(label)) {
                actions.add(snapshot);
            } else if (simple.contains("tabbutton") || isWorldTabLabel(label)) {
                navigation.add(snapshot);
            } else {
                content.add(snapshot);
            }
        }

        navigation.sort(Comparator.comparingInt(WidgetSnapshot::originalY).thenComparingInt(WidgetSnapshot::originalX));
        int navY = top + 26;
        for (WidgetSnapshot snapshot : navigation) {
            AbstractWidget widget = snapshot.widget();
            widget.setRectangle(Math.max(86, railWidth - 18), Math.max(20, snapshot.height()), margin + 8, navY);
            navY += Math.max(25, snapshot.height() + 6);
        }

        layoutRows(content, contentX + 8, top + 24, contentWidth - 16, bottom - 8);
        layoutActionRail(actions, contentX + 8, Math.max(top + 24, bottom - 28), contentWidth - 16);
    }

    private static void layoutAuxiliary(Screen screen, List<AbstractWidget> widgets) {
        int margin = Math.max(12, Math.min(22, screen.width / 24));
        int top = 48;
        int bottom = screen.height - 38;
        int contentX = margin + 10;
        int contentWidth = Math.max(120, screen.width - (margin + 10) * 2);

        List<WidgetSnapshot> actions = new ArrayList<>();
        List<WidgetSnapshot> content = new ArrayList<>();
        for (AbstractWidget widget : widgets) {
            WidgetSnapshot snapshot = new WidgetSnapshot(widget);
            String label = label(widget);
            if (isCreateAction(label) || isCancelAction(label) || label.equals("done")) actions.add(snapshot);
            else content.add(snapshot);
        }

        layoutRows(content, contentX, top + 10, contentWidth, bottom - 8);
        layoutActionRail(actions, contentX, Math.max(top + 10, bottom - 28), contentWidth);
    }

    private static void layoutRows(
            List<WidgetSnapshot> snapshots,
            int x,
            int startY,
            int width,
            int maxY
    ) {
        snapshots.sort(Comparator.comparingInt(WidgetSnapshot::originalY).thenComparingInt(WidgetSnapshot::originalX));
        List<List<WidgetSnapshot>> rows = new ArrayList<>();
        for (WidgetSnapshot snapshot : snapshots) {
            if (rows.isEmpty()) {
                rows.add(new ArrayList<>(List.of(snapshot)));
                continue;
            }
            List<WidgetSnapshot> row = rows.get(rows.size() - 1);
            if (Math.abs(row.get(0).originalY() - snapshot.originalY()) <= 5) row.add(snapshot);
            else rows.add(new ArrayList<>(List.of(snapshot)));
        }

        int y = startY;
        for (List<WidgetSnapshot> row : rows) {
            if (y >= maxY) break;
            row.sort(Comparator.comparingInt(WidgetSnapshot::originalX));
            int count = row.size();
            int gap = 8;
            int cell = Math.max(48, (width - gap * Math.max(0, count - 1)) / Math.max(1, count));
            int rowHeight = 0;

            for (int i = 0; i < count; i++) {
                WidgetSnapshot snapshot = row.get(i);
                AbstractWidget widget = snapshot.widget();
                int h = Math.max(9, snapshot.height());
                int targetWidth;
                if (widget instanceof EditBox && count == 1) {
                    targetWidth = width;
                } else if (count == 1) {
                    targetWidth = Math.min(width, Math.max(snapshot.width(), Math.min(width, 300)));
                } else {
                    targetWidth = cell;
                }
                int targetX = count == 1 ? x : x + i * (cell + gap);
                widget.setRectangle(targetWidth, h, targetX, y);
                rowHeight = Math.max(rowHeight, h);
            }
            y += Math.max(24, rowHeight + 8);
        }
    }

    private static void layoutActionRail(List<WidgetSnapshot> actions, int x, int y, int width) {
        if (actions.isEmpty()) return;
        actions.sort((a, b) -> {
            String la = label(a.widget());
            String lb = label(b.widget());
            if (isCancelAction(la) && !isCancelAction(lb)) return -1;
            if (!isCancelAction(la) && isCancelAction(lb)) return 1;
            if (isCreateAction(la) && !isCreateAction(lb)) return 1;
            if (!isCreateAction(la) && isCreateAction(lb)) return -1;
            return Integer.compare(a.originalX(), b.originalX());
        });

        int gap = 8;
        int count = actions.size();
        int cell = Math.min(170, Math.max(82, (width - gap * Math.max(0, count - 1)) / Math.max(1, count)));
        int total = cell * count + gap * Math.max(0, count - 1);
        int startX = x + Math.max(0, width - total);
        if (count == 2) startX = x;

        for (int i = 0; i < count; i++) {
            WidgetSnapshot snapshot = actions.get(i);
            int targetX = count == 2 && i == 1 ? x + width - cell : startX + i * (cell + gap);
            snapshot.widget().setRectangle(cell, Math.max(22, snapshot.height()), targetX, y);
        }
    }

    private static String label(AbstractWidget widget) {
        try {
            return widget.getMessage() == null
                    ? ""
                    : widget.getMessage().getString().trim().toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static boolean isCreateAction(String label) {
        return label.equals("create")
                || label.contains("create new world")
                || label.equals("done");
    }

    private static boolean isCancelAction(String label) {
        return label.equals("cancel") || label.equals("back") || label.startsWith("back ");
    }

    private static boolean isWorldTabLabel(String label) {
        return label.equals("game")
                || label.equals("world")
                || label.equals("more")
                || label.equals("general")
                || label.equals("advanced");
    }

    private static void onBackground(ScreenEvent.Render.Background event) {
        Screen screen = event.getScreen();
        if (!isThemedScreen(screen)) return;

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        int width = screen.width;
        int height = screen.height;

        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());

        int margin = Math.max(10, Math.min(16, width / 36));
        int top = 38;
        int bottom = height - 34;
        if ("CreateWorldScreen".equals(screen.getClass().getSimpleName())) {
            int railWidth = Math.max(108, Math.min(156, width / 4));
            int contentX = margin + railWidth + 14;

            graphics.fill(margin, top, margin + railWidth, bottom, 0xC2070A10);
            graphics.outline(margin, top, railWidth, Math.max(1, bottom - top), withAlpha(profile.secondary(), 0x80));
            graphics.fill(margin, top, margin + 3, bottom, withAlpha(profile.primary(), 0xD0));

            graphics.fill(contentX, top, width - margin, bottom, 0xB9070A10);
            graphics.outline(contentX, top, Math.max(1, width - margin - contentX), Math.max(1, bottom - top), withAlpha(profile.secondary(), 0x55));

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.font != null) {
                graphics.text(minecraft.font, ComponentText.CONFIGURATION, margin + 10, top + 8, profile.primary());
                graphics.text(minecraft.font, ComponentText.WORKSPACE, contentX + 10, top + 8, 0xFF9FB2BE);
            }
        } else {
            graphics.fill(margin, top, width - margin, bottom, 0xBE070A10);
            graphics.outline(margin, top, Math.max(1, width - margin * 2), Math.max(1, bottom - top), withAlpha(profile.secondary(), 0x70));
            graphics.fill(margin, top, margin + 3, bottom, withAlpha(profile.primary(), 0xB8));
        }
    }

    /** Covers Mojang's top title strip and replaces it with DAI hierarchy. */
    private static void onForeground(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!isThemedScreen(screen)) return;
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();

        graphics.fill(0, 0, screen.width, 34, 0xE507090E);
        graphics.fill(0, 33, screen.width, 34, withAlpha(profile.primary(), 0xA0));

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) return;
        graphics.text(minecraft.font, ComponentText.DAI_WORLD_CREATION, 12, 9, profile.primary());
        String section = sectionName(screen);
        int sectionWidth = minecraft.font.width(section);
        graphics.text(minecraft.font, net.minecraft.network.chat.Component.literal(section),
                Math.max(12, screen.width - sectionWidth - 12), 9, profile.text());
    }

    private static String sectionName(Screen screen) {
        if (screen == null) return "WORLD SETUP";
        return switch (screen.getClass().getSimpleName()) {
            case "CreateWorldScreen" -> "WORLD SETUP";
            case "WorldCreationGameRulesScreen" -> "GAME RULES";
            case "ExperimentsScreen", "ConfirmExperimentalFeaturesScreen", "ExperimentalWarningScreen", "WarningScreen" -> "EXPERIMENTAL FEATURES";
            case "PackSelectionScreen" -> "DATA PACKS";
            case "CreateFlatWorldScreen", "PresetFlatWorldScreen" -> "FLAT WORLD";
            case "CreateBuffetWorldScreen" -> "WORLD PRESET";
            case "DatapackLoadFailureScreen" -> "DATA PACK ERROR";
            case "GenericMessageScreen", "GenericWaitingScreen" -> "PREPARING WORLD";
            case "ConfirmScreen" -> DAI_ExperienceLaunchState.pending() != null
                    ? "EXPERIMENTAL FEATURES"
                    : "CONFIRMATION";
            default -> "WORLD SETUP";
        };
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private record WidgetSnapshot(AbstractWidget widget, int originalX, int originalY, int width, int height) {
        private WidgetSnapshot(AbstractWidget widget) {
            this(widget, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
        }
    }

    private static final class ComponentText {
        private static final net.minecraft.network.chat.Component CONFIGURATION = net.minecraft.network.chat.Component.literal("CONFIGURATION");
        private static final net.minecraft.network.chat.Component WORKSPACE = net.minecraft.network.chat.Component.literal("WORLD WORKSPACE");
        private static final net.minecraft.network.chat.Component DAI_WORLD_CREATION = net.minecraft.network.chat.Component.literal("DAI // WORLD CREATION");
    }
}
