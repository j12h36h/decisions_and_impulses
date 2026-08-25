package io.github.j12h36h.dai.client.creator;

import io.github.j12h36h.dai.client.menus.DAI_StyledButton;
import io.github.j12h36h.dai.client.menus.system.DAI_ButtonStyle;
import io.github.j12h36h.dai.client.network.DAI_ServerBridge;
import io.github.j12h36h.dai.network.DAI_CreatorActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Responsive orange/purple DAI Creator workspace. The main view intentionally
 * exposes only navigation, one property edit, one preset and mode controls;
 * transform/destructive actions live in a separate tool drawer.
 */
public final class DAI_CreatorScreen extends Screen {
    private static final KindGroup WORLD = new KindGroup("WORLD", List.of(
            "physics", "block", "portal", "interactive", "fluid", "structure", "feature"));
    private static final KindGroup GAMEPLAY = new KindGroup("GAMEPLAY", List.of(
            "entity", "item", "vehicle", "projectile", "effect", "potion"));
    private static final KindGroup PRESENT = new KindGroup("PRESENT", List.of(
            "particle", "sound", "music", "hud", "timeline"));
    private static final List<KindGroup> GROUPS = List.of(WORLD, GAMEPLAY, PRESENT);

    private static final DAI_ButtonStyle ORANGE = new DAI_ButtonStyle(
            "#A8140B09", "#D936170B", "#EA5A2710", "#FFF4EA", "#FF8A2A");
    private static final DAI_ButtonStyle PURPLE = new DAI_ButtonStyle(
            "#A8100918", "#D827123B", "#E6421C60", "#FAF0FF", "#A855F7");
    private static final DAI_ButtonStyle DARK = new DAI_ButtonStyle(
            "#A70B0710", "#D31B1025", "#E32A1738", "#F8EFFD", "#704090");
    private static final DAI_ButtonStyle DANGER = new DAI_ButtonStyle(
            "#A6231018", "#D7471728", "#E45C1F35", "#FFF0F5", "#EF5A82");

    private int groupIndex;
    private int kindIndex;
    private int presetIndex;
    private EditBox idEdit;
    private EditBox keyEdit;
    private EditBox valueEdit;
    private String status = "READY // choose a type, create/load, then author in the live world";

    private DAI_StyledButton editMode;
    private DAI_StyledButton previewMode;
    private DAI_StyledButton simulateMode;

    public DAI_CreatorScreen() {
        this(groupForKind(DAI_CreatorRuntime.kind()), DAI_CreatorRuntime.kind());
    }

    private DAI_CreatorScreen(int group, String kind) {
        super(Component.literal("DAI Creator"));
        groupIndex = clamp(group, 0, GROUPS.size() - 1);
        int found = GROUPS.get(groupIndex).kinds().indexOf(kind);
        kindIndex = found < 0 ? 0 : found;
    }

    @Override
    protected void init() {
        super.init();
        DAI_CreatorRuntime.open(Minecraft.getInstance().player);
        send("open", "", "", 0, 0, 0);

        int margin = 10;
        int topH = 38;
        int bottomH = 34;
        int leftW = Math.min(118, Math.max(92, width / 7));
        int rightW = Math.min(230, Math.max(184, width / 4));
        int left = margin;
        int right = width - rightW - margin;

        buildTop(width < 600 ? margin : left + leftW + margin, width - margin);
        buildNav(left, topH + margin, leftW);
        buildInspector(right, topH + margin, rightW);
        buildModes(margin, height - bottomH + 4, width - margin * 2);
        refreshModes();
    }

    private void buildTop(int start, int end) {
        int y = 9;
        int available = Math.max(260, end - start);
        int idW = Math.max(82, available - 214);
        idEdit = new EditBox(font, start, y, idW, 20, Component.literal("namespace:id"));
        idEdit.setValue(DAI_CreatorRuntime.id());
        addRenderableWidget(idEdit);
        int x = start + idW + 5;
        button(x, y, 48, 20, "NEW", ORANGE, this::create); x += 52;
        button(x, y, 48, 20, "LOAD", PURPLE, this::load); x += 52;
        button(x, y, 48, 20, "SAVE", ORANGE, () -> send("save", "", "", 0, 0, 0)); x += 52;
        button(x, y, 34, 20, "X", DANGER, this::onClose);
    }

    private void buildNav(int x, int y, int w) {
        int inner = w - 8;
        for (int i = 0; i < GROUPS.size(); i++) {
            final int index = i;
            DAI_StyledButton b = button(x + 4, y, inner, 20, GROUPS.get(i).title(), DARK,
                    () -> switchGroup(index));
            b.setSelectedStyle(i == groupIndex);
            y += 24;
        }
        y += 10;
        int arrow = 28;
        button(x + 4, y, arrow, 20, "<", PURPLE, this::previousKind);
        button(x + w - arrow - 4, y, arrow, 20, ">", PURPLE, this::nextKind);
    }

