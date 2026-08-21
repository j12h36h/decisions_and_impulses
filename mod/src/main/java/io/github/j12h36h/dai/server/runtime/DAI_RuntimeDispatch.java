package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

/** Shared server-side command/function dispatcher for JSON lifecycle hooks. */
public final class DAI_RuntimeDispatch {
    private DAI_RuntimeDispatch() {}

    public static boolean contentEvent(Entity actor, DAI_ContentRegistry.Entry entry, String eventName) {
        if (entry == null || eventName == null) return false;
        String reference = event(entry.definition().events(), eventName);
        return dispatch(actor, reference);
    }

    public static boolean contentEventAt(ServerLevel level, BlockPos pos, DAI_ContentRegistry.Entry entry, String eventName) {
        if (entry == null || eventName == null) return false;
        String reference = event(entry.definition().events(), eventName);
        return dispatchAt(level, pos, reference);
    }

    public static boolean dispatch(Entity actor, String rawReference) {
        if (actor == null) return false;
        MinecraftServer server = actor.level().getServer();
        if (server == null) return false;
        String command = command(rawReference);
        if (command.isBlank()) return false;
        return perform(server, actor, null, null, command);
    }

    public static boolean dispatchAt(ServerLevel level, BlockPos pos, String rawReference) {
        if (level == null || pos == null) return false;
        MinecraftServer server = level.getServer();
        if (server == null) return false;
        String command = command(rawReference);
        if (command.isBlank()) return false;
        return perform(server, null, level, Vec3.atCenterOf(pos), command);
    }

    public static String event(Map<String, String> events, String name) {
        if (events == null || events.isEmpty() || name == null) return "";
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        String direct = events.get(normalized);
        if (direct != null) return direct.trim();
        for (var e : events.entrySet()) {
            if (e.getKey() != null && e.getKey().trim().equalsIgnoreCase(normalized)) {
                return e.getValue() == null ? "" : e.getValue().trim();
            }
        }
        return "";
    }

    private static String command(String raw) {
        String reference = raw == null ? "" : raw.trim();
        if (reference.isBlank()) return "";
        String lower = reference.toLowerCase(Locale.ROOT);
        if (lower.startsWith("command:")) return reference.substring("command:".length()).trim();
        if (lower.startsWith("function:")) return "function " + reference.substring("function:".length()).trim();
        while (reference.startsWith("/")) reference = reference.substring(1);
        if (reference.indexOf(' ') < 0 && reference.indexOf(':') > 0) return "function " + reference;
        return reference;
    }

    private static boolean perform(
            MinecraftServer server,
            Entity actor,
            ServerLevel level,
            Vec3 position,
            String rawCommand
    ) {
        String command = rawCommand == null ? "" : rawCommand.trim();
        while (command.startsWith("/")) command = command.substring(1);
        if (command.isBlank()) return false;

        Object source = server.createCommandSourceStack();
        Object quiet = invokeOne(source, "withSuppressedOutput");
        if (quiet != null) source = quiet;
        if (level != null) {
            Object withLevel = invoke(source, "withLevel", level);
            if (withLevel != null) source = withLevel;
        }
        if (position != null) {
            Object withPosition = invoke(source, "withPosition", position);
            if (withPosition != null) source = withPosition;
        }

        if (actor != null) {
            command = "execute as " + actor.getUUID() + " at @s run " + command;
        }

        Object commands = server.getCommands();
        for (Method method : commands.getClass().getMethods()) {
            String name = method.getName();
            if (!name.equals("performPrefixedCommand") && !name.equals("performCommand")) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length != 2 || types[1] != String.class || !types[0].isInstance(source)) continue;
            try {
                method.invoke(commands, source, command);
                return true;
            } catch (ReflectiveOperationException exception) {
                DAI_Core.LOGGER.warn("<DAI>: Runtime lifecycle command '{}' failed.", command, exception);
                return false;
            }
        }
        return false;
    }

    private static Object invokeOne(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (Throwable ignored) { return null; }
    }

    private static Object invoke(Object target, String name, Object value) {
        if (target == null || value == null) return null;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            if (!method.getParameterTypes()[0].isInstance(value)) continue;
            try { return method.invoke(target, value); }
            catch (Throwable ignored) { return null; }
        }
        return null;
    }
}
