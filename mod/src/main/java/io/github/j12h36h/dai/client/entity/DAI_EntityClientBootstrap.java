package io.github.j12h36h.dai.client.entity;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.registry.DAI_DynamicRegistryBootstrap;
import io.github.j12h36h.dai.registry.DAI_RegistrySpec;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.lang.reflect.Constructor;

/** Connects custom DAI EntityTypes to supported vanilla renderer templates. */
public final class DAI_EntityClientBootstrap {

    private DAI_EntityClientBootstrap() {}

    public static void initialize(IEventBus modBus) {
        modBus.addListener(DAI_EntityClientBootstrap::registerRenderers);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (DAI_RegistrySpec spec : DAI_DynamicRegistryBootstrap.bootSpecs().values()) {
            if (spec.nativeRegistry() != DAI_RegistrySpec.NativeRegistry.ENTITY) continue;
            EntityType type = DAI_DynamicRegistryBootstrap.entityType(spec);
            if (type == null) continue;

            if (io.github.j12h36h.dai.entity.DAI_EntityTemplateRegistry.isNative(spec.carrier())) {
                event.registerEntityRenderer(type, DAI_InvisibleEntityRenderer::new);
                continue;
            }

            String rendererClass = DAI_EntityRendererTemplateRegistry.rendererClass(spec.carrier());
            if (rendererClass.isBlank()) {
                DAI_Core.LOGGER.error(
                        "<DAI>: No renderer template is available for custom entity '{}' carrier='{}'.",
                        spec.id(), spec.carrier()
                );
                continue;
            }

            event.registerEntityRenderer(type, context -> instantiate(rendererClass, context, spec.id()));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static EntityRenderer instantiate(
            String className,
            EntityRendererProvider.Context context,
            String id
    ) {
        try {
            Class<?> type = Class.forName(className);
            Constructor<?> constructor = type.getConstructor(EntityRendererProvider.Context.class);
            return (EntityRenderer) constructor.newInstance(context);
        } catch (Throwable exception) {
            throw new IllegalStateException(
                    "Could not construct vanilla renderer template '" + className + "' for " + id,
                    exception
            );
        }
    }
}
