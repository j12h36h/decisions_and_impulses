package io.github.j12h36h.dai.server.network;

import io.github.j12h36h.dai.network.DAI_ServerMutationPayload;
import io.github.j12h36h.dai.network.DAI_ServerActionPayload;
import io.github.j12h36h.dai.network.DAI_VehicleInputPayload;
import io.github.j12h36h.dai.server.runtime.DAI_VehicleRuntime;
import io.github.j12h36h.dai.server.action.DAI_ServerActionExecutor;

import io.github.j12h36h.dai.attributes.DAI_NativeAttributeSupport;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.content.DAI_ContentComponents;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class DAI_ServerNetworkBootstrap {

    private DAI_ServerNetworkBootstrap() {}

    public static void initialize(IEventBus modBus) {
        modBus.addListener(DAI_ServerNetworkBootstrap::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2").optional();
        registrar.playToServer(
                DAI_ServerMutationPayload.TYPE,
                DAI_ServerMutationPayload.STREAM_CODEC,
                DAI_ServerNetworkBootstrap::handleServerMutation
        );
        registrar.playToServer(
                DAI_ServerActionPayload.TYPE,
                DAI_ServerActionPayload.STREAM_CODEC,
                DAI_ServerNetworkBootstrap::handleServerAction
        );
        registrar.playToServer(
                DAI_VehicleInputPayload.TYPE,
                DAI_VehicleInputPayload.STREAM_CODEC,
                DAI_ServerNetworkBootstrap::handleVehicleInput
        );
    }


    private static void handleVehicleInput(
            DAI_VehicleInputPayload payload,
            IPayloadContext context
    ) {
        if (context.player() instanceof ServerPlayer sender) {
            DAI_VehicleRuntime.accept(sender, payload);
        }
    }

    private static void handleServerAction(
            DAI_ServerActionPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer sender)) return;
        DAI_ServerActionExecutor.executeClientRequest(sender, payload);
    }

    private static void handleServerMutation(
            DAI_ServerMutationPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer sender)) return;

        if (!DAI_ServerAccessPolicy.allowPrivilegedClient(sender)) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Rejected privileged DAI mutation '{}' from non-admin player '{}'.",
                    payload.operation(),
                    sender.getUUID()
            );
            return;
        }

        // Server-authoritative mutations are intentionally self-scoped.
        // A client must never be able to use the generic DAI payload as an
        // arbitrary entity editor on a multiplayer server.
        if (payload.targetEntityId() >= 0 && payload.targetEntityId() != sender.getId()) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Rejected server mutation targeting entity {} from the sending player.",
                    payload.targetEntityId()
            );
            return;
        }

        LivingEntity target = sender;

        String operation = payload.operation() == null
                ? ""
                : payload.operation().trim().toLowerCase();

        switch (operation) {
            case "native_attribute_set" ->
                    DAI_NativeAttributeSupport.setBase(target, payload.attribute(), payload.value());

            case "native_attribute_modifier_add" ->
                    DAI_NativeAttributeSupport.addModifier(
                            target,
                            payload.attribute(),
                            payload.modifier(),
                            payload.value(),
                            payload.modifierOperation(),
                            payload.persistent()
                    );

            case "native_attribute_modifier_remove" ->
                    DAI_NativeAttributeSupport.removeModifier(
                            target,
                            payload.attribute(),
                            payload.modifier()
                    );

            case "status_set_health" ->
                    target.setHealth((float) payload.value());

            case "status_heal" ->
                    target.heal((float) Math.max(0.0D, payload.value()));

            case "status_damage" -> {
                if (target.level() instanceof ServerLevel level && payload.value() > 0.0D) {
                    target.hurtServer(
                            level,
                            sender.damageSources().generic(),
                            (float) payload.value()
                    );
                }
            }

            case "status_set_absorption" ->
                    target.setAbsorptionAmount((float) Math.max(0.0D, payload.value()));

            case "status_set_air" ->
                    target.setAirSupply((int) Math.round(payload.value()));

            case "status_set_fire_ticks" ->
                    target.setRemainingFireTicks((int) Math.round(payload.value()));

            case "status_set_food" -> {
                if (target instanceof ServerPlayer player) {
                    player.getFoodData().setFoodLevel((int) Math.round(payload.value()));
                }
            }

            case "content_give_carrier" -> {
                Identifier id = Identifier.tryParse(payload.attribute());
                Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
                if (item == null) {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Cannot give unknown content carrier item '{}'.",
                            payload.attribute()
                    );
                    break;
                }
                int count = Math.max(1, (int) Math.round(payload.value()));
                ItemStack stack = new ItemStack(item, count);
                if (payload.modifier() != null && !payload.modifier().isBlank()) {
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal(payload.modifier()));
                }
                if (payload.modifierOperation() != null && !payload.modifierOperation().isBlank()) {
                    if (DAI_ContentComponents.isInitialized()) {
                        stack.set(DAI_ContentComponents.CONTENT_ID.get(), payload.modifierOperation());
                    }
                    DAI_ContentRegistry.Entry content = DAI_ContentRegistry.get(payload.modifierOperation());
                    if (content != null) {
                        var definition = content.definition();
                        if (!definition.model().isBlank()) {
                            Identifier model = Identifier.tryParse(definition.model());
                            if (model != null) stack.set(DataComponents.ITEM_MODEL, model);
                        }
                        if (definition.stats().durability() > 0) {
                            stack.set(DataComponents.MAX_STACK_SIZE, 1);
                            stack.set(DataComponents.MAX_DAMAGE, definition.stats().durability());
                            stack.set(DataComponents.DAMAGE, 0);
                        } else {
                            stack.set(
                                    DataComponents.MAX_STACK_SIZE,
                                    Math.max(1, Math.min(99, definition.stats().stackSize()))
                            );
                        }
                    }
                }
                stack.setCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
                sender.getInventory().add(stack);
            }

            default -> DAI_Core.LOGGER.warn(
                    "<DAI>: Ignored unknown server mutation operation '{}'.",
                    operation
            );
        }
    }
}
