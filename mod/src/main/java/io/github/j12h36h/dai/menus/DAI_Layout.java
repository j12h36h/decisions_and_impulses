package io.github.j12h36h.dai.menus;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;

public final class DAI_Layout {

    public static final int DEFAULT_MARGIN = 8;
    public static final int HUD_BOTTOM_MARGIN = 2;
    public static final int HOTBAR_HALF_WIDTH = 91;
    public static final int DEFAULT_SPACING = 2;
    public static final int MINIMUM_WIDTH = 80;

    private DAI_Layout() {
        // Utility class.
    }
    public record Layout(
            DAI_Position position,
            int x,
            int y,
            int width,
            int height
    ) {
    }

    public static Layout getLayout(
            DAI_Position position,
            int screenWidth,
            int screenHeight,
            int desiredWidth,
            int desiredHeight,
            int leftMargin,
            int topMargin,
            int rightMargin,
            int bottomMargin
    ) {

        int width = Math.max(0, desiredWidth);
        int height = Math.max(0, desiredHeight);

        int hotbarLeft =
                screenWidth / 2 - HOTBAR_HALF_WIDTH;

        int hotbarRight =
                screenWidth / 2 + HOTBAR_HALF_WIDTH;

        int hotbarY =
                screenHeight - height - HUD_BOTTOM_MARGIN;

        int x;
        int y;

        switch (position) {

            case TOP_LEFT -> {
                x = leftMargin;
                y = topMargin;
            }

            case MID_LEFT -> {
                x = leftMargin;
                y = (screenHeight - height) / 2;
            }

            case BOT_LEFT -> {

                int availableWidth =
                        Math.max(0, hotbarLeft - leftMargin);

                width = constrainWidth(
                        width,
                        availableWidth
                );

                x = hotbarLeft - width - leftMargin + 3;
                y = hotbarY;
            }

            case TOP_CENTER -> {
                x = (screenWidth - width) / 2;
                y = topMargin;
            }

            case BOT_CENTER -> {
                x = (screenWidth - width) / 2;
                y = hotbarY;
            }

            case TOP_RIGHT -> {

                int availableWidth =
                        Math.max(
                                0,
                                screenWidth
                                        - hotbarRight
                                        - rightMargin
                        );

                width = constrainWidth(
                        width,
                        availableWidth
                );

                x = hotbarRight + rightMargin - 3;
                y = topMargin;
            }

            case MID_RIGHT -> {
                x = screenWidth - width - rightMargin;
                y = (screenHeight - height) / 2;
            }

            case BOT_RIGHT -> {

                int availableWidth =
                        Math.max(
                                0,
                                screenWidth
                                        - hotbarRight
                                        - rightMargin
                        );

                width = constrainWidth(
                        width,
                        availableWidth
                );

                x = hotbarRight + rightMargin - 3;
                y = hotbarY;
            }

            default -> {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Unsupported layout position '{}'. Using TOP_LEFT.",
                        position
                );

                x = leftMargin;
                y = topMargin;
            }
        }

        Layout layout = new Layout(
                position,
                x,
                y,
                width,
                height
        );

        if (
                x < 0
                        || y < 0
                        || x + width > screenWidth
                        || y + height > screenHeight
        ) {

            DAI_Core.debug(
                    "<DAI>: Layout extends outside screen bounds: position={}, x={}, y={}, width={}, height={}, screenWidth={}, screenHeight={}.",
                    position,
                    x,
                    y,
                    width,
                    height,
                    screenWidth,
                    screenHeight
            );
        }

        return layout;
    }

    public static Layout getSubLayout(
            Layout parent,
            int index
    ) {

        if (parent == null) {

            throw new IllegalArgumentException(
                    "Parent layout cannot be null."
            );
        }

        if (index < 0) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid sub-layout index {}.",
                    index
            );

            index = 0;
        }

        int spacing =
                parent.height() + DEFAULT_SPACING;

        int x = parent.x();
        int y = parent.y();

        switch (parent.position()) {

            case TOP_LEFT,
                 TOP_CENTER,
                 TOP_RIGHT ->
                    y += spacing * (index + 1);

            case BOT_LEFT,
                 BOT_CENTER,
                 BOT_RIGHT ->
                    y -= spacing * (index + 1);

            case MID_LEFT ->
                    x += spacingHorizontal(parent, index);

            case MID_RIGHT ->
                    x -= spacingHorizontal(parent, index);
        }

        return new Layout(
                parent.position(),
                x,
                y,
                parent.width(),
                parent.height()
        );
    }

    private static int constrainWidth(
            int desiredWidth,
            int availableWidth
    ) {

        if (availableWidth <= 0) {
            return 0;
        }

        int minimumWidth =
                Math.min(MINIMUM_WIDTH, availableWidth);

        return Math.clamp(
                desiredWidth,
                minimumWidth,
                availableWidth
        );
    }

    private static int spacingHorizontal(
            Layout parent,
            int index
    ) {

        return (
                parent.width()
                        + DEFAULT_SPACING
        ) * (index + 1);
    }
}