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
import org.jspecify.annotations.NonNull;

/** Orange/purple live editor for DAI automation action JSON. */
public final class DAI_AutomationCreatorScreen extends Screen {
    private static final DAI_ButtonStyle ORANGE = new DAI_ButtonStyle(
            "#B0180D08", "#DE3A1A0A", "#EA5A2710", "#FFF3E8", "#FF8A2A");
    private static final DAI_ButtonStyle PURPLE = new DAI_ButtonStyle(
            "#B0120A1D", "#DE29123E", "#E8441E62", "#F9EEFF", "#A855F7");
    private static final DAI_ButtonStyle DANGER = new DAI_ButtonStyle(
            "#A6251018", "#D9481728", "#E45E1F35", "#FFF0F5", "#EF5A82");

    private EditBox idEdit;
    private EditBox pathEdit;
    private EditBox valueEdit;
    private EditBox rawEdit;
    private String status = "READY // load an action, patch a JSON path, apply live, then test";

    public DAI_AutomationCreatorScreen() {
        super(Component.literal("DAI Automation Creator"));
    }

    @Override
    protected void init() {
        super.init();
        int margin = 14;
        int top = 44;
        int panelW = Math.min(720, width - margin * 2);
        int x = (width - panelW) / 2;
        int inner = panelW - 20;

        idEdit = new EditBox(font, x + 10, 12, Math.max(120, inner - 260), 20, Component.literal("namespace:action"));
        idEdit.setValue(DAI_AutomationCreatorRuntime.id());
        addRenderableWidget(idEdit);
        int bx = x + 14 + Math.max(120, inner - 260);
        button(bx, 12, 56, 20, "NEW", ORANGE, () -> create()); bx += 60;
        button(bx, 12, 56, 20, "LOAD", PURPLE, () -> load()); bx += 60;
        button(bx, 12, 62, 20, "SAVE", ORANGE, () -> save()); bx += 66;
        button(bx, 12, 40, 20, "X", DANGER, this::onClose);

        pathEdit = new EditBox(font, x + 10, top + 26, inner - 120, 20, Component.literal("JSON path"));
        pathEdit.setValue("type");
        addRenderableWidget(pathEdit);
        valueEdit = new EditBox(font, x + 10, top + 52, inner - 120, 20, Component.literal("value"));
        valueEdit.setValue("sequence");
        addRenderableWidget(valueEdit);
        button(x + panelW - 104, top + 26, 94, 20, "SELECT FIELD", PURPLE, this::cycleField);
        button(x + panelW - 104, top + 52, 94, 20, "SET VALUE", ORANGE, this::setValue);

        rawEdit = new EditBox(font, x + 10, top + 90, inner, 20, Component.literal("compact automation JSON"));
        rawEdit.setMaxLength(32767);
        rawEdit.setValue(DAI_AutomationCreatorRuntime.compactJson());
        addRenderableWidget(rawEdit);

        int actionY = top + 120;
        int buttonW = (inner - 12) / 4;
        button(x + 10, actionY, buttonW, 22, "VALIDATE", PURPLE, this::validate);
        button(x + 14 + buttonW, actionY, buttonW, 22, "APPLY LIVE", ORANGE, this::applyLive);
        button(x + 18 + buttonW * 2, actionY, buttonW, 22, "TEST NOW", ORANGE, this::test);
        button(x + 22 + buttonW * 3, actionY, buttonW, 22, "SYNC RAW", PURPLE, this::syncRaw);

        int presetY = actionY + 34;
        int presetGap = 4;
        int presetW = Math.max(48, (inner - presetGap * 3) / 4);
        int px = x + 10;
        button(px, presetY, presetW, 20, "SEQUENCE", PURPLE, () -> field("type", "sequence")); px += presetW + presetGap;
        button(px, presetY, presetW, 20, "ACTION REF", PURPLE, () -> field("action", "namespace:action")); px += presetW + presetGap;
        button(px, presetY, presetW, 20, "WAIT TICKS", PURPLE, () -> field("ticks", "20")); px += presetW + presetGap;
        button(px, presetY, presetW, 20, "MENU OPEN", PURPLE, () -> field("type", "update_menu"));
    }

    private void create() {
        DAI_AutomationCreatorRuntime.create(id());
        rawEdit.setValue(DAI_AutomationCreatorRuntime.compactJson());
        send("create", "", "");
        status = "NEW // automation draft created";
    }

    private void load() {
        boolean ok = DAI_AutomationCreatorRuntime.load(id());
        send("load", "", "");
        if (ok) rawEdit.setValue(DAI_AutomationCreatorRuntime.compactJson());
        status = ok ? "LOADED // current runtime action mirrored" : "LOAD // no live action found; server export requested";
    }

