package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.combat.DAI_MusashiDirectionalCombat;
import io.github.j12h36h.dai.client.logics.DAI_CreativeInputState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class Mixin_Minecraft {

    /*
     * Minecraft 1.21.9+ moved the global modifier queries from Screen to
     * Minecraft. Keep Ctrl artificially down only for the short window in
     * which vanilla consumes DAI's queued Pick Block key click.
     */
    @Inject(
            method = "hasControlDown",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void dai$forceControlDown(
            CallbackInfoReturnable<Boolean> callback
    ) {

        if (DAI_CreativeInputState.forceControlModifier()) {
            callback.setReturnValue(true);
        }
    }

    /*
     * Musashi blades use LMB as draw/aim/release input. Cancel vanilla's
     * attack pipeline while leaving keyAttack physically down so the resource
     * pack can still select its drawn-ready model.
     */
    @Inject(
            method = "startAttack",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dai$musashiStartAttack(
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (DAI_MusashiDirectionalCombat.interceptVanillaAttack()) {
            callback.setReturnValue(false);
            callback.cancel();
        }
    }

    /* Stop held LMB from falling through into vanilla block breaking. */
    @Inject(
            method = "continueAttack",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dai$musashiContinueAttack(
            boolean leftClick,
            CallbackInfo callback
    ) {
        if (DAI_MusashiDirectionalCombat.interceptVanillaAttack()) {
            callback.cancel();
        }
    }

    /*
     * RMB is the half-draw diagonal guard for Musashi blades, so suppress the
     * normal use/interact pipeline while the custom weapon is held.
     */
    @Inject(
            method = "startUseItem",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dai$musashiStartUseItem(
            CallbackInfo callback
    ) {
        if (DAI_MusashiDirectionalCombat.interceptVanillaUse()) {
            callback.cancel();
        }
    }
}
