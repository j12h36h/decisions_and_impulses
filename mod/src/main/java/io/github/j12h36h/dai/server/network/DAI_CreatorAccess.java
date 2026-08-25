package io.github.j12h36h.dai.server.network;

import io.github.j12h36h.dai.server.config.DAI_ServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** Server-authoritative access policy for DAI's creator workspaces. */
public final class DAI_CreatorAccess {
    private DAI_CreatorAccess() {}

    public static boolean allows(ServerPlayer player, boolean automationCreator) {
        if (player == null) return false;
        if (automationCreator ? !DAI_ServerConfig.automationCreatorEnabled() : !DAI_ServerConfig.creatorEnabled()) return false;

        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        if (!server.isDedicatedServer()) return true;
        if (DAI_ServerAccessPolicy.hasAdministrativePermission(player)) return true;

        return switch (DAI_ServerConfig.accessMode()) {
            case ALL -> true;
            case OPS_ONLY -> false;
            case ALLOWLIST -> allowlisted(player);
        };
    }

    private static boolean allowlisted(ServerPlayer player) {
        String configured = DAI_ServerConfig.allowedPlayers();
        if (configured == null || configured.isBlank()) return false;
        String name = player.getGameProfile().name().toLowerCase(Locale.ROOT);
        String uuid = player.getUUID().toString().toLowerCase(Locale.ROOT);
        for (String raw : configured.split("[,;\\n\\r]+")) {
            String token = raw.trim().toLowerCase(Locale.ROOT);
            if (token.equals(name) || token.equals(uuid)) return true;
        }
        return false;
    }
}