    private void setValue() {
        DAI_AutomationCreatorRuntime.set(pathEdit.getValue(), valueEdit.getValue());
        rawEdit.setValue(DAI_AutomationCreatorRuntime.compactJson());
        send("set", pathEdit.getValue(), valueEdit.getValue());
        status = "PATCHED // " + pathEdit.getValue();
    }

    private void syncRaw() {
        boolean ok = DAI_AutomationCreatorRuntime.replaceRaw(rawEdit.getValue());
        if (ok) send("raw_json", "", rawEdit.getValue());
        status = ok ? "RAW JSON // synchronized" : "RAW JSON // invalid object";
    }

    private void validate() {
        syncRaw();
        status = DAI_AutomationCreatorRuntime.validate() == null
                ? "INVALID // action codec rejected this JSON"
                : "VALID // automation JSON is runtime-compatible";
    }

    private void applyLive() {
        if (!DAI_AutomationCreatorRuntime.replaceRaw(rawEdit.getValue())) {
            status = "INVALID // raw JSON was not applied";
            return;
        }
        boolean ok = DAI_AutomationCreatorRuntime.applyLive(id());
        if (ok) send("raw_json", "", rawEdit.getValue());
        status = ok ? "LIVE // action library updated without reload" : "INVALID // action codec rejected this JSON";
    }

    private void test() {
        if (!DAI_AutomationCreatorRuntime.replaceRaw(rawEdit.getValue())) {
            status = "INVALID // raw JSON was not applied";
            return;
        }
        boolean ok = DAI_AutomationCreatorRuntime.test(id());
        status = ok ? "TEST // live automation dispatched" : "INVALID // cannot dispatch draft";
    }

    private void save() {
        if (DAI_AutomationCreatorRuntime.replaceRaw(rawEdit.getValue())) send("raw_json", "", rawEdit.getValue());
        send("save", "", "");
        status = "SAVE // exported as ordinary DAI logics/definitions JSON";
    }

    private int fieldIndex;
    private static final String[] FIELDS = {"type", "action", "ticks", "menu", "open", "state", "value", "target"};
    private void cycleField() {
        fieldIndex = (fieldIndex + 1) % FIELDS.length;
        pathEdit.setValue(FIELDS[fieldIndex]);
        status = "FIELD // " + FIELDS[fieldIndex];
    }

    private void field(String path, String value) {
        pathEdit.setValue(path);
        valueEdit.setValue(value);
    }

    private String id() {
        String value = idEdit == null ? "" : idEdit.getValue().trim();
        return value.isBlank() ? DAI_AutomationCreatorRuntime.id() : value;
    }

    private void send(String operation, String key, String value) {
        DAI_ServerBridge.send(new DAI_CreatorActionPayload(operation, "automation", id(), key, value, 0, 0, 0));
    }

    private void button(int x, int y, int w, int h, String text, DAI_ButtonStyle style, Runnable action) {
        addRenderableWidget(new DAI_StyledButton(x, y, Math.max(24, w), h, Component.literal(text), b -> action.run(), style));
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int margin = 14;
        int panelW = Math.min(720, width - margin * 2);
        int x = (width - panelW) / 2;
        int top = 44;
        int bottom = Math.min(height - 22, top + 226);
        graphics.fillGradient(0, 0, width, height, 0xC00A0610, 0xA014091F);
        panel(graphics, x, top, panelW, bottom - top, 0xD10A0711, 0xFF7A3BB1);
        graphics.text(font, Component.literal("D.A.I. // AUTOMATION CREATOR"), x + 12, top + 8, 0xFFFF9B45);
        graphics.text(font, Component.literal("HOT JSON // PATCH → VALIDATE → APPLY → TEST"), x + 12, top + 18, 0xFFC58AEF);
        graphics.text(font, Component.literal("RAW JSON"), x + 12, top + 80, 0xFFFFA260);
        graphics.text(font, Component.literal(status), x + 12, bottom - 18, 0xFFF4D6A8);
        graphics.text(font, Component.literal("Changes apply to the live DAI action library; SAVE exports normal datapack JSON."),
                x + 12, bottom - 32, 0xFFA88ABF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        send("close", "", "");
        Minecraft.getInstance().gui.setScreen(null);
    }

    private static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill, int border) {
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.outline(x, y, w, h, border);
        graphics.fill(x + 2, y + 2, x + w - 2, y + 3, 0xAAFF8428);
        graphics.fill(x + w - 3, y + 3, x + w - 2, y + h - 3, 0xAAA855F7);
    }
}
