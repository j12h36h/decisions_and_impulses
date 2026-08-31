package io.github.j12h36h.dai.client.logics.input;

import io.github.j12h36h.dai.client.creator.DAI_CreatorRuntime;
import io.github.j12h36h.dai.client.network.DAI_ServerBridge;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.network.DAI_VehicleInputPayload;
import io.github.j12h36h.dai.physics.DAI_PhysicsProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** Publishes physical input while a vehicle or DAI physics profile owns movement. */
public final class DAI_VehicleInputBridge {

    private DAI_VehicleInputBridge() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        DAI_PhysicsProfile physics = DAI_CreatorRuntime.testPhysics(minecraft.player);
        if (physics == null) physics = DAI_PhysicsProfile.activeFor(minecraft.player);
        if (!minecraft.player.isPassenger() && physics == null) return;

        float forward = 0.0F;
        float strafe = 0.0F;
        if (minecraft.options.keyUp.isDown()) forward += 1.0F;
        if (minecraft.options.keyDown.isDown()) forward -= 1.0F;
        if (minecraft.options.keyLeft.isDown()) strafe += 1.0F;
        if (minecraft.options.keyRight.isDown()) strafe -= 1.0F;

        DAI_ServerBridge.send(new DAI_VehicleInputPayload(
                forward,
                strafe,
                minecraft.options.keyJump.isDown(),
                minecraft.options.keyShift.isDown(),
                minecraft.options.keySprint.isDown(),
                minecraft.options.keyAttack.isDown(),
                minecraft.options.keyUse.isDown(),
                minecraft.player.getYRot(),
                minecraft.player.getXRot()
        ));
    }

    /**
     * Returns true when the mounted DAI vehicle explicitly owns LMB/RMB.
     * This lets authored vehicles use the mouse without vanilla attack/use
     * side effects while leaving every existing vehicle unchanged.
     */
    public static boolean ownsMouseControls() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.isPassenger()) return false;

        Entity vehicle = minecraft.player.getVehicle();
        if (vehicle == null) return false;

        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType());
        if (entityId == null) return false;

        for (var entry : DAI_GameCustomizationRegistry.entries(DAI_GameCustomizationKind.VEHICLE).values()) {
            var definition = entry.definition();
            if (!entityId.toString().equals(definition.carrier())) continue;
            return definition.flag("mouse_controls", false);
        }
        return false;
    }
}
