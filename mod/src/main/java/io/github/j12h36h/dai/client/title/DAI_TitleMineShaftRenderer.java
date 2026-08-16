package io.github.j12h36h.dai.client.title;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Vanilla-resource-only mine presentation used by JSON title screens.
 *
 * The renderer intentionally uses only Minecraft's own block textures and
 * simple GUI primitives, so an experience can have a strong mine identity
 * without forcing a companion resource pack.
 */
public final class DAI_TitleMineShaftRenderer {

    private static final Identifier DEEPSLATE = Identifier.fromNamespaceAndPath(
            "minecraft", "textures/block/deepslate_bricks.png"
    );
    private static final Identifier SPRUCE_LOG = Identifier.fromNamespaceAndPath(
            "minecraft", "textures/block/spruce_log.png"
    );
    private static final Identifier DARK_OAK = Identifier.fromNamespaceAndPath(
            "minecraft", "textures/block/dark_oak_planks.png"
    );
    private static final Identifier IRON = Identifier.fromNamespaceAndPath(
            "minecraft", "textures/block/iron_block.png"
    );

    private DAI_TitleMineShaftRenderer() {}

    public static void render(
            GuiGraphicsExtractor graphics,
            int width,
            int height,
            int topColor,
            int bottomColor
    ) {
        graphics.fillGradient(0, 0, width, height, topColor, bottomColor);

        // Rough rock face, darkened enough to keep UI text readable.
        for (int y = 0; y < height; y += 32) {
            for (int x = 0; x < width; x += 32) {
                blit(graphics, DEEPSLATE, x, y, 32, 32, 0xFF777777);
            }
        }
        graphics.fill(0, 0, width, height, 0xA7080706);

        int horizon = Math.max(92, height / 2 + 12);
        int tunnelLeft = Math.max(34, width / 2 - Math.min(210, width / 3));
        int tunnelRight = Math.min(width - 34, width / 2 + Math.min(210, width / 3));
        graphics.fill(tunnelLeft, 34, tunnelRight, height, 0x9A050504);

        // Massive timber framework: broad, industrial, and visibly load-bearing.
        int beam = Math.max(14, Math.min(24, width / 32));
        int leftPost = Math.max(18, width / 10);
        int rightPost = width - leftPost - beam;
        blit(graphics, SPRUCE_LOG, leftPost, 22, beam, height - 22, 0xFFFFFFFF);
        blit(graphics, SPRUCE_LOG, rightPost, 22, beam, height - 22, 0xFFFFFFFF);
        blit(graphics, SPRUCE_LOG, leftPost, 28, rightPost + beam - leftPost, beam, 0xFFFFFFFF);

        int innerLeft = Math.max(leftPost + beam + 30, width / 4);
        int innerRight = Math.min(rightPost - 30, width * 3 / 4);
        int innerBeam = Math.max(10, beam - 5);
        blit(graphics, SPRUCE_LOG, innerLeft, 52, innerBeam, Math.max(50, horizon - 52), 0xFFB9A286);
        blit(graphics, SPRUCE_LOG, innerRight - innerBeam, 52, innerBeam, Math.max(50, horizon - 52), 0xFFB9A286);
        blit(graphics, SPRUCE_LOG, innerLeft, 54, innerRight - innerLeft, innerBeam, 0xFFB9A286);

        // Timber floor/decking in the foreground.
        int floorY = Math.max(horizon, height - 92);
        for (int y = floorY; y < height; y += 24) {
            for (int x = 0; x < width; x += 24) {
                blit(graphics, DARK_OAK, x, y, 24, 24, 0xFF8B765E);
            }
        }
        graphics.fill(0, floorY, width, height, 0x4A000000);

        // Mine rails leading into the tunnel. Perspective is intentionally
        // stylized rather than a literal 3-D projection.
        int center = width / 2;
        int railWidth = Math.max(3, width / 220);
        drawRotatedRect(graphics, center - 46, floorY + 8, railWidth, height - floorY + 24, -0.18F, 0xFFC4BFB4);
        drawRotatedRect(graphics, center + 43, floorY + 8, railWidth, height - floorY + 24, 0.18F, 0xFFC4BFB4);
        for (int i = 0; i < 5; i++) {
            int y = floorY + 14 + i * Math.max(10, (height - floorY - 20) / 5);
            int half = 24 + i * 10;
            graphics.fill(center - half, y, center + half, y + 3, 0xFF5A3821);
        }

        // Iron braces and warm mine lamps. The lamps are GUI primitives so the
        // title remains safe even before a client level exists.
        blit(graphics, IRON, leftPost + beam, 48, 6, 54, 0xFF777777);
        blit(graphics, IRON, rightPost - 6, 48, 6, 54, 0xFF777777);
        drawLamp(graphics, innerLeft + 34, 74);
        drawLamp(graphics, innerRight - 34, 74);

        // Vignette keeps the central interface readable and gives the mouth of
        // the mine more visual depth.
        graphics.fill(0, 0, width, 18, 0x65000000);
        graphics.fill(0, height - 12, width, height, 0x70000000);
        graphics.fill(0, 0, Math.max(8, width / 20), height, 0x72000000);
        graphics.fill(width - Math.max(8, width / 20), 0, width, height, 0x72000000);
    }

    private static void drawLamp(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 20, x + 1, y, 0xFF5F5B54);
        graphics.fill(x - 13, y - 9, x + 13, y + 13, 0x18FFC346);
        graphics.fill(x - 8, y - 6, x + 8, y + 10, 0x32FFC346);
        graphics.fill(x - 4, y - 3, x + 4, y + 7, 0xFFFFC34A);
        graphics.outline(x - 5, y - 4, 10, 12, 0xFF6B4B20);
    }

    private static void drawRotatedRect(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            float rotation,
            int color
    ) {
        float centerX = x + width / 2.0F;
        float centerY = y + height / 2.0F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate(rotation);
        graphics.pose().translate(-centerX, -centerY);
        graphics.fill(x, y, x + width, y + height, color);
        graphics.pose().popMatrix();
    }

    private static void blit(
            GuiGraphicsExtractor graphics,
            Identifier texture,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x,
                y,
                0.0F,
                0.0F,
                width,
                height,
                16,
                16,
                color
        );
    }
}
