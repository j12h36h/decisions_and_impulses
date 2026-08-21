package io.github.j12h36h.dai.client.particle;

import io.github.j12h36h.dai.registry.DAI_DynamicRegistryBootstrap;
import io.github.j12h36h.dai.registry.DAI_RegistrySpec;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.jspecify.annotations.Nullable;

/** Binds early-registered DAI particle types to generated particle descriptions. */
public final class DAI_ParticleClientBootstrap {
    private DAI_ParticleClientBootstrap() {}

    public static void initialize(IEventBus modBus) {
        modBus.addListener(DAI_ParticleClientBootstrap::registerProviders);
    }

    private static void registerProviders(RegisterParticleProvidersEvent event) {
        for (DAI_RegistrySpec spec : DAI_DynamicRegistryBootstrap.bootSpecs().values()) {
            if (spec.nativeRegistry() != DAI_RegistrySpec.NativeRegistry.PARTICLE) continue;
            SimpleParticleType type = DAI_DynamicRegistryBootstrap.particleType(spec);
            if (type == null) continue;
            event.registerSpriteSet(type, sprites -> new Provider(sprites, spec.id()));
        }
    }

    private record Provider(SpriteSet sprites, String contentId) implements ParticleProvider<SimpleParticleType> {
        @Override
        public @Nullable Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xd,
                double yd,
                double zd,
                RandomSource random
        ) {
            return new DAI_JsonParticle(level, x, y, z, xd, yd, zd, sprites, contentId);
        }
    }
}
