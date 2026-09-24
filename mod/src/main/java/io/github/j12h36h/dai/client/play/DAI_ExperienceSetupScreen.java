package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.experience.DAI_ExperienceRepository;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.packs.DAI_DatapackMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * DAI 4.3 Experience configuration surface.
 *
 * The Experience determines which ADDON ids are permitted; the player chooses
 * the exact permitted set for this new save. The stable ids are persisted in
 * the world and deliberately do not include addon versions.
 */
public final class DAI_ExperienceSetupScreen extends Screen {

    private final Screen parent;
    private final String experienceId;
    private final int page;
    private final Set<String> selected;
    private final boolean seedDefaults;
    private DAI_ExperienceDefinition experience;
    private List<AddonChoice> addons = List.of();

    public DAI_ExperienceSetupScreen(Screen parent, String experienceId) {
        this(parent, experienceId, 0, null);
    }

    private DAI_ExperienceSetupScreen(
            Screen parent,
            String experienceId,
            int page,
            Set<String> selected
    ) {
        super(Component.literal("Experience Setup"));
        this.parent = parent;
        this.experienceId = experienceId == null ? "" : experienceId.trim().toLowerCase(java.util.Locale.ROOT);
        this.page = Math.max(0, page);
        this.seedDefaults = selected == null;
        this.selected = selected == null ? new LinkedHashSet<>() : new LinkedHashSet<>(selected);
    }

    @Override
    protected void init() {
        super.init();
        DAI_ExperienceRepository.reloadSelectable();
        experience = DAI_ExperienceRepository.getSelectable(experienceId);
        if (experience == null) {
            addRenderableWidget(button(width / 2 - 48, height - 30, 96, 20, "BACK", b -> onClose(), false));
            return;
        }

        ArrayList<AddonChoice> choices = new ArrayList<>();
        for (Path addon : DAI_DatapackMetadata.globalAddons()) {
            String stableId = DAI_DatapackMetadata.stableId(addon);
            if (stableId.isBlank()) continue;
            boolean allowed = experience.addons().allows(stableId);
            choices.add(new AddonChoice(stableId, label(addon), allowed));
            if (seedDefaults && allowed && DAI_Config.autoEnableAddons()) selected.add(stableId);
        }
        choices.sort(Comparator.comparing(AddonChoice::label, String.CASE_INSENSITIVE_ORDER));
        addons = List.copyOf(choices);

        int rows = Math.max(2, Math.min(6, (height - 150) / 28));
        int maxPage = Math.max(0, (addons.size() - 1) / rows);
        int current = Math.min(page, maxPage);
        int start = Math.min(current * rows, addons.size());
        int end = Math.min(start + rows, addons.size());
        int rowWidth = Math.min(500, Math.max(250, width - 36));
        int left = width / 2 - rowWidth / 2;
        int y = 64;

        for (int i = start; i < end; i++) {
            AddonChoice addon = addons.get(i);
            boolean enabled = selected.contains(addon.stableId());
            String prefix = !addon.allowed() ? "× " : (enabled ? "✓ " : "□ ");
            addRenderableWidget(button(
                    left, y, rowWidth, 22,
                    prefix + addon.label(),
                    b -> toggle(addon, current),
                    enabled,
                    addon.allowed()
            ));
            y += 28;
        }

        if (maxPage > 0) {
            int navY = height - 76;
            addRenderableWidget(button(width / 2 - 106, navY, 62, 20, "◀ PREV",
                    b -> reopen(Math.max(0, current - 1)), false));
            addRenderableWidget(button(width / 2 - 39, navY, 78, 20,
                    (current + 1) + " / " + (maxPage + 1), b -> {}, false));
            addRenderableWidget(button(width / 2 + 44, navY, 62, 20, "NEXT ▶",
                    b -> reopen(Math.min(maxPage, current + 1)), false));
        }

        addRenderableWidget(button(width / 2 - 118, height - 28, 108, 20, "BACK", b -> onClose(), false));
        addRenderableWidget(button(width / 2 + 10, height - 28, 108, 20, "START", b -> launch(), true));
    }

    private void toggle(AddonChoice addon, int currentPage) {
        if (addon == null || !addon.allowed()) return;
        if (!selected.remove(addon.stableId())) selected.add(addon.stableId());
        Minecraft.getInstance().gui.setScreen(new DAI_ExperienceSetupScreen(parent, experienceId, currentPage, selected));
    }

    private void reopen(int nextPage) {
        Minecraft.getInstance().gui.setScreen(new DAI_ExperienceSetupScreen(parent, experienceId, nextPage, selected));
    }

    private void launch() {
        if (experience == null) return;
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        for (String id : selected) if (experience.addons().allows(id)) allowed.add(id);
        DAI_ExperienceLauncher.launchNew(parent, experience.id(), Set.copyOf(allowed));
    }

    private DAI_UniverseButton button(int x, int y, int w, int h, String text, Button.OnPress press, boolean primary) {
        return button(x, y, w, h, text, press, primary, true);
    }

    private DAI_UniverseButton button(
            int x, int y, int w, int h, String text,
            Button.OnPress press, boolean primary, boolean active
    ) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseButton button = new DAI_UniverseButton(
                x, y, w, h, Component.literal(text), press,
                DAI_UniverseButton.Shape.CHIP,
                primary ? profile.primary() : profile.secondary()
        );
        button.active = active;
        return button;
    }

    private static String label(Path path) {
        if (path == null || path.getFileName() == null) return "DAI Addon";
        String value = path.getFileName().toString();
        int dot = value.lastIndexOf('.');
        if (dot > 0) value = value.substring(0, dot);
        value = value.replace('_', ' ').replace('-', ' ').trim();
        return value.isBlank() ? "DAI Addon" : value;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        String name = experience == null ? experienceId : display(experience);
        graphics.centeredText(font, Component.literal(name), width / 2, 14, profile.text());
        String policy = experience == null
                ? "EXPERIENCE UNAVAILABLE"
                : !experience.addons().enabled()
                        ? "ADDONS DISABLED BY EXPERIENCE"
                        : experience.addons().whitelist().isEmpty()
                                ? "SELECT ALLOWED ADDONS"
                                : "SELECT FROM EXPERIENCE WHITELIST";
        graphics.centeredText(font, Component.literal(policy), width / 2, 28, 0xFF9FB2BE);
        graphics.centeredText(font, Component.literal(selected.size() + " selected"), width / 2, 42, 0xFFFFD782);
        if (addons.isEmpty() && experience != null) {
            graphics.centeredText(font, Component.literal("No installed DAI ADDON packs are available."), width / 2, 92, 0xFFB7C7D0);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private static String display(DAI_ExperienceDefinition definition) {
        String value = definition.saveName();
        if (value == null || value.isBlank()) value = definition.id();
        return value == null ? "DAI EXPERIENCE" : value.toUpperCase(java.util.Locale.ROOT);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }

    private record AddonChoice(String stableId, String label, boolean allowed) {}
}
