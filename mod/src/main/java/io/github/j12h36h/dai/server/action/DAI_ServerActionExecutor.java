package io.github.j12h36h.dai.server.action;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.network.DAI_ServerActionPayload;
import io.github.j12h36h.dai.server.network.DAI_ServerAccessPolicy;
import io.github.j12h36h.dai.server.worldgen.DAI_WorldgenRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_ProjectileRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_AudioRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_PotionRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_EffectRuntime;
import io.github.j12h36h.dai.server.runtime.DAI_ParticleRuntime;
import io.github.j12h36h.dai.server.state.DAI_ServerStateRuntime;
import io.github.j12h36h.dai.api.DAI_StateValue;
import io.github.j12h36h.dai.content.DAI_ItemComponentRuntime;
import io.github.j12h36h.dai.content.DAI_JsonBlockEntity;
import io.github.j12h36h.dai.logics.action.DAI_ActionArguments;
import io.github.j12h36h.dai.state.DAI_StateRegistry;
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

        String operation = normalize(payload.operation());

        /*
         * 1.9 customization events are safe for ordinary players because the
         * client only names a server-loaded definition + event. Any command or
         * function that ultimately runs is sourced from the trusted datapack,
         * never from arbitrary client text.
         */
        if (operation.equals("customization_event")) {
            return executeTrusted(sender, payload);
        }

        if (operation.startsWith("state_")) {
            var definition = DAI_StateRegistry.get(payload.action());
            if (definition != null && definition.serverOwned() && definition.clientWritable()) {
                return executeTrusted(sender, payload);
            }
        }

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

                case "projectile_spawn", "server_projectile_spawn" ->
                        DAI_ProjectileRuntime.spawn(actor, payload.action(), DAI_ActionArguments.fromJson(payload.argumentsJson()));

                case "particle_emit", "server_particle_emit" ->
                        DAI_ParticleRuntime.emit(actor, payload.action(), DAI_ActionArguments.fromJson(payload.argumentsJson()));

                case "effect_apply", "server_effect_apply" ->
                        DAI_EffectRuntime.apply(actor, payload.action(), parseInt(payload.target(), 0), (int)Math.round(payload.value()));

                case "effect_remove", "server_effect_remove" ->
                        DAI_EffectRuntime.remove(actor, payload.action());

                case "potion_apply", "server_potion_apply" ->
                        DAI_PotionRuntime.apply(actor, payload.action());

                case "take_item", "server_take_item" ->
                        takeItem(actor, payload.action(), count(payload.value()));

                case "experience_startup_dispatched" -> {
                    DAI_WorldgenRuntime.markFirstJoinDispatched(payload.action());
                    yield true;
                }

                case "state_set" ->
                        DAI_ServerStateRuntime.mutate(actor, payload.action(), "set", stateValue(payload.target(), payload.state(), payload.value()), actor);

                case "state_add" ->
                        DAI_ServerStateRuntime.mutate(actor, payload.action(), "add", DAI_StateValue.number(payload.value()), actor);

                case "state_toggle" ->
                        DAI_ServerStateRuntime.mutate(actor, payload.action(), "toggle", DAI_StateValue.bool(false), actor);

                case "state_clear" ->
                        DAI_ServerStateRuntime.mutate(actor, payload.action(), "clear", DAI_StateValue.missing(), actor);

                case "item_component_set" ->
                        setItemComponent(actor, payload.action(), payload.target(), payload.argumentsJson());

                case "item_component_remove" ->
                        DAI_ItemComponentRuntime.remove(DAI_ItemComponentRuntime.resolveStack(actor, payload.target()), payload.action());

                case "item_component_copy" ->
                        copyItemComponent(actor, payload.action(), payload.target(), payload.argumentsJson());

                case "block_entity_set_boolean" ->
                        mutateBlockEntity(level, actor, payload.target(), payload.action(), "set",
                                DAI_StateValue.bool(parseBoolean(payload.state(), false)));

                case "block_entity_set_number" ->
                        mutateBlockEntity(level, actor, payload.target(), payload.action(), "set",
                                DAI_StateValue.number(payload.value()));

                case "block_entity_set_string" ->
                        mutateBlockEntity(level, actor, payload.target(), payload.action(), "set",
                                DAI_StateValue.string(DAI_ActionArguments.fromJson(payload.argumentsJson()).string("text", payload.state())));

                case "block_entity_add_number" ->
                        mutateBlockEntity(level, actor, payload.target(), payload.action(), "add",
                                DAI_StateValue.number(payload.value()));

                case "block_entity_toggle_boolean" ->
                        mutateBlockEntity(level, actor, payload.target(), payload.action(), "toggle", DAI_StateValue.bool(false));

                case "block_entity_clear" ->
                        mutateBlockEntity(level, actor, payload.target(), payload.action(), "clear", DAI_StateValue.missing());

                case "block_entity_slot_set" ->
                        setBlockEntitySlot(level, actor, payload.target(), parseInt(payload.state(), 0), payload.action(), count(payload.value()));

                case "block_entity_slot_clear" ->
                        clearBlockEntitySlot(level, actor, payload.target(), parseInt(payload.state(), 0));

                case "customization_event" ->
                        customizationEvent(actor, payload.action(), payload.target(), payload.state(), payload.value());

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

    private static boolean customizationEvent(
            ServerPlayer actor,
            String rawKind,
            String rawId,
            String rawEvent,
            double value
    ) {
        DAI_GameCustomizationKind kind = DAI_GameCustomizationKind.parse(rawKind);
        if (kind == null) {
            DAI_Core.LOGGER.warn("<DAI>: Unknown customization kind '{}'.", rawKind);
            return false;
        }

        DAI_GameCustomizationRegistry.Entry entry =
                DAI_GameCustomizationRegistry.get(kind, rawId);
        if (entry == null) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Unknown {} customization definition '{}'.",
                    kind.id(), rawId
            );
            return false;
        }

        DAI_GameCustomizationDefinition definition = entry.definition();
        String eventPayload = rawEvent == null ? "" : rawEvent;
        String[] eventParts = eventPayload.split("\n", 2);
        String eventName = normalize(eventParts.length == 0 ? "" : eventParts[0]);
        String runtimeTarget = eventParts.length > 1 ? eventParts[1].trim() : "";
        if (eventName.isBlank()) eventName = "run";
        DAI_AudioRuntime.onCustomizationEvent(actor, kind, entry, eventName);
        String dispatch = definition.event(eventName);

        if (dispatch.isBlank()) {
            dispatch = definition.command();
        }

        /*
         * Rulesets can be authored as a compact entries array even when no
         * explicit event/command exists: ["doDaylightCycle=false", ...].
         */
        if (dispatch.isBlank() && kind == DAI_GameCustomizationKind.RULESET
                && (eventName.equals("apply") || eventName.equals("run"))) {
            boolean ok = true;
            for (String entryText : definition.entries()) {
                if (entryText == null || entryText.isBlank()) continue;
                String[] pair = entryText.trim().split("=", 2);
                if (pair.length != 2) {
                    DAI_Core.LOGGER.warn("<DAI>: Invalid ruleset entry '{}'.", entryText);
                    ok = false;
                    continue;
                }
                ok &= performServerCommand(actor, "gamerule " + pair[0].trim() + " " + pair[1].trim());
            }
            return ok;
        }

        if (dispatch.isBlank()) {
            dispatch = defaultCustomizationCommand(kind, entry.id().toString(), eventName, definition, value, runtimeTarget);
        }

        if (dispatch.isBlank()) {
            // A state-only customization definition is still a valid event.
            return true;
        }

        String expanded = expandCustomizationPlaceholders(
                dispatch, actor, kind, entry.id().toString(), eventName, value, runtimeTarget, definition
        );
        String lower = expanded.toLowerCase(Locale.ROOT);

        if (lower.startsWith("command:")) {
            return performServerCommand(actor, expanded.substring("command:".length()).trim());
        }
        if (lower.startsWith("function:")) {
            return runFunction(actor, expanded.substring("function:".length()).trim());
        }

        /*
         * Unprefixed identifiers are treated as functions only on the server.
         * Existing DAI action ids are normally consumed by the client before a
         * customization_event payload is sent.
         */
        if (Identifier.tryParse(expanded) != null) {
            return runFunction(actor, expanded);
        }

        return performServerCommand(actor, expanded);
    }

    private static String defaultCustomizationCommand(
            DAI_GameCustomizationKind kind,
            String definitionId,
            String event,
            DAI_GameCustomizationDefinition definition,
            double value,
            String runtimeTarget
    ) {
        String carrier = definition.carrier();
        String target = runtimeTarget == null || runtimeTarget.isBlank()
                ? (definition.target().isBlank() ? "~ ~ ~" : definition.target())
                : runtimeTarget;

        return switch (kind) {
            case SOUND -> {
                if (carrier.isBlank()) yield "";
                String source = definition.property("source");
                if (source.isBlank()) source = "master";
                String audience = definition.property("audience");
                if (audience.isBlank()) audience = "@s";
                if (event.equals("stop")) {
                    yield "command:stopsound " + audience + " " + source + " " + carrier;
                }
                double volume = definition.number("volume", 1.0D);
                double pitch = definition.number("pitch", 1.0D);
                double minVolume = Math.max(0.0D, definition.number("min_volume", 0.0D));
                yield "command:playsound " + carrier + " " + source
                        + " " + audience + " " + target + " " + volume + " " + pitch + " " + minVolume;
            }
            case MUSIC -> {
                if (carrier.isBlank()) yield "";
                String audience = definition.property("audience");
                if (audience.isBlank()) audience = "@s";
                if (event.equals("stop")) {
                    yield "command:stopsound " + audience + " music " + carrier;
                }
                double volume = definition.number("volume", 1.0D);
                double pitch = definition.number("pitch", 1.0D);
                double minVolume = Math.max(0.0D, definition.number("min_volume", 0.0D));
                yield "command:playsound " + carrier + " music " + audience + " " + target
                        + " " + volume + " " + pitch + " " + minVolume;
            }
            case STRUCTURE -> carrier.isBlank()
                    ? ""
                    : "command:place template " + carrier + " " + target;
            case FEATURE -> carrier.isBlank()
                    ? ""
                    : "command:place feature " + carrier + " " + target;
            case LOOT -> carrier.isBlank()
                    ? ""
                    : "command:loot give @s loot " + carrier;
            case CURRENCY -> {
                String objective = definition.property("objective");
                if (objective.isBlank()) objective = definition.property("scoreboard");
                if (objective.isBlank()) yield "";
                int amount = (int) Math.round(value == 0.0D
                        ? definition.number("amount", 1.0D)
                        : value);
                if (event.equals("take")) amount = Math.abs(amount);
                if (event.equals("add")) amount = Math.abs(amount);
                yield switch (event) {
                    case "add" -> "command:scoreboard players add @s " + objective + " " + amount;
                    case "take" -> "command:scoreboard players remove @s " + objective + " " + amount;
                    case "set" -> "command:scoreboard players set @s " + objective + " " + amount;
                    default -> "";
                };
            }
            case FACTION -> {
                String tag = definition.property("tag");
                if (tag.isBlank()) yield "";
                yield event.equals("leave")
                        ? "command:tag @s remove " + tag
                        : "command:tag @s add " + tag;
            }
            case DIMENSION -> {
                String dimension = carrier.isBlank() ? normalize(definitionId) : carrier;
                yield dimension.isBlank()
                        ? ""
                        : "command:execute in " + dimension + " run tp @s " + target;
            }
            case VEHICLE -> {
                if (event.equals("dismount")) yield "command:ride @s dismount";
                if (carrier.isBlank()) yield "";
                yield switch (event) {
                    case "spawn" -> "command:summon " + carrier + " " + target;
                    case "mount" -> "command:ride @s mount @e[type=" + carrier
                            + ",sort=nearest,limit=1,distance=..8]";
                    case "despawn" -> "command:kill @e[type=" + carrier
                            + ",sort=nearest,limit=1,distance=..8]";
                    default -> "";
                };
            }
            case FLUID -> carrier.isBlank() || !event.equals("apply")
                    ? ""
                    : "command:setblock " + target + " " + carrier;
            default -> "";
        };
    }

    private static String expandCustomizationPlaceholders(
            String raw,
            ServerPlayer actor,
            DAI_GameCustomizationKind kind,
            String id,
            String event,
            double value,
            String runtimeTarget,
            DAI_GameCustomizationDefinition definition
    ) {
        String text = raw == null ? "" : raw.trim();
        String target = definition.target();
        return text
                .replace("{player}", actor.getName().getString())
                .replace("{uuid}", actor.getUUID().toString())
                .replace("{x}", Double.toString(actor.getX()))
                .replace("{y}", Double.toString(actor.getY()))
                .replace("{z}", Double.toString(actor.getZ()))
                .replace("{value}", Double.toString(value))
                .replace("{kind}", kind.id())
                .replace("{id}", id)
                .replace("{event}", event)
                .replace("{target}", target == null ? "" : target)
                .replace("{runtime_target}", runtimeTarget == null ? "" : runtimeTarget);
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


    private static DAI_JsonBlockEntity blockEntity(ServerLevel level, ServerPlayer actor, String rawTarget) {
        BlockPos pos = parseBlockPos(actor, rawTarget);
        if (pos == null || !level.hasChunkAt(pos)) return null;
        return level.getBlockEntity(pos) instanceof DAI_JsonBlockEntity blockEntity ? blockEntity : null;
    }

    private static boolean mutateBlockEntity(
            ServerLevel level, ServerPlayer actor, String rawTarget, String key, String operation, DAI_StateValue value
    ) {
        DAI_JsonBlockEntity blockEntity = blockEntity(level, actor, rawTarget);
        if (blockEntity == null || key == null || key.isBlank()) return false;
        switch (operation) {
            case "add" -> blockEntity.addNumber(key, value.numberValue());
            case "toggle" -> blockEntity.toggleBoolean(key);
            case "clear" -> blockEntity.setState(key, DAI_StateValue.missing());
            default -> blockEntity.setState(key, value);
        }
        return true;
    }

    private static boolean setBlockEntitySlot(
            ServerLevel level, ServerPlayer actor, String rawTarget, int slot, String rawItem, int count
    ) {
        DAI_JsonBlockEntity blockEntity = blockEntity(level, actor, rawTarget);
        if (blockEntity == null || slot < 0 || slot >= blockEntity.getContainerSize()) return false;
        Identifier id = Identifier.tryParse(normalize(rawItem));
        Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
        if (item == null) return false;
        blockEntity.setItem(slot, new ItemStack(item, Math.max(1, count)));
        return true;
    }

    private static boolean clearBlockEntitySlot(ServerLevel level, ServerPlayer actor, String rawTarget, int slot) {
        DAI_JsonBlockEntity blockEntity = blockEntity(level, actor, rawTarget);
        if (blockEntity == null || slot < 0 || slot >= blockEntity.getContainerSize()) return false;
        blockEntity.setItem(slot, ItemStack.EMPTY);
        return true;
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



    private static boolean setItemComponent(ServerPlayer actor, String componentId, String target, String argumentsJson) {
        DAI_ActionArguments arguments = DAI_ActionArguments.fromJson(argumentsJson);
        var value = arguments.element("value");
        if (value == null) return false;
        return DAI_ItemComponentRuntime.set(
                DAI_ItemComponentRuntime.resolveStack(actor, target), componentId, value, actor.level().registryAccess()
        );
    }

    private static boolean copyItemComponent(ServerPlayer actor, String componentId, String target, String argumentsJson) {
        DAI_ActionArguments arguments = DAI_ActionArguments.fromJson(argumentsJson);
        String source = arguments.string("source", "mainhand");
        return DAI_ItemComponentRuntime.copy(
                DAI_ItemComponentRuntime.resolveStack(actor, source),
                DAI_ItemComponentRuntime.resolveStack(actor, target),
                componentId
        );
    }

    private static DAI_StateValue stateValue(String type, String text, double number) {
        return switch (normalize(type)) {
            case "number" -> DAI_StateValue.number(number);
            case "string" -> DAI_StateValue.string(text);
            default -> DAI_StateValue.bool(parseBoolean(text, false));
        };
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

    private static int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw == null ? "" : raw.trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
