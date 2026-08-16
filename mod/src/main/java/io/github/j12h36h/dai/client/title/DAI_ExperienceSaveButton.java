package io.github.j12h36h.dai.client.title;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Two-line title-screen entry for one DAI experience save. */
public final class DAI_ExperienceSaveButton extends Button.Plain {

    private static final DateTimeFormatter LAST_PLAYED = DateTimeFormatter
            .ofPattern("MMM d · h:mm a")
            .withZone(ZoneId.systemDefault());

    private final DAI_TitleScreenDefinition.SaveBrowserDefinition style;
    private final DAI_ExperienceLauncher.ExperienceSave save;
    private final String prefix;

    public DAI_ExperienceSaveButton(
            int x,
            int y,
            int width,
            int height,
            DAI_TitleScreenDefinition.SaveBrowserDefinition style,
            DAI_ExperienceLauncher.ExperienceSave save,
            Button.OnPress onPress
    ) {
        super(
                x,
                y,
                width,
                height,
                Component.literal("Continue"),
                onPress,
                DEFAULT_NARRATION
        );
        this.style = style;
        this.save = save;
        this.prefix = style == null ? "Run" : style.entryPrefix();
    }

    @Override
    protected void extractContents(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        DAI_TitleScreenDefinition.SaveBrowserDefinition s = style;
        boolean hovered = isHoveredOrFocused();
        int background = hovered ? s.entryHover() : s.entryBackground();

        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), s.entryBorder());

        Minecraft minecraft = Minecraft.getInstance();
        int line = minecraft.font.lineHeight;
        int textX = getX() + 8;
        int top = getY() + Math.max(4, (getHeight() - line * 2 - 2) / 2);

        Component name = Component.literal(prefix + " #" + save.sequence());
        graphics.text(minecraft.font, name, textX, top, s.textColor());

        String played = save.modifiedTime() <= 0
                ? "Last played: unknown"
                : "Last played: " + LAST_PLAYED.format(Instant.ofEpochMilli(save.modifiedTime()));
        graphics.text(
                minecraft.font,
                Component.literal(played),
                textX,
                top + line + 2,
                s.mutedColor()
        );

        Component continueText = Component.literal("CONTINUE");
        int continueWidth = minecraft.font.width(continueText);
        graphics.text(
                minecraft.font,
                continueText,
                getX() + getWidth() - continueWidth - 8,
                top,
                hovered ? s.titleColor() : s.mutedColor()
        );
    }
}
