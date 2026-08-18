package io.github.j12h36h.dai.customization;

import java.util.Locale;

/**
 * First-class JSON game-customization registries introduced for DAI 1.9.
 *
 * These registries intentionally sit above vanilla data-pack primitives. A
 * definition may delegate to ordinary Minecraft commands/functions, DAI action
 * sequences, overlays, menus, reactions, native content, or any combination of
 * those systems without requiring generated Java.
 */
public enum DAI_GameCustomizationKind {
    SOUND("dai_sounds"),
    MUSIC("dai_music"),
    HUD("dai_hud"),
    RENDER_PROFILE("dai_render_profiles"),
    STRUCTURE("dai_structures"),
    FEATURE("dai_features"),
    LOOT("dai_loot"),
    CURRENCY("dai_currencies"),
    SHOP("dai_shops"),
    DIALOGUE("dai_dialogues"),
    QUEST("dai_quests"),
    FACTION("dai_factions"),
    BIOME("dai_biomes"),
    DIMENSION_TYPE("dai_dimension_types"),
    DIMENSION("dai_dimensions"),
    TIMELINE("dai_timelines"),
    RULESET("dai_rules"),
    VEHICLE("dai_vehicles"),
    INTERACTIVE("dai_interactives"),
    FLUID("dai_fluids"),
    ENVIRONMENT("dai_environments");

    private final String folder;

    DAI_GameCustomizationKind(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return folder;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DAI_GameCustomizationKind parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (DAI_GameCustomizationKind kind : values()) {
            if (kind.id().equals(normalized) || kind.folder.equals(normalized)) return kind;
        }
        return null;
    }
}