    private void buildInspector(int x, int y, int w) {
        int inner = w - 16;
        int left = x + 8;
        int top = y + 30;
        keyEdit = new EditBox(font, left, top, inner, 20, Component.literal("JSON path"));
        keyEdit.setValue(defaultPath(kind()));
        addRenderableWidget(keyEdit);
        valueEdit = new EditBox(font, left, top + 26, Math.max(70, inner - 52), 20, Component.literal("value"));
        valueEdit.setValue(defaultValue(kind()));
        addRenderableWidget(valueEdit);
        button(left + inner - 48, top + 26, 48, 20, "SET", ORANGE, this::setValue);

        int presetY = top + 68;
        button(left, presetY, 30, 20, "<", PURPLE, this::previousPreset);
        button(left + inner - 30, presetY, 30, 20, ">", PURPLE, this::nextPreset);
        button(left, presetY + 26, inner, 22, "APPLY PRESET", ORANGE, this::applyPreset);
    }

    private void buildModes(int x, int y, int totalW) {
        int gap = 5;
        int count = 5;
        int usable = Math.max(150, totalW - gap * (count - 1));
        int base = Math.max(28, Math.min(82, usable / count));
        int[] widths = new int[]{base, base, base, base, base};
        int need = base * count + gap * (count - 1);
        x += Math.max(0, totalW - need) / 2;

        editMode = button(x, y, widths[0], 20, "EDIT", PURPLE,
                () -> mode(DAI_CreatorRuntime.EditorMode.EDIT)); x += widths[0] + gap;
        previewMode = button(x, y, widths[1], 20, "PREVIEW", PURPLE,
                () -> mode(DAI_CreatorRuntime.EditorMode.PREVIEW)); x += widths[1] + gap;
        simulateMode = button(x, y, widths[2], 20, "SIMULATE", ORANGE,
                () -> mode(DAI_CreatorRuntime.EditorMode.SIMULATE)); x += widths[2] + gap;
        button(x, y, widths[3], 20, "TOOLS", ORANGE,
                () -> Minecraft.getInstance().gui.setScreen(new DAI_CreatorToolsScreen(this, kind(), id()))); x += widths[3] + gap;
        button(x, y, widths[4], 20, "HOLOGRAM", PURPLE,
                () -> send("hologram", "", "", 0, 0, 0));
    }

    private void create() {
        Vec3 p = playerPos();
        DAI_CreatorRuntime.create(kind(), id(), p);
        send("create", "", "", p.x, p.y, p.z);
        mode(DAI_CreatorRuntime.EditorMode.PREVIEW);
        status = "CREATED // " + kind() + " " + id();
    }

    private void load() {
        boolean local = DAI_CreatorRuntime.load(kind(), id());
        send("load", "", "", 0, 0, 0);
        status = local ? "LOADED // live registry mirrored" : "LOAD REQUEST // server/export source";
    }

    private void setValue() {
        String key = keyEdit.getValue();
        String value = valueEdit.getValue();
        DAI_CreatorRuntime.set(key, value);
        send("set", key, value, 0, 0, 0);
        status = "PROPERTY // " + key + " = " + value;
    }

    private void mode(DAI_CreatorRuntime.EditorMode next) {
        DAI_CreatorRuntime.setMode(next);
        send("mode", "", next.name().toLowerCase(), 0, 0, 0);
        refreshModes();
        status = next == DAI_CreatorRuntime.EditorMode.SIMULATE
                ? "SIMULATE // live runtime behavior" : next.name() + " // authoring workspace";
    }

    private void refreshModes() {
        if (editMode != null) editMode.setSelectedStyle(DAI_CreatorRuntime.mode() == DAI_CreatorRuntime.EditorMode.EDIT);
        if (previewMode != null) previewMode.setSelectedStyle(DAI_CreatorRuntime.mode() == DAI_CreatorRuntime.EditorMode.PREVIEW);
        if (simulateMode != null) simulateMode.setSelectedStyle(DAI_CreatorRuntime.mode() == DAI_CreatorRuntime.EditorMode.SIMULATE);
    }

    private void previousKind() { switchKind(Math.floorMod(kindIndex - 1, group().kinds().size())); }
    private void nextKind() { switchKind((kindIndex + 1) % group().kinds().size()); }
    private void previousPreset() { presetIndex = Math.floorMod(presetIndex - 1, presets().size()); }
    private void nextPreset() { presetIndex = (presetIndex + 1) % presets().size(); }

