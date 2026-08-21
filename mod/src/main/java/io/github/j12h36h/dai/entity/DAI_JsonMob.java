package io.github.j12h36h.dai.entity;

import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Generic physical mob used by native JSON-defined DAI entities. */
public final class DAI_JsonMob extends PathfinderMob {

    @SuppressWarnings("unchecked")
    public DAI_JsonMob(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // Native DAI entities own their AI through JSON behavior sequences.
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        DAI_EntityRidingSettings riding = riding();
        int index = getPassengers().indexOf(passenger);
        double[] seat = riding.seat(index);
        if (seat == null) {
            super.positionRider(passenger, moveFunction);
            return;
        }

        // Seat coordinates are authored in local entity space. Rotate X/Z by
        // vehicle yaw so multi-seat layouts stay attached to the chassis.
        double radians = Math.toRadians(-getYRot());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double localX = seat[0];
        double localZ = seat[2];
        double x = getX() + localX * cos - localZ * sin;
        double z = getZ() + localX * sin + localZ * cos;
        double y = getY() + seat[1];
        moveFunction.accept(passenger, x, y, z);
    }

    @Override
    public boolean shouldRiderSit() {
        return riding().riderSit();
    }

    @Override
    public boolean canRiderInteract() {
        return riding().riderInteract();
    }

    private DAI_EntityRidingSettings riding() {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(getType());
        if (id == null) return DAI_EntityRidingSettings.DEFAULT;
        DAI_ContentRegistry.Entry entry = DAI_ContentRegistry.get(id.toString());
        return entry == null ? DAI_EntityRidingSettings.DEFAULT : entry.definition().entity().riding();
    }
}
