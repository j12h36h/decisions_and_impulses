package io.github.j12h36h.dai.client.navigation;

import io.github.j12h36h.dai.client.logics.input.DAI_KeybindDefinition;
import io.github.j12h36h.dai.client.logics.input.DAI_KeybindRegistry;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.client.title.DAI_TitleActionDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * DAI keyboard-map presentation inspired by MapleStory's spatial key layout.
 * The engine owns the map; Minecraft's keybinding screen remains the persisted
 * binding editor until DAI's drag/drop rebinder is introduced.
 */
public final class DAI_ControlsMapScreen extends Screen {
    private final Screen parent;
    private List<Map.Entry<String, DAI_KeybindDefinition>> definitions = List.of();

    public DAI_ControlsMapScreen(Screen parent) {
        super(Component.literal("Controls"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        definitions = new ArrayList<>(DAI_KeybindRegistry.snapshot().entrySet());
        definitions = definitions.stream().sorted(Comparator.comparing(Map.Entry::getKey)).toList();
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        addRenderableWidget(new DAI_UniverseButton(
                width / 2 - 110, height - 54, 220, 20,
                Component.literal("EDIT / REBIND CONTROLS"),
                b -> DAI_TitleActionDispatcher.openReflective(this, "net.minecraft.client.gui.screens.options.controls.ControlsScreen"),
                DAI_UniverseButton.Shape.CHIP, profile.primary()
        ));
        addRenderableWidget(new DAI_UniverseButton(
                width / 2 - 48, height - 30, 96, 20,
                Component.literal("BACK"), b -> onClose(),
                DAI_UniverseButton.Shape.CHIP, profile.secondary()
        ));
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        graphics.centeredText(font, Component.literal("CONTROLS"), width / 2, 14, profile.text());
        graphics.centeredText(font, Component.literal("DAI KEY MAP · spatial overview"), width / 2, 28, 0xFF9FB2BE);

        int keyboardW = Math.min(540, width - 28);
        int keyW = Math.max(18, keyboardW / 16);
        int keyH = 20;
        int x0 = width / 2 - (keyW * 10 + 9 * 3) / 2;
        int y0 = 54;
        String[][] rows = {
                {"1","2","3","4","5","6","7","8","9","0"},
                {"Q","W","E","R","T","Y","U","I","O","P"},
                {"A","S","D","F","G","H","J","K","L"},
                {"Z","X","C","V","B","N","M"}
        };
        for (int r = 0; r < rows.length; r++) {
            int rowOffset = r * Math.max(0, keyW / 3);
            for (int c = 0; c < rows[r].length; c++) {
                int x = x0 + rowOffset + c * (keyW + 3);
                int y = y0 + r * (keyH + 4);
                graphics.fill(x, y, x + keyW, y + keyH, 0xAA101824);
                graphics.centeredText(font, Component.literal(rows[r][c]), x + keyW / 2, y + 6, 0xFFEAF4FF);
            }
        }

        int listY = y0 + 4 * (keyH + 4) + 10;
        int shown = Math.min(definitions.size(), Math.max(2, (height - listY - 88) / 12));
        for (int i = 0; i < shown; i++) {
            Map.Entry<String, DAI_KeybindDefinition> entry = definitions.get(i);
            DAI_KeybindDefinition definition = entry.getValue();
            String name = definition.displayName().isBlank() ? entry.getKey() : definition.displayName();
            String key = definition.rawKeyId().toUpperCase(java.util.Locale.ROOT);
            graphics.text(font, Component.literal(key), width / 2 - 150, listY + i * 12, profile.primary());
            graphics.text(font, Component.literal(name), width / 2 - 105, listY + i * 12, 0xFFC6D5DF);
        }
        if (definitions.size() > shown) {
            graphics.centeredText(font, Component.literal("+ " + (definitions.size() - shown) + " more DAI bindings"), width / 2, listY + shown * 12 + 2, 0xFF8FA6B5);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }
}
