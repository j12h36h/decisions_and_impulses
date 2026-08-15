package io.github.j12h36h.dai.client.logics.condition;

import io.github.j12h36h.dai.client.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;

public record DAI_ConditionContext(
        Minecraft minecraft,
        LocalPlayer player,
        ClientLevel level,
        MultiPlayerGameMode gameMode,
        Entity target,
        HitResult hitResult
) {

    public DAI_ConditionContext {

        if (minecraft == null) {
            throw new IllegalArgumentException(
                    "Minecraft instance cannot be null."
            );
        }
    }

    public static DAI_ConditionContext capture() {

        Minecraft minecraft =
                Minecraft.getInstance();

        return new DAI_ConditionContext(
                minecraft,
                minecraft.player,
                minecraft.level,
                minecraft.gameMode,
                DAI_TargetState.selected(),
                minecraft.hitResult
        );
    }

    public boolean hasPlayer() {
        return player != null;
    }

    public boolean hasLevel() {
        return level != null;
    }

    public boolean hasTarget() {
        return target != null;
    }

    public boolean hasHitResult() {
        return hitResult != null;
    }
}
