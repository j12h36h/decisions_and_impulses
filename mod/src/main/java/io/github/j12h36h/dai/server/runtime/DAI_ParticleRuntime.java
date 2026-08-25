package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.content.DAI_ParticleSettings;
import io.github.j12h36h.dai.logics.action.DAI_ActionArguments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Server-owned emitter for dai_particles. Actual sprites/options remain resource-pack/particle-id driven. */
public final class DAI_ParticleRuntime {
    private DAI_ParticleRuntime() {}

    public static boolean emit(ServerPlayer actor, String rawId) {
        return emit((LivingEntity) actor, rawId, DAI_ActionArguments.EMPTY);
    }

    public static boolean emit(ServerPlayer actor, String rawId, DAI_ActionArguments arguments) {
        return emit((LivingEntity) actor, rawId, arguments);
    }

    public static boolean emit(LivingEntity actor, String rawId, DAI_ActionArguments arguments) {
        if (actor == null) return false;
        var entry = DAI_ContentRegistry.get(rawId);
        if (entry == null || entry.kind() != DAI_ContentKind.PARTICLE) return false;
        String particleId = entry.definition().registryBacked() ? entry.id().toString() : entry.definition().carrier();
        if (particleId == null || particleId.isBlank()) return false;
        DAI_ParticleSettings p = entry.definition().particle();
        DAI_ActionArguments args = arguments == null ? DAI_ActionArguments.EMPTY : arguments;
        String mode = args.bool("force", p.force()) ? "force" : "normal";
        String audience = args.string("audience", "@a").trim();
        if (audience.isBlank()) audience = "@a";
        Vec3 c = resolveOrigin(actor, args);
        int authoredCount = Math.max(1, Math.min(4096, args.integer("count", p.count())));
        double radius = Math.max(0.0D, args.number("radius", p.radius()));
        double speed = Math.max(0.0D, args.number("speed", p.speed()));
        String shape = args.normalized("shape", p.shape());

        switch (shape) {
            case "ring", "circle" -> {
                int points = Math.max(4, authoredCount);
                for (int i = 0; i < points; i++) {
                    double angle = Math.PI * 2.0 * i / points;
                    particle(actor, particleId, c.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), 1, 0, 0, 0, speed, mode, audience);
                }
            }
            case "sphere" -> {
                int points = Math.max(6, authoredCount);
                double golden = Math.PI * (3.0 - Math.sqrt(5.0));
                for (int i = 0; i < points; i++) {
                    double y = 1.0 - (i / (double)(points - 1)) * 2.0;
                    double r = Math.sqrt(Math.max(0, 1.0 - y * y));
                    double theta = golden * i;
                    particle(actor, particleId, c.add(Math.cos(theta)*r*radius, y*radius, Math.sin(theta)*r*radius), 1,0,0,0,speed,mode,audience);
                }
            }
            default -> particle(actor, particleId, c, authoredCount,
                    args.number("spread_x", p.spreadX()), args.number("spread_y", p.spreadY()),
                    args.number("spread_z", p.spreadZ()), speed, mode, audience);
        }
        DAI_RuntimeDispatch.contentEvent(actor, entry, "emit");
        return true;
    }

    private static Vec3 resolveOrigin(LivingEntity actor, DAI_ActionArguments args) {
        String mode = args.normalized("origin", "position");
        Vec3 base = switch (mode) {
            case "eye", "eyes" -> actor.getEyePosition();
            case "center" -> actor.getBoundingBox().getCenter();
            default -> actor.position().add(0, 1.0D, 0);
        };
        double[] offset = args.vector("offset", 0.0D, 0.0D, 0.0D);
        return base.add(offset[0], offset[1], offset[2]);
    }

    private static void particle(LivingEntity actor, String id, Vec3 pos, int count,
                                 double dx, double dy, double dz, double speed, String mode, String audience) {
        DAI_RuntimeDispatch.dispatch(actor, "command:particle " + id + " " + pos.x + " " + pos.y + " " + pos.z
                + " " + dx + " " + dy + " " + dz + " " + speed + " " + count + " " + mode + " " + audience);
    }
}
