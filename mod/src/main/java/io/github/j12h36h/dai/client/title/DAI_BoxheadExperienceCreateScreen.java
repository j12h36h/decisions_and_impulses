package io.github.j12h36h.dai.client.title;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/**
 * Focused creator used by the Boxhead MAIN experience. Minecraft still owns
 * actual save creation; this screen only exposes the three game-facing choices.
 */
public final class DAI_BoxheadExperienceCreateScreen extends Screen {
    private static final String[] WORLD_LABELS = {"The Space", "Box Town", "Sky Scraper", "Village Box", "Boxlaska"};
    private static final String[] WORLD_IDS = {"the_space", "box_town", "sky_scraper", "village_box", "boxlaska"};
    private static final String[] MODE_LABELS = {"Last Breath", "Infinite Dead", "Casual Paradise"};
    private static final String[] MODE_IDS = {"last_breath", "infinite_dead", "casual_paradise"};

    private final Screen parent;
    private final String experienceId;
    private EditBox nameBox;
    private Button worldButton;
    private Button modeButton;
    private int worldIndex = 1;
    private int modeIndex = 1;

    public DAI_BoxheadExperienceCreateScreen(Screen parent, String experienceId) {
        super(Component.literal("Create Boxhead World"));
        this.parent = parent;
        this.experienceId = experienceId == null || experienceId.isBlank() ? "boxhead:boxhead" : experienceId;
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int top = Math.max(42, height / 2 - 96);

        nameBox = new EditBox(font, cx - 130, top + 32, 260, 24, Component.literal("World Name"));
        nameBox.setMaxLength(64);
        nameBox.setValue("Boxhead Survival");
        addRenderableWidget(nameBox);

        worldButton = Button.builder(Component.empty(), ignored -> {
            worldIndex = (worldIndex + 1) % WORLD_LABELS.length;
            updateLabels();
        }).bounds(cx - 130, top + 76, 260, 26).build();
        addRenderableWidget(worldButton);

        modeButton = Button.builder(Component.empty(), ignored -> {
            modeIndex = (modeIndex + 1) % MODE_LABELS.length;
            updateLabels();
        }).bounds(cx - 130, top + 118, 260, 26).build();
        addRenderableWidget(modeButton);

        addRenderableWidget(Button.builder(Component.literal("CREATE WORLD"), ignored -> create())
                .bounds(cx - 130, top + 165, 260, 28).build());
        addRenderableWidget(Button.builder(Component.literal("BACK"), ignored -> Minecraft.getInstance().gui.setScreen(parent))
                .bounds(cx - 130, top + 201, 260, 24).build());
        updateLabels();
    }

    private void updateLabels() {
        worldButton.setMessage(Component.literal("World Type:  " + WORLD_LABELS[worldIndex]));
        modeButton.setMessage(Component.literal("Game Mode:  " + MODE_LABELS[modeIndex]));
    }

    private void create() {
        String name = nameBox == null ? "Boxhead Survival" : nameBox.getValue().trim();
        if (name.isBlank()) name = "Boxhead Survival";
        String worldgen = "boxhead:" + WORLD_IDS[worldIndex] + "_" + MODE_IDS[modeIndex];
        DAI_ExperienceLauncher.launchNewConfigured(this, experienceId, name, worldgen);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xFF0B0D0F, 0xFF261010);
        int cx = width / 2;
        int top = Math.max(42, height / 2 - 96);
        graphics.centeredText(font, Component.literal("CREATE BOXHEAD WORLD"), cx, top, 0xFFF4EEE5);
        graphics.centeredText(font, Component.literal("World Name"), cx, top + 18, 0xFFB9AAA5);
        graphics.centeredText(font, Component.literal("Only Boxhead-supported options are exposed."), cx, top + 231, 0xFF9B8D89);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
}
