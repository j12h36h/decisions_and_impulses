package io.github.j12h36h.dai.entity;

import io.github.j12h36h.dai.attributes.DAI_NativeAttributeSupport;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.registry.DAI_DynamicRegistryBootstrap;
import io.github.j12h36h.dai.registry.DAI_RegistrySpec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/** Registers JSON-defined default attributes for DAI native entity types. */
public final class DAI_EntityBootstrap {

    private DAI_EntityBootstrap() {}

    public static void initialize(IEventBus modBus) {
        modBus.addListener(DAI_EntityBootstrap::createAttributes);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void createAttributes(EntityAttributeCreationEvent event) {
        for (DAI_RegistrySpec spec : DAI_DynamicRegistryBootstrap.bootSpecs().values()) {
            if (spec.nativeRegistry() != DAI_RegistrySpec.NativeRegistry.ENTITY) continue;

            EntityType type = DAI_DynamicRegistryBootstrap.entityType(spec);
            if (type == null) continue;

            try {
                AttributeSupplier.Builder builder =
                        DAI_EntityTemplateRegistry.createDefaultAttributes(spec.carrier());
                for (var entry : spec.nativeAttributes().entrySet()) {
                    Holder<Attribute> attribute = DAI_NativeAttributeSupport.resolve(entry.getKey());
                    if (attribute != null && Double.isFinite(entry.getValue())) {
                        builder.add(attribute, entry.getValue());
                    }
                }
                event.put(type, builder.build());
                DAI_Core.LOGGER.info("<DAI>: Registered default attributes for DAI entity '{}'.", spec.id());
            } catch (RuntimeException exception) {
                DAI_Core.LOGGER.error(
                        "<DAI>: Failed to register default attributes for DAI entity '{}'.",
                        spec.id(),
                        exception
                );
            }
        }
    }
}