    private void applyPreset() {
        String preset = presets().get(Math.floorMod(presetIndex, presets().size()));
        switch (preset) {
            case "GRAVITY DOWN" -> gravity(0, -1, 0, 0.08);
            case "GRAVITY UP" -> gravity(0, 1, 0, 0.08);
            case "ZERO G" -> {
                set("numbers.gravity_strength", "0"); set("numbers.surface_drag", "0");
                set("numbers.max_speed", "0.8"); set("flags.free_flight", "true");
            }
            case "GRAVITY NORTH" -> gravity(0, 0, -1, 0.08);
            case "GRAVITY EAST" -> gravity(1, 0, 0, 0.08);
            case "ALL ENTITIES" -> set("properties.affects", "all");
            case "LOW GRAVITY" -> set("numbers.gravity_strength", "0.03");
            case "ARCADE VEHICLE" -> { set("numbers.acceleration", "0.06"); set("numbers.turn_rate", "7"); set("numbers.drag", "0.04"); }
            case "HEAVY VEHICLE" -> { set("numbers.acceleration", "0.025"); set("numbers.turn_rate", "3"); set("numbers.drag", "0.015"); }
            case "STRAIGHT SHOT" -> set("stats.gravity", "0");
            case "ARC SHOT" -> set("stats.gravity", "0.05");
            case "HOMING" -> { set("projectile.homing_radius", "16"); set("projectile.homing_strength", "0.18"); }
            case "RETURNING" -> set("projectile.return_to_owner", "true");
            case "FULL BRIGHT" -> set("particle.full_bright", "true");
            case "COLLIDING" -> set("particle.collision", "true");
            case "AI ON" -> set("entity.vanilla_ai", "true");
            case "AI OFF" -> set("entity.vanilla_ai", "false");
            case "BOX VOLUME" -> set("properties.shape", "box");
            case "SPHERE VOLUME" -> set("properties.shape", "sphere");
            default -> mode(DAI_CreatorRuntime.EditorMode.PREVIEW);
        }
        status = "PRESET // " + preset;
    }

    private void gravity(double x, double y, double z, double strength) {
        set("numbers.gravity_x", Double.toString(x)); set("numbers.gravity_y", Double.toString(y));
        set("numbers.gravity_z", Double.toString(z)); set("numbers.gravity_strength", Double.toString(strength));
    }

    private void set(String key, String value) {
        DAI_CreatorRuntime.set(key, value);
        send("set", key, value, 0, 0, 0);
    }

    private List<String> presets() {
        return switch (kind()) {
            case "physics" -> List.of("GRAVITY DOWN", "GRAVITY UP", "ZERO G", "GRAVITY NORTH", "GRAVITY EAST", "ALL ENTITIES", "LOW GRAVITY");
            case "vehicle" -> List.of("ARCADE VEHICLE", "HEAVY VEHICLE");
            case "projectile" -> List.of("STRAIGHT SHOT", "ARC SHOT", "HOMING", "RETURNING");
            case "particle" -> List.of("FULL BRIGHT", "COLLIDING");
            case "entity" -> List.of("AI ON", "AI OFF");
            case "portal", "interactive" -> List.of("BOX VOLUME", "SPHERE VOLUME", "ALL ENTITIES");
            default -> List.of("LIVE PREVIEW");
        };
    }

    private void switchGroup(int next) {
        int group = clamp(next, 0, GROUPS.size() - 1);
        Minecraft.getInstance().gui.setScreen(new DAI_CreatorScreen(group, GROUPS.get(group).kinds().get(0)));
    }

    private void switchKind(int next) {
        Minecraft.getInstance().gui.setScreen(new DAI_CreatorScreen(groupIndex, group().kinds().get(next)));
    }

    private DAI_StyledButton button(int x, int y, int w, int h, String text, DAI_ButtonStyle style, Runnable action) {
        DAI_StyledButton b = new DAI_StyledButton(x, y, Math.max(24, w), h, Component.literal(text), ignored -> action.run(), style);
        addRenderableWidget(b);
        return b;
    }

