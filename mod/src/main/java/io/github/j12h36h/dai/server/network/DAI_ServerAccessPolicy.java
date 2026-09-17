package io.github.j12h36h.dai.server.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.lang.reflect.Method;

/**
 * Central authorization boundary for client-originated requests that mutate
 * authoritative server state.
 *
 * Integrated singleplayer trusts only the actual local world owner. Remote
 * players connected through Open to LAN are still untrusted unless normal
 * server permissions or an explicit DAI capability authorize the request.
 */
public final class DAI_ServerAccessPolicy {

    private DAI_ServerAccessPolicy() {}

    public static boolean allowPrivilegedClient(ServerPlayer sender) {
        if (sender == null) return false;
        MinecraftServer server = sender.level().getServer();
        if (server == null) return false;
        return isIntegratedOwner(server, sender) || hasAdministrativePermission(sender);
    }

    /** Integrated singleplayer trusts its local owner; dedicated servers require
     * the strongest operator tier for requests equivalent to server-console
     * command/function execution or unrestricted server-file authoring. */
    public static boolean allowServerOwnerClient(ServerPlayer sender) {
        if (sender == null) return false;
        MinecraftServer server = sender.level().getServer();
        if (server == null) return false;
        return isIntegratedOwner(server, sender) || hasServerOwnerPermission(sender);
    }

    /**
     * Trust only the actual integrated-server host. This remains false for
     * remote players connected through Open to LAN.
     */
    public static boolean isIntegratedOwner(MinecraftServer server, ServerPlayer sender) {
        return server != null
                && sender != null
                && !server.isDedicatedServer()
                && server.isSingleplayerOwner(new NameAndId(sender.getGameProfile()));
    }

    public static boolean hasAdministrativePermission(ServerPlayer sender) {
        return hasPermissionLevel(sender, 2);
    }

    /**
     * Strong authority boundary for operations equivalent to modifying the
     * server installation/world files or executing arbitrary server-source
     * commands. Permission level 4 is intentionally stricter than the level-2
     * gameplay mutation boundary.
     */
    public static boolean hasServerOwnerPermission(ServerPlayer sender) {
        return hasPermissionLevel(sender, 4);
    }

    private static boolean hasPermissionLevel(ServerPlayer sender, int level) {
        if (sender == null) return false;
        Object source = sender.createCommandSourceStack();

        for (String methodName : new String[]{"hasPermission", "hasPermissionLevel"}) {
            for (Method method : source.getClass().getMethods()) {
                if (!method.getName().equals(methodName)) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length != 1 || types[0] != int.class) continue;
                try {
                    Object result = method.invoke(source, level);
                    if (result instanceof Boolean allowed) return allowed;
                } catch (ReflectiveOperationException ignored) {
                    // Try another supported permission API shape.
                }
            }
        }

        return false;
    }
}
