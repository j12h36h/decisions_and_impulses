package io.github.j12h36h.dai.server.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * Central authorization boundary for client-originated requests that mutate
 * authoritative server state.
 *
 * Integrated singleplayer trusts its local client. Dedicated servers require
 * an administrative sender until DAI gains a server-declared client-callable
 * capability policy for multiplayer experiences.
 */
public final class DAI_ServerAccessPolicy {

    private DAI_ServerAccessPolicy() {}

    public static boolean allowPrivilegedClient(ServerPlayer sender) {
        if (sender == null) return false;
        MinecraftServer server = sender.level().getServer();
        if (server == null) return false;
        return !server.isDedicatedServer() || hasAdministrativePermission(sender);
    }

    public static boolean hasAdministrativePermission(ServerPlayer sender) {
        Object source = sender.createCommandSourceStack();

        for (String methodName : new String[]{"hasPermission", "hasPermissionLevel"}) {
            for (Method method : source.getClass().getMethods()) {
                if (!method.getName().equals(methodName)) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length != 1 || types[0] != int.class) continue;
                try {
                    Object result = method.invoke(source, 2);
                    if (result instanceof Boolean allowed) return allowed;
                } catch (ReflectiveOperationException ignored) {
                    // Try another supported permission API shape.
                }
            }
        }

        return false;
    }
}