    private void send(String operation, String key, String value, double x, double y, double z) {
        DAI_ServerBridge.send(new DAI_CreatorActionPayload(operation, kind(), id(), key, value, x, y, z));
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int margin = 10;
        int topH = 38;
        int bottomH = 34;
        int leftW = Math.min(118, Math.max(92, width / 7));
        int rightW = Math.min(230, Math.max(184, width / 4));
        int right = width - rightW - margin;
        int bodyTop = topH + margin;
        int bodyBottom = height - bottomH - 4;

        graphics.fillGradient(0, 0, width, topH + 4, 0xE0090610, 0xB012081B);
        graphics.fillGradient(0, height - bottomH, width, height, 0xC012081B, 0xEA09060F);
        panel(graphics, margin, bodyTop, leftW, bodyBottom - bodyTop, 0xA60B0710, 0xFF7A3CB1);
        panel(graphics, right, bodyTop, rightW, bodyBottom - bodyTop, 0xB50C0711, 0xFFFF8428);

        graphics.text(font, Component.literal("D.A.I. // CREATOR"), 12, 8, 0xFFFFA05B);
        graphics.text(font, Component.literal("UNIVERSAL WIREFRAME AUTHORING"), 12, 20, 0xFFC48AEF);
        graphics.text(font, Component.literal(kindLabel(kind()) + " // " + id()),
                Math.max(leftW + 24, width / 2 - 80), 31, 0xFFF7EFFF);

        graphics.text(font, Component.literal("SPACE"), margin + 8, bodyTop + 8, 0xFFA977D2);
        graphics.centeredText(font, Component.literal(kindLabel(kind())), margin + leftW / 2, bodyTop + 96, 0xFFFFA05B);
        graphics.text(font, Component.literal("INSPECTOR"), right + 8, bodyTop + 8, 0xFFFF9A50);
        graphics.text(font, Component.literal(contextHint(kind())), right + 8, bodyTop + 18, 0xFFB38DC6);
        graphics.centeredText(font, Component.literal(presets().get(Math.floorMod(presetIndex, presets().size()))),
                right + rightW / 2, bodyTop + 133, 0xFFE7C8FA);

        drawWireframe(graphics, margin + leftW + 10, right - 10, bodyTop + 6, bodyBottom - 6);
        graphics.text(font, Component.literal(status), 12, height - bottomH - 12, 0xFFFFC27C);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void onClose() {
        send("close", "", "", 0, 0, 0);
        DAI_CreatorRuntime.close();
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override public boolean isPauseScreen() { return false; }

    private String id() {
        String v = idEdit == null ? "" : idEdit.getValue().trim();
        return v.isBlank() ? DAI_CreatorRuntime.id() : v;
    }
    private Vec3 playerPos() { var p = Minecraft.getInstance().player; return p == null ? Vec3.ZERO : p.position(); }
    private KindGroup group() { return GROUPS.get(clamp(groupIndex, 0, GROUPS.size() - 1)); }
    private String kind() { return group().kinds().get(clamp(kindIndex, 0, group().kinds().size() - 1)); }

    private static String defaultPath(String kind) {
        return switch (kind) {
            case "physics" -> "numbers.gravity_strength"; case "vehicle" -> "numbers.max_speed";
            case "projectile" -> "stats.projectile_speed"; case "particle" -> "particle.lifetime";
            case "portal", "interactive" -> "properties.affects"; case "entity" -> "events.spawn";
            default -> "display_name";
        };
    }
    private static String defaultValue(String kind) {
        return switch (kind) {
            case "physics" -> "0.08"; case "vehicle" -> "0.8"; case "projectile" -> "1.5";
            case "particle" -> "30"; case "portal", "interactive" -> "all";
            case "entity" -> "example:entity/spawn"; default -> "Creator Draft";
        };
    }
    private static String contextHint(String kind) {
        return switch (kind) {
            case "physics" -> "gravity / drag / volume"; case "vehicle" -> "handling / speed / boost";
            case "projectile" -> "trajectory / collision"; case "portal" -> "destination / volume";
            case "particle" -> "motion / render"; case "entity" -> "behavior / events";
            default -> "properties / events / JSON";
        };
    }
    private static int groupForKind(String kind) {
        for (int i = 0; i < GROUPS.size(); i++) if (GROUPS.get(i).kinds().contains(kind)) return i;
        return 0;
    }
    private static String kindLabel(String raw) { return raw == null ? "UNKNOWN" : raw.replace('_', ' ').toUpperCase(); }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill); g.outline(x, y, w, h, border);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, 0x99FF8428);
        g.fill(x + w - 3, y + 3, x + w - 2, y + h - 3, 0x99A855F7);
    }
    private static void drawWireframe(GuiGraphicsExtractor g, int left, int right, int top, int bottom) {
        if (right - left < 80 || bottom - top < 80) return;
        int cx = (left + right) / 2, cy = (top + bottom) / 2;
        int orange = 0x66FF8428, purple = 0x66A855F7;
        g.outline(left + 8, top + 8, 22, 22, purple); g.outline(right - 30, top + 8, 22, 22, orange);
        g.outline(left + 8, bottom - 30, 22, 22, orange); g.outline(right - 30, bottom - 30, 22, 22, purple);
        g.fill(cx - 18, cy, cx - 5, cy + 1, orange); g.fill(cx + 5, cy, cx + 18, cy + 1, orange);
        g.fill(cx, cy - 18, cx + 1, cy - 5, purple); g.fill(cx, cy + 5, cx + 1, cy + 18, purple);
    }
    private record KindGroup(String title, List<String> kinds) {}
}
