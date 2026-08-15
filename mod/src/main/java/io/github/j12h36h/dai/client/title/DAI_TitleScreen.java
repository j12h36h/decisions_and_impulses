package io.github.j12h36h.dai.client.title;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/** JSON-driven replacement for Minecraft's vanilla title screen. */
public final class DAI_TitleScreen extends Screen {

    private static final int COMPACT_BOTTOM_MARGIN = 8;
    private static final int COMPACT_TEXT_GAP = 2;
    private static final int COMPACT_BUTTON_GAP = 6;
    private static final int MIN_COMPACT_BUTTON_GAP = 2;
    private static final int MIN_BUTTON_HEIGHT = 18;

    /**
     * Prevents the click that closed/disconnected the previous screen from
     * activating a button that occupies the same coordinates on this screen.
     * The guard is intentionally short enough to be invisible during normal
     * title use but long enough to cover a mouse press/release transition.
     */
    private static final long TRANSITION_CLICK_GUARD_NANOS = 650_000_000L;

    private final DAI_TitleScreenDefinition definition;
    private long acceptClicksAfterNanos;

    public DAI_TitleScreen(DAI_TitleScreenDefinition definition) {
        super(Component.literal("Decisions & Impulses"));
        this.definition = definition == null
                ? DAI_TitleScreenDefinition.fallback("decisions_and_impulses:fallback")
                : definition;
    }

    @Override
    protected void init() {
        super.init();

        acceptClicksAfterNanos = System.nanoTime() + TRANSITION_CLICK_GUARD_NANOS;

        CompactLayout compact = buildCompactLayout();
        int centeredIndex = 0;

        for (DAI_TitleScreenDefinition.ButtonDefinition button : definition.buttons()) {
            if (compact != null && isCentered(button)) {
                DAI_TitleScreenDefinition.ButtonDefinition fitted = withHeight(
                        button,
                        compact.buttonHeight()
                );

                int x = resolveX(fitted);
                int y = compact.firstY()
                        + centeredIndex * (compact.buttonHeight() + compact.gap());

                centeredIndex++;
                addTitleButton(x, y, fitted);
                continue;
            }

            addTitleButton(
                    resolveX(button),
                    resolveY(button),
                    button
            );
        }

        DAI_Core.debug(
                "<DAI>: Initialized JSON title screen '{}' with {} button(s), compactLayout={}.",
                definition.id(),
                definition.buttons().size(),
                compact != null
        );
    }

    private void addTitleButton(
            int x,
            int y,
            DAI_TitleScreenDefinition.ButtonDefinition button
    ) {
        DAI_TitleButton widget = new DAI_TitleButton(
                x,
                y,
                button,
                pressed -> {
                    if (System.nanoTime() < acceptClicksAfterNanos) {
                        DAI_Core.debug(
                                "<DAI>: Ignored title-screen button '{}' during transition click guard.",
                                button.id()
                        );
                        return;
                    }
                    DAI_TitleActionDispatcher.run(this, button);
                }
        );

        addRenderableWidget(widget);
    }

    @Override
    public void extractRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.fillGradient(
                0,
                0,
                width,
                height,
                definition.backgroundTop(),
                definition.backgroundBottom()
        );

        graphics.centeredText(
                font,
                Component.literal(definition.title()),
                width / 2,
                titleY(),
                definition.titleColor()
        );

