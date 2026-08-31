package io.github.j12h36h.dai.entity;

import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

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

        double localX = seat[0];
        double localY = seat[1];
        double localZ = seat[2];

        // Vehicle seats can opt into chassis pitch. This is required for bikes:
        // the rider must travel with the seat during wheelies, jumps and crashes
        // instead of remaining at a fixed world-Y above an independently pitched mesh.
        if (riding.followVehiclePitch()) {
            double[] pivot = riding.pitchPivotVector();
            double radiansX = Math.toRadians(getXRot());
            double cosX = Math.cos(radiansX);
            double sinX = Math.sin(radiansX);

            double relativeY = localY - pivot[1];
            double relativeZ = localZ - pivot[2];
            double rotatedY = relativeY * cosX - relativeZ * sinX;
            double rotatedZ = relativeY * sinX + relativeZ * cosX;

            localY = pivot[1] + rotatedY;
            localZ = pivot[2] + rotatedZ;
        }

        // Seat coordinates are authored in local entity space. Rotate X/Z by
        // vehicle yaw so multi-seat layouts stay attached to the chassis.
        double radiansY = Math.toRadians(-getYRot());
        double cosY = Math.cos(radiansY);
        double sinY = Math.sin(radiansY);
        double x = getX() + localX * cosY - localZ * sinY;
        double z = getZ() + localX * sinY + localZ * cosY;
        double y = getY() + localY;
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
