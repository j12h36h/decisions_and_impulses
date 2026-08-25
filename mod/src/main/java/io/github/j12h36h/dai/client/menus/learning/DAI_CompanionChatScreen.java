package io.github.j12h36h.dai.client.menus.learning;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.j12h36h.dai.client.learning.DAI_LearningRuntime;
import io.github.j12h36h.dai.client.menus.DAI_StyledButton;
import io.github.j12h36h.dai.client.menus.system.DAI_ButtonStyle;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Dedicated local-only chat for the active DAI learning agent. */
public final class DAI_CompanionChatScreen extends Screen {
    private static final DAI_ButtonStyle CYAN = new DAI_ButtonStyle(
            "#B0061418", "#D20A2830", "#E50C3845", "#ECFFFF", "#38E8FF");
    private static final DAI_ButtonStyle GREEN = new DAI_ButtonStyle(
            "#A908180F", "#D20D2F1C", "#E514472A", "#F1FFF6", "#55E58A");
    private static final DAI_ButtonStyle RED = new DAI_ButtonStyle(
            "#A91A080C", "#D2341018", "#E54B1824", "#FFF1F3", "#F45D72");

    private static final Identifier SAPPHIRE_SPRITE_SHEET =
            Identifier.fromNamespaceAndPath(DAI_Core.MODID, "textures/gui/sapphire/sprite_sheet.png");
    private static final int SHEET_COLUMNS = 4;
    private static final int SHEET_ROWS = 3;
    private static final int SHEET_FRAME_SIZE = 362;

    private EditBox input;
    private Button autoButton;
    private String status = "Local Sapphire channel // normal Minecraft chat is untouched";

    public DAI_CompanionChatScreen() {
        super(Component.literal("Sapphire Chat"));
    }

    @Override
    protected void init() {
        super.init();
        DAI_LearningRuntime.greeting();

        int panelW = Math.min(920, Math.max(360, width - 28));
        int x = (width - panelW) / 2;
        int bottom = height - 16;
        int inputY = bottom - 28;

        input = new EditBox(font, x + 10, inputY, panelW - 94, 20, Component.literal("Message Sapphire"));
        input.setMaxLength(2048);
        addRenderableWidget(input);
        setInitialFocus(input);

        button(x + panelW - 78, inputY, 68, 20, "SEND", CYAN, this::send);

        int controlsY = inputY - 26;
        int w = Math.max(64, (panelW - 32) / 5);
        int bx = x + 10;
        button(bx, controlsY, w, 20, "+ REPLY", GREEN, () -> feedbackDialogue(1.0D)); bx += w + 3;
        button(bx, controlsY, w, 20, "- REPLY", RED, () -> feedbackDialogue(-1.0D)); bx += w + 3;
        button(bx, controlsY, w, 20, "+ ACTION", GREEN, () -> feedbackAction(1.0D)); bx += w + 3;
        button(bx, controlsY, w, 20, "- ACTION", RED, () -> feedbackAction(-1.0D)); bx += w + 3;
        autoButton = button(bx, controlsY, Math.max(64, x + panelW - 10 - bx), 20,
                autoText(), CYAN, this::toggleAutonomy);
    }

    private void send() {
        if (input == null) return;
        String value = input.getValue().trim();
        if (value.isEmpty()) return;
        String result = DAI_LearningRuntime.sendDialogue(value);
        if (value.startsWith("/")) status = result;
        else status = "Sapphire is listening.";
        input.setValue("");
        setInitialFocus(input);
    }

    private void feedbackDialogue(double reward) {
        DAI_LearningRuntime.rewardDialogue(reward);
        status = reward > 0 ? "Rewarded Sapphire's last reply." : "Penalized Sapphire's last reply.";
    }

    private void feedbackAction(double reward) {
        DAI_LearningRuntime.rewardLastAction(reward);
        status = reward > 0 ? "Rewarded Sapphire's last autonomous action." : "Penalized Sapphire's last autonomous action.";
    }

    private void toggleAutonomy() {
        DAI_LearningRuntime.setAutonomy(!DAI_LearningRuntime.autonomy());
        if (autoButton != null) autoButton.setMessage(Component.literal(autoText()));
        status = "Autonomy " + (DAI_LearningRuntime.autonomy() ? "enabled" : "disabled") + ".";
    }

    private String autoText() { return "AUTO " + (DAI_LearningRuntime.autonomy() ? "ON" : "OFF"); }

