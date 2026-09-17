package io.github.j12h36h.dai.server.gamerules;

import io.github.j12h36h.dai.gamerules.DAI_WorldGameRuleOverrides;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Applies Modify World game-rule overrides through Minecraft's live GameRules API. */
public final class DAI_WorldGameRuleRuntime {

    private static boolean initialized;

    private DAI_WorldGameRuleRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener(DAI_WorldGameRuleRuntime::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        DAI_WorldGameRuleOverrides.applyToServer(event.getServer());
    }
}
