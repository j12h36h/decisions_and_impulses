package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.gamerules.DAI_WorldGameRuleOverrides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * DAI-native modular editor for Minecraft's dynamic game-rule registry.
 *
 * <p>This is intentionally not a reskinned vanilla Game Rules list. Categories,
 * rules, defaults and value types are discovered from Minecraft's live registry,
 * so vanilla additions and mod-registered rules appear automatically.</p>
 */
public final class DAI_WorldGameRulesScreen extends Screen {

    private static final String ALL = "all";

    private final Screen parent;
    private final DAI_PlayWorldAccess.WorldEntry world;
    private final String category;
    private final int page;

    private List<DAI_WorldGameRuleOverrides.RuleEntry> allRules = List.of();
    private List<DAI_WorldGameRuleOverrides.RuleEntry> visibleRules = List.of();
    private List<String> categories = List.of(ALL);
    private final List<RuleField> fields = new ArrayList<>();

    public DAI_WorldGameRulesScreen(Screen parent, DAI_PlayWorldAccess.WorldEntry world) {
        this(parent, world, ALL, 0);
    }

    private DAI_WorldGameRulesScreen(
            Screen parent,
            DAI_PlayWorldAccess.WorldEntry world,
            String category,
            int page
    ) {
        super(Component.literal("DAI Game Rules"));
        this.parent = parent;
        this.world = world;
        this.category = category == null || category.isBlank() ? ALL : category.toLowerCase(Locale.ROOT);
        this.page = Math.max(0, page);
    }

