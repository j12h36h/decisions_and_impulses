package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.content.DAI_ParticleSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server-owned emitter for dai_particles. Actual sprites/options remain resource-pack/particle-id driven. */
public final class DAI_ParticleRuntime {
    private DAI_ParticleRuntime() {}

    public static boolean emit(ServerPlayer actor, String rawId) {
        if (actor == null) return false;
        var entry = DAI_ContentRegistry.get(rawId);
        if (entry == null || entry.kind() != DAI_ContentKind.PARTICLE) return false;
        String particleId = entry.definition().registryBacked()
                ? entry.id().toString()
                : entry.definition().carrier();
        if (particleId == null || particleId.isBlank()) return false;
        DAI_ParticleSettings p = entry.definition().particle();
        String mode = p.force() ? "force" : "normal";
        Vec3 c = actor.position().add(0, 1.0, 0);

        switch (p.shape()) {
            case "ring", "circle" -> {
                int points = Math.max(4, p.count());
                for (int i = 0; i < points; i++) {
                    double angle = Math.PI * 2.0 * i / points;
                    particle(actor, particleId, c.add(Math.cos(angle) * p.radius(), 0, Math.sin(angle) * p.radius()), 1, 0, 0, 0, p.speed(), mode);
                }
            }
            case "sphere" -> {
                int points = Math.max(6, p.count());
                double golden = Math.PI * (3.0 - Math.sqrt(5.0));
                for (int i = 0; i < points; i++) {
                    double y = 1.0 - (i / (double)(points - 1)) * 2.0;
                    double radius = Math.sqrt(Math.max(0, 1.0 - y * y));
                    double theta = golden * i;
                    particle(actor, particleId, c.add(Math.cos(theta)*radius*p.radius(), y*p.radius(), Math.sin(theta)*radius*p.radius()), 1,0,0,0,p.speed(),mode);
                }
            }
            default -> particle(actor, particleId, c, p.count(), p.spreadX(), p.spreadY(), p.spreadZ(), p.speed(), mode);
        }
        DAI_RuntimeDispatch.contentEvent(actor, entry, "emit");
        return true;
    }

    private static void particle(ServerPlayer actor, String id, Vec3 pos, int count,
                                 double dx, double dy, double dz, double speed, String mode) {
        DAI_RuntimeDispatch.dispatch(actor, "command:particle " + id + " " + pos.x + " " + pos.y + " " + pos.z
                + " " + dx + " " + dy + " " + dz + " " + speed + " " + count + " " + mode + " @a");
    }
}