    private Button button(int x, int y, int w, int h, String text, DAI_ButtonStyle style, Runnable action) {
        Button button = new DAI_StyledButton(x, y, Math.max(24, w), h,
                Component.literal(text), ignored -> action.run(), style);
        addRenderableWidget(button);
        return button;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = InputConstants.getKey(event).getValue();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int panelW = Math.min(920, Math.max(360, width - 28));
        int x = (width - panelW) / 2;
        int top = 16;
        int bottom = height - 16;
        int inputY = bottom - 28;
        int controlsY = inputY - 26;

        int spriteSize = Math.max(86, Math.min(112, (height - top - 120) / 3));
        int spriteX = x + panelW - spriteSize - 12;
        int spriteY = top + 42;

        int historyLeft = x + 10;
        int historyRight = spriteX - 12;
        int historyWidth = Math.max(120, historyRight - historyLeft);
        int lineHeight = 11;
        int historyTop = top + 41;
        int historyBottom = controlsY - 20;
        int maxLines = Math.max(1, (historyBottom - historyTop) / lineHeight);

        graphics.fillGradient(0, 0, width, height, 0xD0060D10, 0xCB071317);
        graphics.fill(x, top, x + panelW, bottom, 0xDF071115);
        graphics.outline(x, top, panelW, bottom - top, 0xFF28DCEE);
        graphics.fill(x + 2, top + 2, x + panelW - 2, top + 4, 0xCC38E8FF);

        graphics.text(font, Component.literal("D.A.I. // Sapphire"), x + 10, top + 9, 0xFFBFFAFF);
        graphics.text(font, Component.literal("SAPPHIRE CHAT"), x + 10, top + 21, 0xFF55E58A);

        renderSprite(graphics, spriteX, spriteY, spriteSize);

        List<WrappedLine> statusLines = wrapColored(status, panelW - 20, 0xFF9AB9BE);
        int statusY = controlsY - 24;
        int statusStart = Math.max(0, statusLines.size() - 2);
        for (int i = statusStart; i < statusLines.size(); i++) {
            WrappedLine line = statusLines.get(i);
            graphics.text(font, Component.literal(line.text()), x + 10, statusY, line.color());
            statusY += lineHeight;
        }

        List<WrappedLine> wrappedHistory = new ArrayList<>();
        for (String line : DAI_LearningRuntime.history()) {
            int color = line.startsWith("You:") ? 0xFFEAFDFF
                    : line.startsWith("[DAI]") ? 0xFFFFD166
                    : 0xFF78F0A4;
            wrappedHistory.addAll(wrapColored(line, historyWidth, color));
        }

        int start = Math.max(0, wrappedHistory.size() - maxLines);
        int y = historyTop;
        for (int i = start; i < wrappedHistory.size(); i++) {
            WrappedLine line = wrappedHistory.get(i);
            graphics.text(font, Component.literal(line.text()), historyLeft, y, line.color());
            y += lineHeight;
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSprite(GuiGraphicsExtractor graphics, int x, int y, int size) {
        int frame = Math.max(0, Math.min(11, DAI_LearningRuntime.spriteFrame()));
        int column = frame % SHEET_COLUMNS;
        int row = frame / SHEET_COLUMNS;
        graphics.fill(x - 8, y - 8, x + size + 8, y + size + 8, 0x28000000);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                SAPPHIRE_SPRITE_SHEET,
                x,
                y,
                column * (float) SHEET_FRAME_SIZE,
                row * (float) SHEET_FRAME_SIZE,
                size,
                size,
                SHEET_FRAME_SIZE,
                SHEET_FRAME_SIZE,
                SHEET_FRAME_SIZE * SHEET_COLUMNS,
                SHEET_FRAME_SIZE * SHEET_ROWS,
                0xFFFFFFFF
        );
    }

    private List<WrappedLine> wrapColored(String text, int maxWidth, int color) {
        List<WrappedLine> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        String[] paragraphs = text.split("\\n");
        for (String paragraph : paragraphs) {
            String remaining = paragraph.trim();
            if (remaining.isEmpty()) {
                lines.add(new WrappedLine("", color));
                continue;
            }

            while (!remaining.isEmpty()) {
                int cut = maxFittingIndex(remaining, maxWidth);
                if (cut >= remaining.length()) {
                    lines.add(new WrappedLine(remaining, color));
                    remaining = "";
                    continue;
                }

                int split = remaining.lastIndexOf(' ', cut);
                if (split <= 0) split = cut;
                String line = remaining.substring(0, split).trim();
                if (line.isEmpty()) line = remaining.substring(0, Math.min(cut, remaining.length())).trim();
                lines.add(new WrappedLine(line, color));
                remaining = remaining.substring(Math.min(split + 1, remaining.length())).trim();
            }
        }
        return lines;
    }

    private int maxFittingIndex(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text.length();
        int low = 1;
        int high = text.length();
        int best = 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            String sub = text.substring(0, mid);
            if (font.width(sub) <= maxWidth) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return Math.max(1, best);
    }

    private record WrappedLine(String text, int color) {}

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean isInGameUi() { return true; }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(null);
    }
}
