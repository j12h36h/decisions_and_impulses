package io.github.j12h36h.dai.server.network;

import io.github.j12h36h.dai.network.DAI_CreatorActionPayload;
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
        // Only the actual integrated-server owner is implicitly trusted.
        // Remote Open-to-LAN players must satisfy the same explicit access
        // policy as any other network peer.
        if (DAI_ServerAccessPolicy.isIntegratedOwner(server, player)) return true;
        if (DAI_ServerAccessPolicy.hasAdministrativePermission(player)) return true;

        return switch (DAI_ServerConfig.accessMode()) {
            case ALL -> true;
            case OPS_ONLY -> false;
            case ALLOWLIST -> allowlisted(player, DAI_ServerConfig.allowedPlayers());
        };
    }

    /**
     * Privileged Creator operations are deliberately separate from workspace
     * access. A player may be allowed to edit an in-memory draft without being
     * trusted to rewrite server files or execute arbitrary server commands.
     */
    public static boolean allowsPrivileged(ServerPlayer player, boolean automationCreator) {
        if (player == null) return false;
        if (automationCreator ? !DAI_ServerConfig.automationCreatorEnabled() : !DAI_ServerConfig.creatorEnabled()) return false;

        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        // Do not treat every connection to an integrated/Open-to-LAN server
        // as the local owner. Only the actual host receives implicit privileged
        // Creator authority; remote peers still require the configured mode.
        if (DAI_ServerAccessPolicy.isIntegratedOwner(server, player)) return true;

        return switch (DAI_ServerConfig.privilegedAccessMode()) {
            case DISABLED -> false;
            case OPS_ONLY -> DAI_ServerAccessPolicy.hasServerOwnerPermission(player);
            case ALLOWLIST -> DAI_ServerAccessPolicy.hasServerOwnerPermission(player)
                    || allowlisted(player, DAI_ServerConfig.privilegedAllowedPlayers());
        };
    }

    public static boolean requiresPrivileged(DAI_CreatorActionPayload payload) {
        if (payload == null) return false;
        String operation = payload.operation() == null
                ? ""
                : payload.operation().trim().toLowerCase(Locale.ROOT);
        if (operation.equals("save") || operation.equals("delete")
                || operation.equals("run_event") || operation.equals("test")) {
            return true;
        }
        if (operation.equals("mode")) {
            String mode = payload.value() == null ? "" : payload.value().trim().toLowerCase(Locale.ROOT);
            return mode.equals("simulate") || mode.equals("live");
        }
        return false;
    }

    private static boolean allowlisted(ServerPlayer player, String configured) {
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
