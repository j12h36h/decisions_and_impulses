package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class DAI_VolumeUtil {
    private DAI_VolumeUtil() {}

    static Vec3 center(DAI_GameCustomizationDefinition def) {
        String raw = def.target();
        if (raw != null && !raw.isBlank()) {
            String[] p = raw.trim().replace(',', ' ').split("\\s+");
            if (p.length >= 3) {
                try { return new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])); }
                catch (NumberFormatException ignored) {}
            }
        }
        return new Vec3(def.number("x", 0), def.number("y", 0), def.number("z", 0));
    }

    static AABB box(DAI_GameCustomizationDefinition def) {
        Vec3 c = center(def);
        double radius = Math.max(0.05, def.number("radius", 1.5));
        double width = Math.max(0.05, def.number("width", radius * 2.0));
        double height = Math.max(0.05, def.number("height", radius * 2.0));
        double depth = Math.max(0.05, def.number("depth", width));
        return new AABB(c.x - width / 2, c.y - height / 2, c.z - depth / 2,
                c.x + width / 2, c.y + height / 2, c.z + depth / 2);
    }

    static boolean contains(DAI_GameCustomizationDefinition def, Entity entity) {
        if (entity == null) return false;
        String shape = def.property("shape");
        if (shape.equalsIgnoreCase("sphere") || shape.equalsIgnoreCase("radius")) {
            Vec3 c = center(def);
            double r = Math.max(0.05, def.number("radius", 1.5));
            return entity.position().distanceToSqr(c) <= r * r;
        }
        return box(def).intersects(entity.getBoundingBox());
    }

    static boolean dimensionMatches(DAI_GameCustomizationDefinition def, Entity entity) {
        String dimension = def.property("dimension");
        if (dimension.isBlank() || dimension.equals("any") || dimension.equals("*")) return true;
        return entity.level().dimension().identifier().toString().equalsIgnoreCase(dimension);
    }

    static boolean requirementsPass(DAI_GameCustomizationDefinition def, Entity entity) {
        String requiredTag = def.property("required_tag");
        if (!requiredTag.isBlank() && !entity.tags().toList().contains(requiredTag)) return false;
        String forbiddenTag = def.property("forbidden_tag");
        return forbiddenTag.isBlank() || !entity.tags().toList().contains(forbiddenTag);
    }
}
