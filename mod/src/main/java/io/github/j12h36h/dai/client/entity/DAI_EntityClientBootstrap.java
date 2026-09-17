package io.github.j12h36h.dai.client.entity;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.entity.mesh.DAI_MeshModelLibrary;
import io.github.j12h36h.dai.client.entity.mesh.DAI_NativeMeshEntityRenderer;
import io.github.j12h36h.dai.client.player.DAI_PlayerPresentationLibrary;
import io.github.j12h36h.dai.client.player.DAI_PlayerPresentationRenderer;
import io.github.j12h36h.dai.registry.DAI_DynamicRegistryBootstrap;
import io.github.j12h36h.dai.registry.DAI_RegistrySpec;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

import java.lang.reflect.Constructor;

/** Connects custom DAI EntityTypes to supported vanilla renderer templates. */
public final class DAI_EntityClientBootstrap {

    private DAI_EntityClientBootstrap() {}

    public static void initialize(IEventBus modBus) {
        modBus.addListener(DAI_EntityClientBootstrap::registerRenderers);
        modBus.addListener(DAI_EntityClientBootstrap::registerReloadListeners);
        modBus.addListener(DAI_EntityClientBootstrap::registerRenderStateModifiers);
        NeoForge.EVENT_BUS.addListener(RenderPlayerEvent.Pre.class, DAI_PlayerPresentationRenderer::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener(DAI_PlayerPresentationRenderer::onRenderHand);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (DAI_RegistrySpec spec : DAI_DynamicRegistryBootstrap.bootSpecs().values()) {
            if (spec.nativeRegistry() != DAI_RegistrySpec.NativeRegistry.ENTITY) continue;
            EntityType type = DAI_DynamicRegistryBootstrap.entityType(spec);
            if (type == null) continue;

            if (io.github.j12h36h.dai.entity.DAI_EntityTemplateRegistry.isNative(spec.carrier())) {
                event.registerEntityRenderer(
                        type,
                        context -> new DAI_NativeMeshEntityRenderer(context, spec.id(), spec.model())
                );
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


    private static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState renderState) {
                io.github.j12h36h.dai.client.player.DAI_PlayerPresentationContext.capture(avatar, renderState);
            }
        });
    }


    private static void registerReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "native_mesh_models"),
                new DAI_MeshModelLibrary()
        );
        event.addListener(
                Identifier.fromNamespaceAndPath(DAI_Core.MODID, "player_presentations"),
                new DAI_PlayerPresentationLibrary()
        );
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
