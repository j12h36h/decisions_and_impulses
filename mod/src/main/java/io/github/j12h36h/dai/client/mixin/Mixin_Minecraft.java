package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.combat.DAI_MusashiDirectionalCombat;
import io.github.j12h36h.dai.client.logics.DAI_CreativeInputState;
import io.github.j12h36h.dai.client.logics.input.DAI_VehicleInputBridge;
import io.github.j12h36h.dai.client.reactions.DAI_ReactionDispatchSession;
import io.github.j12h36h.dai.client.reactions.DAI_ReactionDispatcher;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventRegistry;
import io.github.j12h36h.dai.reactions.DAI_ReactionOutcome;
import io.github.j12h36h.dai.reactions.DAI_ReactionPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class Mixin_Minecraft {

    @Unique
    private DAI_ReactionDispatchSession dai$attackInputReactionSession;

    @Unique
    private boolean dai$attackInputSuppressed;

    @Inject(method = "hasControlDown", at = @At("HEAD"), cancellable = true)
    private static void dai$forceControlDown(CallbackInfoReturnable<Boolean> callback) {
        if (DAI_CreativeInputState.forceControlModifier()) callback.setReturnValue(true);
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void dai$beforeStartAttack(CallbackInfoReturnable<Boolean> callback) {
        if (DAI_VehicleInputBridge.ownsMouseControls()) {
            dai$attackInputSuppressed = true;
            callback.setReturnValue(false);
            callback.cancel();
            return;
        }

        if (DAI_MusashiDirectionalCombat.interceptVanillaAttack()) {
            callback.setReturnValue(false);
            callback.cancel();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = null;
        BlockPos blockPos = null;
        HitResult hit = minecraft.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            entity = entityHit.getEntity();
        } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            blockPos = blockHit.getBlockPos();
        }

        String itemId = "";
        if (minecraft.player != null) {
            var stack = minecraft.player.getMainHandItem();
            if (stack != null && !stack.isEmpty()) {
                var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null) itemId = id.toString();
            }
        }

        dai$attackInputReactionSession = DAI_ReactionDispatcher.begin(
                DAI_ReactionEventRegistry.PLAYER_ATTACK_INPUT,
                entity,
                blockPos,
                itemId
        );
        if (dai$attackInputReactionSession == null) return;

        DAI_ReactionOutcome pre = dai$attackInputReactionSession.fire(DAI_ReactionPhase.PRE);
        if (pre.stopsUnderlyingEvent()) {
            dai$finishSuppressedAttackInput(callback);
            return;
        }
        DAI_ReactionOutcome during = dai$attackInputReactionSession.fire(DAI_ReactionPhase.DURING);
        if (during.stopsUnderlyingEvent()) dai$finishSuppressedAttackInput(callback);
    }

    @Inject(method = "startAttack", at = @At("RETURN"))
    private void dai$afterStartAttack(CallbackInfoReturnable<Boolean> callback) {
        if (dai$attackInputReactionSession == null) return;
        dai$attackInputReactionSession.fire(DAI_ReactionPhase.POST);
        dai$attackInputReactionSession.flush();
        dai$attackInputReactionSession = null;
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void dai$continueAttack(boolean leftClick, CallbackInfo callback) {
        if (!leftClick) {
            dai$attackInputSuppressed = false;
            return;
        }
        if (DAI_VehicleInputBridge.ownsMouseControls()
                || dai$attackInputSuppressed
                || DAI_MusashiDirectionalCombat.interceptVanillaAttack()) {
            callback.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void dai$startUseItem(CallbackInfo callback) {
        if (DAI_VehicleInputBridge.ownsMouseControls()
                || DAI_MusashiDirectionalCombat.interceptVanillaUse()) {
            callback.cancel();
        }
    }

    @Unique
    private void dai$finishSuppressedAttackInput(CallbackInfoReturnable<Boolean> callback) {
        dai$attackInputReactionSession.flush();
        dai$attackInputReactionSession = null;
        dai$attackInputSuppressed = true;
        callback.setReturnValue(false);
        callback.cancel();
    }
}
