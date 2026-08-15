package io.github.j12h36h.dai.server.action;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.network.DAI_ServerActionPayload;
import io.github.j12h36h.dai.server.network.DAI_ServerAccessPolicy;
import io.github.j12h36h.dai.server.worldgen.DAI_WorldgenRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

/**
 * Logical-server implementation of DAI's authoritative action vocabulary.
 *
 * These operations deliberately use the server game API for world/inventory
 * mutations. They do not depend on the player's cheats setting, command
 * permission, reach, selected slot, or local client implementation.
 */
public final class DAI_ServerActionExecutor {

    private DAI_ServerActionExecutor() {}

    /**
     * Entry used by optional client payloads. Integrated singleplayer is
     * trusted because the client and logical server belong to the same game.
     * Dedicated servers require an administrative sender for generic
     * privileged requests. Server-owned DAI systems may call executeTrusted
     * directly instead of going through this client authorization path.
     */
    public static boolean executeClientRequest(
            ServerPlayer sender,
            DAI_ServerActionPayload payload
    ) {
        if (sender == null || payload == null) return false;

        MinecraftServer server = sender.level().getServer();
        if (server == null) return false;

        if (!DAI_ServerAccessPolicy.allowPrivilegedClient(sender)) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Rejected privileged DAI server action '{}' from non-admin player '{}'.",
                    payload.operation(),
                    sender.getUUID()
            );
            return false;
        }

        return executeTrusted(sender, payload);
    }

    public static boolean executeTrusted(
            ServerPlayer actor,
            DAI_ServerActionPayload payload
    ) {
        if (actor == null || payload == null) return false;
        if (!(actor.level() instanceof ServerLevel level)) return false;

        String operation = normalize(payload.operation());

        try {
            return switch (operation) {
                case "function", "server_run_function" ->
                        runFunction(actor, payload.action());

                case "command", "server_command", "run_server_command" ->
                        runCommand(actor, payload.action());

                case "set_block", "server_set_block" ->
                        setBlock(level, actor, payload.action(), payload.target());

                case "break_block", "server_break_block" ->
                        breakBlock(level, actor, payload.target(), parseBoolean(payload.state(), true));

                case "give_item", "server_give_item" ->
                        giveItem(actor, payload.action(), count(payload.value()));

                case "take_item", "server_take_item" ->
                        takeItem(actor, payload.action(), count(payload.value()));

                case "experience_startup_dispatched" -> {
                    DAI_WorldgenRuntime.markFirstJoinDispatched(payload.action());
                    yield true;
                }

                default -> {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Ignored unknown authoritative server action '{}'.",
                            operation
                    );
                    yield false;
                }
            };
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Authoritative server action '{}' failed.",
                    operation,
                    exception
            );
            return false;
        }
    }

    private static boolean runFunction(ServerPlayer actor, String rawId) {
        String id = normalize(rawId);
        if (id.startsWith("function ")) id = id.substring("function ".length()).trim();
        if (Identifier.tryParse(id) == null) {
            DAI_Core.LOGGER.warn("<DAI>: Invalid server function id '{}'.", rawId);
            return false;
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Running server-owned function '{}' as player '{}'.",
                id,
                actor.getUUID()
        );

        boolean dispatched = performServerCommand(actor, "function " + id);

        DAI_Core.LOGGER.info(
                "<DAI>: Server-owned function '{}' dispatch complete={}",
                id,
                dispatched
        );

        return dispatched;
    }

    private static boolean runCommand(ServerPlayer actor, String rawCommand) {
        String command = rawCommand == null ? "" : rawCommand.trim();
        while (command.startsWith("/")) command = command.substring(1);
        if (command.isBlank()) return false;
        return performServerCommand(actor, command);
    }

    private static boolean setBlock(
            ServerLevel level,
            ServerPlayer actor,
            String rawBlock,
            String rawTarget
    ) {
        BlockPos pos = parseBlockPos(actor, rawTarget);
        BlockState state = parseBlockState(rawBlock);
        if (pos == null || state == null) return false;

        boolean changed = level.setBlock(pos, state, 3);
        DAI_Core.debug(
                "<DAI>: server_set_block {} -> '{}' changed={}.",
                pos,
                rawBlock,
                changed
        );
        return changed || level.getBlockState(pos).equals(state);
    }

    private static boolean breakBlock(
            ServerLevel level,
            ServerPlayer actor,
            String rawTarget,
            boolean drop
    ) {
        BlockPos pos = parseBlockPos(actor, rawTarget);
        if (pos == null) return false;
        return level.destroyBlock(pos, drop, actor);
    }

    private static boolean giveItem(
            ServerPlayer actor,
            String rawItem,
            int count
    ) {
        Identifier id = Identifier.tryParse(normalize(rawItem));
        Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        if (item == null) {
            DAI_Core.LOGGER.warn("<DAI>: Unknown item '{}' for server_give_item.", rawItem);
            return false;
        }

        ItemStack stack = new ItemStack(item, count);
        boolean inserted = actor.getInventory().add(stack);
        if (!stack.isEmpty()) {
            actor.drop(stack, false);
        }
        return inserted || stack.isEmpty();
    }

    private static boolean takeItem(
            ServerPlayer actor,
            String rawItem,
            int requested
    ) {
        Identifier id = Identifier.tryParse(normalize(rawItem));
        Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        if (item == null) {
            DAI_Core.LOGGER.warn("<DAI>: Unknown item '{}' for server_take_item.", rawItem);
            return false;
        }

        int remaining = requested;
        var inventory = actor.getInventory();

        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.getItem() != item) continue;

            int remove = Math.min(remaining, stack.getCount());
            stack.shrink(remove);
            remaining -= remove;
        }

        inventory.setChanged();
        return remaining == 0;
    }

    private static BlockState parseBlockState(String raw) {
        String spec = normalize(raw);
        if (spec.isBlank()) return null;

        String idText = spec;
        String properties = "";
        int bracket = spec.indexOf('[');
        if (bracket >= 0 && spec.endsWith("]")) {
            idText = spec.substring(0, bracket).trim();
            properties = spec.substring(bracket + 1, spec.length() - 1).trim();
        }

        Identifier id = Identifier.tryParse(idText);
        Block block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
        if (block == null) {
            DAI_Core.LOGGER.warn("<DAI>: Unknown block '{}' for server_set_block.", idText);
            return null;
        }

        BlockState state = block.defaultBlockState();
        if (properties.isBlank()) return state;

        for (String assignment : properties.split(",")) {
            String[] pair = assignment.trim().split("=", 2);
            if (pair.length != 2) {
                DAI_Core.LOGGER.warn("<DAI>: Invalid block-state assignment '{}'.", assignment);
                return null;
            }

            Property<?> property = state.getBlock().getStateDefinition().getProperty(pair[0].trim());
            if (property == null) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Block '{}' has no property '{}'.",
                        idText,
                        pair[0].trim()
                );
                return null;
            }

            BlockState updated = applyProperty(state, property, pair[1].trim());
            if (updated == null) return null;
            state = updated;
        }

        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(
            BlockState state,
            Property property,
            String rawValue
    ) {
        Optional value = property.getValue(rawValue);
        if (value.isEmpty()) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid value '{}' for block property '{}'.",
                    rawValue,
                    property.getName()
            );
            return null;
        }
        return state.setValue(property, (Comparable) value.get());
    }

    private static BlockPos parseBlockPos(ServerPlayer actor, String raw) {
        String target = raw == null ? "" : raw.trim();
        if (target.isBlank() || target.equalsIgnoreCase("self")) {
            return actor.blockPosition();
        }

        String[] parts = target.replace(',', ' ').trim().split("\\s+");
        if (parts.length != 3) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Expected server block target as 'x y z', got '{}'.",
                    raw
            );
            return null;
        }

        Double x = coordinate(parts[0], actor.getX());
        Double y = coordinate(parts[1], actor.getY());
        Double z = coordinate(parts[2], actor.getZ());
        if (x == null || y == null || z == null) return null;

        return new BlockPos(
                (int) Math.floor(x),
                (int) Math.floor(y),
                (int) Math.floor(z)
        );
    }

    private static Double coordinate(String raw, double base) {
        String token = raw == null ? "" : raw.trim();
        try {
            if (token.startsWith("~")) {
                String delta = token.substring(1);
                return base + (delta.isBlank() ? 0.0D : Double.parseDouble(delta));
            }
            return Double.parseDouble(token);
        } catch (NumberFormatException exception) {
            DAI_Core.LOGGER.warn("<DAI>: Invalid coordinate '{}'.", raw);
            return null;
        }
    }

    private static boolean performServerCommand(ServerPlayer actor, String command) {
        MinecraftServer server = actor.level().getServer();
        if (server == null) return false;

        /*
         * Server-authoritative actions must not inherit the player's command
         * permission level. A no-cheats singleplayer save (and an ordinary
         * non-op multiplayer player) is still allowed to participate in an
         * experience whose SERVER owns the mutation.
         *
         * Use the MinecraftServer command source, which is authoritative, and
         * explicitly re-enter the player's execution context. That preserves
         * @s / position semantics inside datapack functions without pretending
         * that the player personally has cheat permission.
         */
        Object source = server.createCommandSourceStack();
        Object quiet = invokeNoArg(source, "withSuppressedOutput");
        if (quiet != null) source = quiet;

        String wrappedCommand =
                "execute as " + actor.getUUID() + " at @s run " + command;

        Object commands = server.getCommands();
        for (Method method : commands.getClass().getMethods()) {
            String name = method.getName();
            if (!name.equals("performPrefixedCommand") && !name.equals("performCommand")) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length != 2 || types[1] != String.class || !types[0].isInstance(source)) continue;

            try {
                Object result = method.invoke(commands, source, wrappedCommand);
                DAI_Core.debug(
                        "<DAI>: Executed server-owned command '{}' (wrapped='{}', result={}).",
                        command,
                        wrappedCommand,
                        result
                );
                return true;
            } catch (ReflectiveOperationException exception) {
                DAI_Core.LOGGER.error(
                        "<DAI>: Minecraft rejected server-owned command '{}' (wrapped='{}').",
                        command,
                        wrappedCommand,
                        exception
                );
                return false;
            }
        }

        DAI_Core.LOGGER.warn(
                "<DAI>: No compatible server command executor found for '{}' (wrapped='{}').",
                command,
                wrappedCommand
        );
        return false;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeInt(Object target, String name, int value) {
        if (target == null) return null;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name)) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length != 1 || types[0] != int.class) continue;
            try {
                return method.invoke(target, value);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int count(double raw) {
        if (!Double.isFinite(raw) || raw <= 0.0D) return 1;
        return Math.max(1, Math.min(9999, (int) Math.round(raw)));
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        String value = normalize(raw);
        if (value.isBlank()) return fallback;
        if (value.equals("true") || value.equals("yes") || value.equals("1")) return true;
        if (value.equals("false") || value.equals("no") || value.equals("0")) return false;
        return fallback;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
