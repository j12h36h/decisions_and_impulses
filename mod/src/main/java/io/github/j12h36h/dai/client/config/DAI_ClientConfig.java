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

    public static final ModConfigSpec.BooleanValue DAI_LOADING_SCREENS = BUILDER
            .comment("Use DAI's universe-ring loading presentation whenever an Experience does not provide its own loading screen.")
            .define("daiLoadingScreens", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean creatorEnabled() { return bool(CREATOR_ENABLED, true); }
    public static boolean automationCreatorEnabled() { return bool(AUTOMATION_CREATOR_ENABLED, true); }
    public static boolean loadingScreens() { return bool(DAI_LOADING_SCREENS, true); }

    private static boolean bool(ModConfigSpec.BooleanValue value, boolean fallback) {
        try { return value.get(); } catch (IllegalStateException ignored) { return fallback; }
    }

    private DAI_ClientConfig() {}
}
