package io.github.j12h36h.dai.client.particle;

import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.content.DAI_ParticleSettings;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

/** Client particle whose behavior is sourced from the reloadable dai_particles definition. */
public final class DAI_JsonParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final Layer layer;
    private final boolean fullBright;

    public DAI_JsonParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xd,
            double yd,
            double zd,
            SpriteSet sprites,
            String contentId
    ) {
        super(level, x, y, z, xd, yd, zd, sprites.first());
        this.sprites = sprites;
        this.layer = Layer.bySprite(this.sprite);

        var entry = DAI_ContentRegistry.get(contentId);
        DAI_ParticleSettings settings = entry == null
                ? DAI_ParticleSettings.DEFAULT
                : entry.definition().particle();

        this.lifetime = settings.lifetime();
        this.gravity = (float) settings.gravity();
        this.friction = (float) settings.friction();
        this.hasPhysics = settings.collision();
        this.fullBright = settings.fullBright();
        this.scale((float) settings.scale());

        int rgb = settings.color();
        this.setColor(
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F
        );
        this.setAlpha((float) settings.alpha());
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) this.setSpriteFromAge(this.sprites);
    }

    @Override
    protected Layer getLayer() {
        return this.layer;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return this.fullBright ? 0x00F000F0 : super.getLightCoords(partialTick);
    }
}
