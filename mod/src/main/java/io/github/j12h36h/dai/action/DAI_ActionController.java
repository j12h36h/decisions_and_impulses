package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.input.DAI_TargetController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class DAI_ActionController {

    private static boolean attackPending;

    private DAI_ActionController() {
        // Utility class.
    }

    public static void requestAttack() {
        attackPending = true;
        DAI_Core.LOGGER.debug("<DAI>: Attack requested.");
    }

    public static void tick() {
        if (!attackPending) {
            return;
        }

        attackPending = false;
        Minecraft minecraft = Minecraft.getInstance();
        Entity target = DAI_TargetController.selected();

        if (minecraft.player == null || minecraft.gameMode == null) {
            return;
        }

        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            DAI_Core.LOGGER.warn("<DAI>: Selected attack target is unavailable or not living.");
            return;
        }

        minecraft.gameMode.attack(minecraft.player, target);
        minecraft.player.swing(InteractionHand.MAIN_HAND);
        DAI_Core.LOGGER.debug("<DAI>: Attacked '{}'.", target.getName().getString());
    }

    public static void reset() {
        attackPending = false;
    }
}
