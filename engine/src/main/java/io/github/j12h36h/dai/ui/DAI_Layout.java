package io.github.j12h36h.dai.ui;

import java.awt.*;
public class DAI_Layout {

    public static final int DEFAULT_MARGIN = 8;
    public static final int HUD_BOTTOM_MARGIN = 2;
    public static final int HOTBAR_HALF_WIDTH = 91;
    public static final int DEFAULT_SPACING = 2;

    public record Layout(
            DAI_Position position,
            int x,
            int y,
            int width,
            int height
    ) {}

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

        int width = desiredWidth;
        int height = desiredHeight;

        int hotbarLeft = screenWidth / 2 - HOTBAR_HALF_WIDTH;
        int hotbarRight = screenWidth / 2 + HOTBAR_HALF_WIDTH;
        int hotbarY = screenHeight - height - HUD_BOTTOM_MARGIN;

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

                int available = hotbarLeft - leftMargin;

                width = Math.min(width, available);
                width = Math.max(width, 80);

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

                int available = screenWidth - hotbarRight - rightMargin;

                width = Math.min(width, available);
                width = Math.max(width, 80);

                x = hotbarRight + rightMargin - 3;
                y = topMargin;
            }

            case MID_RIGHT -> {
                x = screenWidth - width - rightMargin;
                y = (screenHeight - height) / 2;
            }

            case BOT_RIGHT -> {

                int available = screenWidth - hotbarRight - rightMargin;

                width = Math.min(width, available);
                width = Math.max(width, 80);

                x = hotbarRight + rightMargin - 3;
                y = hotbarY;
            }

            default -> {
                x = leftMargin;
                y = topMargin;
            }
        }

        return new Layout(position, x, y, width, height);
    }

    public static Layout getSubLayout(Layout parent, int index) {

        int spacing = parent.height() + DEFAULT_SPACING;

        int x = parent.x();
        int y = parent.y();

        switch (parent.position()) {

            case TOP_LEFT,
                 TOP_CENTER,
                 TOP_RIGHT -> {
                y += spacing * (index + 1);
            }

            case BOT_LEFT,
                 BOT_CENTER,
                 BOT_RIGHT -> {
                y -= spacing * (index + 1);
            }

            case MID_LEFT -> {
                x += parent.width() + DEFAULT_SPACING;
            }

            case MID_RIGHT -> {
                x -= parent.width() + DEFAULT_SPACING;
            }
        }

        return new Layout(
                parent.position(),
                x,
                y,
                parent.width(),
                parent.height()
        );
    }
}