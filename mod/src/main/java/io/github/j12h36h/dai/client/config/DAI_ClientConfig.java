package io.github.j12h36h.dai.client.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only presentation and authoring switches. */
public final class DAI_ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CREATOR_ENABLED = BUILDER
            .comment("Allow the in-game DAI Creator interface on this client.")
            .define("creatorEnabled", true);

    public static final ModConfigSpec.BooleanValue AUTOMATION_CREATOR_ENABLED = BUILDER
            .comment("Allow the in-game Automation Creator interface on this client.")
            .define("automationCreatorEnabled", true);

    public static final ModConfigSpec.BooleanValue FULL_GAME_SHELL = BUILDER
            .comment("Replace Minecraft's default title/pause presentation loop with the DAI Engine universe shell.")
            .define("fullGameShell", true);

    public static final ModConfigSpec.BooleanValue DAI_LOADING_SCREENS = BUILDER
            .comment("Legacy fallback switch. In fullGameShell mode DAI 4.3 always owns the bootstrap/world-transition splash.")
            .define("daiLoadingScreens", true);

    public static final ModConfigSpec.BooleanValue AUTO_SHELL_WORLD = BUILDER
            .comment("Legacy shell-world switch. In fullGameShell mode DAI 4.3 always loads the reserved base world before revealing the 3-D title.")
            .define("autoShellWorld", true);

    public static final ModConfigSpec.ConfigValue<String> PRESENTATION_PROFILE = BUILDER
            .comment("Selected DAI presentation profile. Profiles may be provided by enabled/included resource packs through dai/presentation.json.")
            .define("presentationProfile", "dai:default", value -> value instanceof String string && !string.isBlank());

    public static final ModConfigSpec.BooleanValue WORLD_CATALOG_REFRESH = BUILDER
            .comment("Allow DAI Worlds to refresh the ERAS catalog while online. The last valid catalog remains cached for offline use.")
            .define("worldCatalogRefresh", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean creatorEnabled() { return bool(CREATOR_ENABLED, true); }
    public static boolean automationCreatorEnabled() { return bool(AUTOMATION_CREATOR_ENABLED, true); }
    public static boolean fullGameShell() { return bool(FULL_GAME_SHELL, true); }
    public static boolean loadingScreens() { return fullGameShell() || bool(DAI_LOADING_SCREENS, true); }
    public static boolean autoShellWorld() { return fullGameShell() || bool(AUTO_SHELL_WORLD, true); }
    public static String presentationProfile() {
        try {
            String value = PRESENTATION_PROFILE.get();
            return value == null || value.isBlank() ? "dai:default" : value.trim();
        } catch (IllegalStateException ignored) {
            return "dai:default";
        }
    }
    public static boolean worldCatalogRefresh() { return bool(WORLD_CATALOG_REFRESH, true); }

    public static void save() {
        try { SPEC.save(); } catch (RuntimeException ignored) { }
    }

    private static boolean bool(ModConfigSpec.BooleanValue value, boolean fallback) {
        try { return value.get(); } catch (IllegalStateException ignored) { return fallback; }
    }

    private DAI_ClientConfig() {}
}
