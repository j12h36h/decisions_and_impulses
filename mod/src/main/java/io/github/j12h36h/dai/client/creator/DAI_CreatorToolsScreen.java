package io.github.j12h36h.dai.client.creator;

import io.github.j12h36h.dai.client.menus.DAI_StyledButton;
import io.github.j12h36h.dai.client.menus.system.DAI_ButtonStyle;
import io.github.j12h36h.dai.client.network.DAI_ServerBridge;
import io.github.j12h36h.dai.network.DAI_CreatorActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/** Secondary tool drawer keeps transforms/destructive operations off the main Creator canvas. */
public final class DAI_CreatorToolsScreen extends Screen {
    private static final DAI_ButtonStyle ORANGE = new DAI_ButtonStyle("#A8140B09", "#D936170B", "#EA5A2710", "#FFF4EA", "#FF8A2A");
    private static final DAI_ButtonStyle PURPLE = new DAI_ButtonStyle("#A8100918", "#D827123B", "#E6421C60", "#FAF0FF", "#A855F7");
    private static final DAI_ButtonStyle DANGER = new DAI_ButtonStyle("#A6231018", "#D7471728", "#E45C1F35", "#FFF0F5", "#EF5A82");
    private final Screen parent;
    private final String kind;
    private final String id;

    public DAI_CreatorToolsScreen(Screen parent, String kind, String id) {
        super(Component.literal("DAI Creator Tools"));
        this.parent = parent;
        this.kind = kind;
        this.id = id;
    }

    @Override
    protected void init() {
        super.init();
        int panelW = Math.min(430, Math.max(250, width - 40));
        int x = (width - panelW) / 2;
        int y = Math.max(28, height / 2 - 112);
        int inner = panelW - 32;
        int gap = 6;
        int col = Math.max(56, (inner - gap * 2) / 3);
        int sx = x + 16;
        int sy = y + 38;

        row(sx, sy, col, gap,
                new Tool("-X", PURPLE, () -> nudge(-1, 0, 0)),
                new Tool("+X", PURPLE, () -> nudge(1, 0, 0)),
                new Tool("-Y", PURPLE, () -> nudge(0, -1, 0)));
        sy += 27;
        row(sx, sy, col, gap,
                new Tool("+Y", PURPLE, () -> nudge(0, 1, 0)),
                new Tool("-Z", PURPLE, () -> nudge(0, 0, -1)),
                new Tool("+Z", PURPLE, () -> nudge(0, 0, 1)));
        sy += 27;
        row(sx, sy, col, gap,
                new Tool("MOVE HERE", ORANGE, this::moveHere),
                new Tool("SIZE -", PURPLE, () -> send("resize", "", "", -1, -1, -1)),
                new Tool("SIZE +", PURPLE, () -> send("resize", "", "", 1, 1, 1)));
        sy += 27;
        row(sx, sy, col, gap,
                new Tool("RUN EVENT", ORANGE, () -> send("run_event", "test", "", 0, 0, 0)),
                new Tool("UNDO", PURPLE, () -> send("undo", "", "", 0, 0, 0)),
                new Tool("REDO", PURPLE, () -> send("redo", "", "", 0, 0, 0)));
        sy += 27;
        row(sx, sy, col, gap,
                new Tool("DUPLICATE", ORANGE, () -> send("duplicate", "", "", 0, 0, 0)),
                new Tool("DELETE", DANGER, () -> send("delete", "", "", 0, 0, 0)),
                new Tool("BACK", PURPLE, this::onClose));
    }

    private void row(int x, int y, int w, int gap, Tool a, Tool b, Tool c) {
        button(x, y, w, 20, a.label(), a.style(), a.action());
        button(x + w + gap, y, w, 20, b.label(), b.style(), b.action());
        button(x + (w + gap) * 2, y, w, 20, c.label(), c.style(), c.action());
    }

    private void nudge(double x, double y, double z) {
        send("nudge", "", "", x, y, z);
    }

    private void moveHere() {
        Vec3 p = Minecraft.getInstance().player == null ? Vec3.ZERO : Minecraft.getInstance().player.position();
        send("move_here", "", "", p.x, p.y, p.z);
    }

    private void send(String op, String key, String value, double x, double y, double z) {
        DAI_ServerBridge.send(new DAI_CreatorActionPayload(op, kind, id, key, value, x, y, z));
    }

    private void button(int x, int y, int w, int h, String text, DAI_ButtonStyle style, Runnable action) {
        addRenderableWidget(new DAI_StyledButton(x, y, w, h, Component.literal(text), ignored -> action.run(), style));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int panelW = Math.min(430, Math.max(250, width - 40));
        int x = (width - panelW) / 2;
        int y = Math.max(28, height / 2 - 112);
        int h = 188;
        g.fill(x, y, x + panelW, y + h, 0xE30A0710);
        g.outline(x, y, panelW, h, 0xFFA855F7);
        g.fill(x + 2, y + 2, x + panelW - 2, y + 3, 0xAAFF8428);
        g.text(font, Component.literal("CREATOR // TOOL DRAWER"), x + 14, y + 10, 0xFFFF9B45);
        g.text(font, Component.literal("TRANSFORM / HISTORY / EVENT / LIFECYCLE"), x + 14, y + 22, 0xFFC58AEF);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private record Tool(String label, DAI_ButtonStyle style, Runnable action) {}
}
