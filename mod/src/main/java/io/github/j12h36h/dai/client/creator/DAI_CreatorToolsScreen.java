package io.github.j12h36h.dai.client.creator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.client.menus.DAI_StyledButton;
import io.github.j12h36h.dai.client.menus.system.DAI_ButtonStyle;
import io.github.j12h36h.dai.client.network.DAI_ServerBridge;
import io.github.j12h36h.dai.creator.DAI_CreatorSchemaDefinition;
import io.github.j12h36h.dai.creator.DAI_CreatorSchemaRegistry;
import io.github.j12h36h.dai.network.DAI_CreatorActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Data-driven Creator tool drawer.
 *
 * The engine owns only the shell. Every module-specific tool button is read
 * from the selected creator_schemas JSON definition's optional `tools` array.
 */
public final class DAI_CreatorToolsScreen extends Screen {
    private static final DAI_ButtonStyle ACTION = new DAI_ButtonStyle(
            "#A8140B09", "#D936170B", "#EA5A2710", "#FFF4EA", "#FF8A2A");
    private static final DAI_ButtonStyle SOFT = new DAI_ButtonStyle(
            "#A8100918", "#D827123B", "#E6421C60", "#FAF0FF", "#A855F7");
    private static final DAI_ButtonStyle DANGER = new DAI_ButtonStyle(
            "#A6231018", "#D7471728", "#E45C1F35", "#FFF0F5", "#EF5A82");

    private final Screen parent;
    private final String schemaId;
    private final String folder;
    private final String id;
    private final int page;

    public DAI_CreatorToolsScreen(Screen parent, String schemaId, String folder, String id) {
        this(parent, schemaId, folder, id, 0);
    }

    private DAI_CreatorToolsScreen(Screen parent, String schemaId, String folder, String id, int page) {
        super(Component.literal("DAI Creator Tools"));
        this.parent = parent;
        this.schemaId = schemaId == null ? "" : schemaId;
        this.folder = folder == null ? "" : folder;
        this.id = id == null ? "" : id;
        this.page = Math.max(0, page);
    }

    @Override
    protected void init() {
        super.init();
        List<JsonObject> tools = tools();
        int panelW = Math.min(510, Math.max(270, width - 40));
        int panelH = Math.min(310, Math.max(170, height - 70));
        int x = (width - panelW) / 2;
        int y = Math.max(28, (height - panelH) / 2);
        int columns = panelW >= 420 ? 3 : 2;
        int gap = 6;
        int innerW = panelW - 32;
        int buttonW = Math.max(72, (innerW - gap * (columns - 1)) / columns);
        int buttonH = 21;
        int startY = y + 44;
        int rows = Math.max(1, (panelH - 86) / (buttonH + gap));
        int perPage = Math.max(1, rows * columns);
        int pages = Math.max(1, (tools.size() + perPage - 1) / perPage);
        int actualPage = Math.min(page, pages - 1);
        int start = actualPage * perPage;
        int end = Math.min(tools.size(), start + perPage);

        for (int i = start; i < end; i++) {
            int local = i - start;
            int col = local % columns;
            int row = local / columns;
            JsonObject tool = tools.get(i);
            String label = DAI_CreatorSchemaDefinition.string(tool, "label", "TOOL");
            button(x + 16 + col * (buttonW + gap), startY + row * (buttonH + gap), buttonW, buttonH,
                    compact(label, 18), style(tool), () -> execute(tool));
        }

        int navY = y + panelH - 31;
        if (pages > 1) {
            button(x + 16, navY, 54, 20, "< PAGE", SOFT,
                    () -> reopen(Math.floorMod(actualPage - 1, pages)));
            button(x + 76, navY, 54, 20, "PAGE >", SOFT,
                    () -> reopen((actualPage + 1) % pages));
        }
        button(x + panelW - 86, navY, 70, 20, "BACK", SOFT, this::onClose);
    }

    private List<JsonObject> tools() {
        DAI_CreatorSchemaDefinition schema = DAI_CreatorSchemaRegistry.get(schemaId);
        if (schema == null) return List.of();
        List<JsonObject> output = new ArrayList<>();
        for (JsonElement element : schema.tools()) {
            if (element != null && element.isJsonObject()) output.add(element.getAsJsonObject());
        }
        return List.copyOf(output);
    }

    private void execute(JsonObject tool) {
        String op = DAI_CreatorSchemaDefinition.string(tool, "operation", "").trim();
        if (op.isBlank()) return;
        String key = DAI_CreatorSchemaDefinition.string(tool, "key", "");
        String value = DAI_CreatorSchemaDefinition.string(tool, "value", "");
        double x = number(tool, "x", 0.0D);
        double y = number(tool, "y", 0.0D);
        double z = number(tool, "z", 0.0D);
        send(op, key, value, x, y, z);
    }

    private DAI_ButtonStyle style(JsonObject tool) {
        String style = DAI_CreatorSchemaDefinition.string(tool, "style", "soft")
                .trim().toLowerCase(Locale.ROOT);
        return switch (style) {
            case "action", "primary", "orange" -> ACTION;
            case "danger", "delete", "destructive" -> DANGER;
            default -> SOFT;
        };
    }

    private static double number(JsonObject object, String key, double fallback) {
        try { return object != null && object.has(key) ? object.get(key).getAsDouble() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private void send(String operation, String key, String value, double x, double y, double z) {
        DAI_ServerBridge.send(new DAI_CreatorActionPayload(operation, folder, id, key, value, x, y, z));
    }

    private void button(int x, int y, int w, int h, String text, DAI_ButtonStyle style, Runnable action) {
        addRenderableWidget(new DAI_StyledButton(x, y, w, h, Component.literal(text), ignored -> action.run(), style));
    }

    private void reopen(int newPage) {
        Minecraft.getInstance().gui.setScreen(new DAI_CreatorToolsScreen(parent, schemaId, folder, id, newPage));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int panelW = Math.min(510, Math.max(270, width - 40));
        int panelH = Math.min(310, Math.max(170, height - 70));
        int x = (width - panelW) / 2;
        int y = Math.max(28, (height - panelH) / 2);
        g.fill(x, y, x + panelW, y + panelH, 0xE30A0710);
        g.outline(x, y, panelW, panelH, 0xFFA855F7);
        g.fill(x + 2, y + 2, x + panelW - 2, y + 3, 0xAAFF8428);
        g.text(font, Component.literal("CREATOR // MODULE TOOLS"), x + 14, y + 10, 0xFFFF9B45);
        g.text(font, Component.literal("Defined by creator_schemas JSON"), x + 14, y + 23, 0xFFC58AEF);
        if (tools().isEmpty()) {
            g.centeredText(font, Component.literal("This schema defines no extra tools."),
                    x + panelW / 2, y + 70, 0xFFBFB0CC);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private static String compact(String text, int max) {
        String value = text == null ? "" : text.trim();
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 3)) + "...";
    }
}