        graphics.centeredText(
                font,
                Component.literal(definition.subtitle()),
                width / 2,
                subtitleY(),
                definition.subtitleColor()
        );

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // This screen draws its own JSON-configured background.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int titleY() {
        return Math.max(20, height / 2 - 132);
    }

    private int subtitleY() {
        return Math.max(35, height / 2 - 112);
    }

    private int resolveX(DAI_TitleScreenDefinition.ButtonDefinition button) {
        return switch (button.anchor()) {
            case "top_left", "left" -> button.x();
            case "top_right", "right" -> width - button.width() - button.x();
            default -> width / 2 - button.width() / 2 + button.x();
        };
    }

    private int resolveY(DAI_TitleScreenDefinition.ButtonDefinition button) {
        return switch (button.anchor()) {
            case "top_left", "top_right", "top" -> button.y();
            case "bottom_left", "bottom_right", "bottom" -> height - button.height() - button.y();
            default -> height / 2 + button.y();
        };
    }

    /**
     * Builds a compact layout only when the JSON-defined centered stack would
     * run off the bottom of the current GUI-scaled screen.
     *
     * Instead of shifting the entire stack upward (which can collide with the
     * subtitle), the compact layout keeps the first button below the subtitle,
     * slightly reduces button height, and tightens the vertical step just
     * enough for the final button to retain a bottom margin.
     */
    private CompactLayout buildCompactLayout() {
        List<DAI_TitleScreenDefinition.ButtonDefinition> centered = new ArrayList<>();

        for (DAI_TitleScreenDefinition.ButtonDefinition button : definition.buttons()) {
            if (isCentered(button)) {
                centered.add(button);
            }
        }

        if (centered.isEmpty()) {
            return null;
        }

        DAI_TitleScreenDefinition.ButtonDefinition first = centered.get(0);
        DAI_TitleScreenDefinition.ButtonDefinition last = centered.get(centered.size() - 1);

        int rawLastBottom = height / 2 + last.y() + last.height();
        int bottomLimit = height - COMPACT_BOTTOM_MARGIN;

        if (rawLastBottom <= bottomLimit) {
            return null;
        }

        int rawFirstY = height / 2 + first.y();
        int minimumFirstY = subtitleY() + font.lineHeight + COMPACT_TEXT_GAP;
        int firstY = Math.max(rawFirstY, minimumFirstY);

        int count = centered.size();
        int available = bottomLimit - firstY;
        if (available <= 0) {
            return null;
        }

        int originalMinHeight = Integer.MAX_VALUE;
        for (DAI_TitleScreenDefinition.ButtonDefinition button : centered) {
            originalMinHeight = Math.min(originalMinHeight, button.height());
        }

        int buttonHeight = Math.min(
                originalMinHeight,
                (available - COMPACT_BUTTON_GAP * Math.max(0, count - 1)) / count
        );
        buttonHeight = Math.max(MIN_BUTTON_HEIGHT, buttonHeight);

        int gap;
        if (count <= 1) {
            gap = 0;
        } else {
            gap = Math.min(
                    COMPACT_BUTTON_GAP,
                    (available - buttonHeight * count) / (count - 1)
            );
            gap = Math.max(MIN_COMPACT_BUTTON_GAP, gap);
        }

        int totalHeight = buttonHeight * count + gap * Math.max(0, count - 1);

        // Extremely short layouts can still exceed the available region even
        // at the minimum supported button height. In that case, pin the first
        // button as high as safely possible and keep the minimum gap.
        if (totalHeight > available) {
            firstY = Math.max(
                    minimumFirstY,
                    bottomLimit - totalHeight
            );
        }

        return new CompactLayout(
                firstY,
                buttonHeight,
                gap
        );
    }

    private static boolean isCentered(
            DAI_TitleScreenDefinition.ButtonDefinition button
    ) {
        return switch (button.anchor()) {
            case "top_left", "top_right", "top",
                 "bottom_left", "bottom_right", "bottom" -> false;
            default -> true;
        };
    }

    private static DAI_TitleScreenDefinition.ButtonDefinition withHeight(
            DAI_TitleScreenDefinition.ButtonDefinition button,
            int height
    ) {
        return new DAI_TitleScreenDefinition.ButtonDefinition(
                button.id(),
                button.label(),
                button.action(),
                button.url(),
                button.experience(),
                button.anchor(),
                button.x(),
                button.y(),
                button.width(),
                height,
                button.icon(),
                button.style(),
                button.hoverAnimation()
        );
    }

    private record CompactLayout(
            int firstY,
            int buttonHeight,
            int gap
    ) {
    }
}
