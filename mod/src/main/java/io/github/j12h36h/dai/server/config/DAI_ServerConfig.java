package io.github.j12h36h.dai.server.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Dedicated/integrated-server controls for privileged creator tooling. */
public final class DAI_ServerConfig {
    public enum CreatorAccessMode { OPS_ONLY, ALLOWLIST, ALL }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CREATOR_ENABLED = BUILDER
            .comment("Master server switch for DAI Creator mutations and live simulations.")
            .define("creatorEnabled", true);

    public static final ModConfigSpec.BooleanValue AUTOMATION_CREATOR_ENABLED = BUILDER
            .comment("Master server switch for Automation Creator live edits/exports.")
            .define("automationCreatorEnabled", true);

    public static final ModConfigSpec.EnumValue<CreatorAccessMode> CREATOR_ACCESS_MODE = BUILDER
            .comment("OPS_ONLY: operators only. ALLOWLIST: operators plus creatorAllowedPlayers. ALL: every connected player.")
            .defineEnum("creatorAccessMode", CreatorAccessMode.OPS_ONLY);

    public static final ModConfigSpec.ConfigValue<String> CREATOR_ALLOWED_PLAYERS = BUILDER
            .comment("Comma/semicolon separated player names or UUIDs allowed when creatorAccessMode=ALLOWLIST. Operators are always allowed.")
            .define("creatorAllowedPlayers", "");

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean creatorEnabled() { return bool(CREATOR_ENABLED, true); }
    public static boolean automationCreatorEnabled() { return bool(AUTOMATION_CREATOR_ENABLED, true); }
    public static CreatorAccessMode accessMode() {
        try { return CREATOR_ACCESS_MODE.get(); } catch (IllegalStateException ignored) { return CreatorAccessMode.OPS_ONLY; }
    }
    public static String allowedPlayers() {
        try { return CREATOR_ALLOWED_PLAYERS.get(); } catch (IllegalStateException ignored) { return ""; }
    }

    private static boolean bool(ModConfigSpec.BooleanValue value, boolean fallback) {
        try { return value.get(); } catch (IllegalStateException ignored) { return fallback; }
    }

    private DAI_ServerConfig() {}
}