    @Override
    protected void init() {
        super.init();
        fields.clear();

        allRules = new ArrayList<>(DAI_PlayWorldAccess.gameRuleEntries(world));
        allRules.sort(Comparator
                .comparing(DAI_WorldGameRuleOverrides.RuleEntry::category, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(this::displayName, String.CASE_INSENSITIVE_ORDER));

        Set<String> categorySet = new LinkedHashSet<>();
        categorySet.add(ALL);
        allRules.stream()
                .map(DAI_WorldGameRuleOverrides.RuleEntry::category)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(categorySet::add);
        categories = List.copyOf(categorySet);

        visibleRules = allRules.stream()
                .filter(entry -> ALL.equals(category) || category.equalsIgnoreCase(entry.category()))
                .toList();

        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        int margin = Math.max(8, Math.min(14, width / 40));
        int top = 43;
        int bottom = height - 34;
        int railWidth = Math.max(88, Math.min(118, width / 4));
        int railX = margin;
        int contentX = railX + railWidth + 10;
        int contentWidth = Math.max(150, width - contentX - margin);

        // Dynamic category rail. It is generated from GameRuleCategory rather
        // than a fixed DAI/Minecraft list.
        int categoryY = top + 24;
        int categoryHeight = Math.max(18, Math.min(22, (bottom - categoryY - 4) / Math.max(1, categories.size())));
        for (String value : categories) {
            boolean selected = value.equals(category);
            addRenderableWidget(new DAI_UniverseButton(
                    railX + 6,
                    categoryY,
                    railWidth - 12,
                    categoryHeight,
                    Component.literal(categoryLabel(value)),
                    b -> reopen(value, 0),
                    DAI_UniverseButton.Shape.CHIP,
                    selected ? profile.primary() : profile.secondary()
            ));
            categoryY += categoryHeight + 3;
        }

        int rows = rowCount(top, bottom);
        int maxPage = Math.max(0, (visibleRules.size() - 1) / rows);
        int currentPage = Math.min(page, maxPage);
        int start = Math.min(currentPage * rows, visibleRules.size());
        int end = Math.min(start + rows, visibleRules.size());
        int rowGap = 5;
        int rowHeight = Math.max(31, Math.min(39, (bottom - top - 34 - rowGap * Math.max(0, rows - 1)) / rows));
        int y = top + 24;

        for (int i = start; i < end; i++) {
            DAI_WorldGameRuleOverrides.RuleEntry entry = visibleRules.get(i);
            int resetWidth = 48;
            int controlRight = contentX + contentWidth - 6;
            int resetX = controlRight - resetWidth;

            addRenderableWidget(button(
                    resetX,
                    y + Math.max(4, (rowHeight - 20) / 2),
                    resetWidth,
                    20,
                    "RESET",
                    b -> reset(entry),
                    false
            ));

            if (entry.type() == DAI_WorldGameRuleOverrides.ValueType.BOOLEAN) {
                int toggleWidth = 62;
                int toggleX = resetX - toggleWidth - 6;
                boolean enabled = "true".equalsIgnoreCase(entry.value());
                addRenderableWidget(button(
                        toggleX,
                        y + Math.max(4, (rowHeight - 20) / 2),
                        toggleWidth,
                        20,
                        enabled ? "ON" : "OFF",
                        b -> set(entry, enabled ? "false" : "true"),
                        enabled
                ));
            } else {
                int saveWidth = 44;
                int fieldWidth = Math.max(52, Math.min(86, contentWidth / 4));
                int fieldX = resetX - saveWidth - fieldWidth - 12;
                int fy = y + Math.max(4, (rowHeight - 20) / 2);

                EditBox edit = new EditBox(font, fieldX, fy, fieldWidth, 20, Component.literal(entry.id()));
                edit.setMaxLength(64);
                edit.setValue(entry.value());
                edit.setBordered(false);
                edit.setTextShadow(true);
                addRenderableWidget(edit);
                fields.add(new RuleField(edit));

                addRenderableWidget(button(
                        fieldX + fieldWidth + 4,
                        fy,
                        saveWidth,
                        20,
                        "SET",
                        b -> setFromField(entry, edit),
                        false
                ));
            }
            y += rowHeight + rowGap;
        }

        int navY = height - 27;
        addRenderableWidget(button(margin, navY, railWidth, 20, "BACK", b -> onClose(), true));
        if (maxPage > 0) {
            int navWidth = Math.min(190, contentWidth);
            int navX = contentX + contentWidth - navWidth;
            int side = Math.max(50, (navWidth - 58) / 2);
            addRenderableWidget(button(navX, navY, side, 20, "◀", b -> reopen(category, Math.max(0, currentPage - 1)), false));
            addRenderableWidget(button(navX + side + 4, navY, 50, 20,
                    (currentPage + 1) + "/" + (maxPage + 1), b -> {}, false));
            addRenderableWidget(button(navX + side + 58, navY, side, 20, "▶",
                    b -> reopen(category, Math.min(maxPage, currentPage + 1)), false));
        }
    }

    private int rowCount(int top, int bottom) {
        int available = Math.max(80, bottom - top - 42);
        return Math.max(2, Math.min(5, available / 37));
    }

    private void setFromField(DAI_WorldGameRuleOverrides.RuleEntry entry, EditBox edit) {
        if (edit == null) return;
        String value = edit.getValue() == null ? "" : edit.getValue().trim();
        if (value.isBlank()) {
            edit.setValue(entry.value());
            return;
        }
        if (!DAI_PlayWorldAccess.setGameRule(world, entry.id(), value)) {
            edit.setValue(entry.value());
            return;
        }
        reopen(category, page);
    }

    private void set(DAI_WorldGameRuleOverrides.RuleEntry entry, String value) {
        if (DAI_PlayWorldAccess.setGameRule(world, entry.id(), value)) reopen(category, page);
    }

    private void reset(DAI_WorldGameRuleOverrides.RuleEntry entry) {
        if (DAI_PlayWorldAccess.resetGameRule(world, entry.id())) reopen(category, page);
    }

    private void reopen(String nextCategory, int nextPage) {
        Minecraft.getInstance().gui.setScreen(new DAI_WorldGameRulesScreen(parent, world, nextCategory, nextPage));
    }

    private DAI_UniverseButton button(
            int x, int y, int w, int h,
            String text,
            Button.OnPress press,
            boolean primary
    ) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        return new DAI_UniverseButton(
                x, y, w, h, Component.literal(text), press,
                DAI_UniverseButton.Shape.CHIP,
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

        int margin = Math.max(8, Math.min(14, width / 40));
        int top = 43;
        int bottom = height - 34;
        int railWidth = Math.max(88, Math.min(118, width / 4));
        int railX = margin;
        int contentX = railX + railWidth + 10;
        int contentWidth = Math.max(150, width - contentX - margin);

        graphics.fill(0, 0, width, 34, 0xE507090E);
        graphics.fill(0, 33, width, 34, (profile.primary() & 0x00FFFFFF) | 0xA0000000);
        graphics.text(font, Component.literal("DAI // WORLD RULES"), 12, 9, profile.primary());
        String worldName = DAI_PlayWorldAccess.displayName(world);
        int worldWidth = font.width(worldName);
        graphics.text(font, Component.literal(worldName), Math.max(12, width - worldWidth - 12), 9, profile.text());

        graphics.fill(railX, top, railX + railWidth, bottom, 0xC2070A10);
        graphics.outline(railX, top, railWidth, Math.max(1, bottom - top), (profile.secondary() & 0x00FFFFFF) | 0x80000000);
        graphics.fill(railX, top, railX + 3, bottom, (profile.primary() & 0x00FFFFFF) | 0xD0000000);
        graphics.text(font, Component.literal("MODULES"), railX + 8, top + 8, profile.primary());

        graphics.fill(contentX, top, contentX + contentWidth, bottom, 0xB9070A10);
        graphics.outline(contentX, top, contentWidth, Math.max(1, bottom - top), (profile.secondary() & 0x00FFFFFF) | 0x55000000);
        graphics.text(font, Component.literal(categoryLabel(category) + " RULES"), contentX + 8, top + 8, 0xFFB7C7D0);

        int rows = rowCount(top, bottom);
        int maxPage = Math.max(0, (visibleRules.size() - 1) / rows);
        int currentPage = Math.min(page, maxPage);
        int start = Math.min(currentPage * rows, visibleRules.size());
        int end = Math.min(start + rows, visibleRules.size());
        int rowGap = 5;
        int rowHeight = Math.max(31, Math.min(39, (bottom - top - 34 - rowGap * Math.max(0, rows - 1)) / rows));
        int y = top + 24;

        for (int i = start; i < end; i++) {
            DAI_WorldGameRuleOverrides.RuleEntry entry = visibleRules.get(i);
            boolean changed = entry.overridden();
            int edge = changed ? profile.primary() : ((profile.secondary() & 0x00FFFFFF) | 0x66000000);
            graphics.fill(contentX + 5, y, contentX + contentWidth - 5, y + rowHeight, changed ? 0xB713101D : 0xA90A0D14);
            graphics.outline(contentX + 5, y, contentWidth - 10, rowHeight, edge);
            if (changed) graphics.fill(contentX + 5, y, contentX + 8, y + rowHeight, profile.primary());

            String name = displayName(entry);
            graphics.text(font, Component.literal(name), contentX + 12, y + 6, profile.text());
            String sub = shortId(entry.id()) + "  ·  DEFAULT " + entry.defaultValue();
            graphics.text(font, Component.literal(sub), contentX + 12, y + 18, 0xFF81939E, false);
            y += rowHeight + rowGap;
        }

        for (RuleField field : fields) {
            EditBox edit = field.edit();
            int x = edit.getX();
            int ey = edit.getY();
            int w = edit.getWidth();
            int h = edit.getHeight();
            graphics.fill(x, ey, x + w, ey + h, 0xBC080A10);
            graphics.outline(x, ey, w, h, edit.isHoveredOrFocused() ? profile.primary() : 0x885F4A73);
        }

        if (allRules.isEmpty()) {
            graphics.centeredText(font, Component.literal("Minecraft exposed no registered game rules."),
                    contentX + contentWidth / 2, top + 64, 0xFFE3A65B);
        } else if (visibleRules.isEmpty()) {
            graphics.centeredText(font, Component.literal("No rules are registered in this module."),
                    contentX + contentWidth / 2, top + 64, 0xFFB7C7D0);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private String displayName(DAI_WorldGameRuleOverrides.RuleEntry entry) {
        if (entry == null) return "RULE";
        try {
            Component translated = Component.translatable(entry.descriptionId());
            String value = translated.getString();
            if (value != null && !value.isBlank() && !value.equals(entry.descriptionId())) return value;
        } catch (RuntimeException ignored) {}
        return prettify(shortId(entry.id()));
    }

    private static String shortId(String id) {
        if (id == null) return "";
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static String categoryLabel(String value) {
        if (value == null || value.isBlank() || ALL.equalsIgnoreCase(value)) return "ALL";
        return prettify(value).toUpperCase(Locale.ROOT);
    }

    private static String prettify(String raw) {
        if (raw == null || raw.isBlank()) return "RULE";
        String[] parts = raw.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.isEmpty() ? raw : out.toString();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return DAI_ShellWorldRuntime.isShellActive();
    }

    private record RuleField(EditBox edit) {}
}
