package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.combat.DAI_MusashiDirectionalCombat;
import io.github.j12h36h.dai.client.logics.DAI_CreativeInputState;
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
     * Universal physical LMB bridge. This runs before vanilla decides whether
     * the click attacks an entity, starts breaking a block, or simply swings
     * through empty air. Datapacks can therefore override/cancel one attack
     * input consistently without depending on vanilla interaction reach.
     *
     * Musashi keeps first claim over LMB because its directional combat owns
     * the physical key while one of its blades is held.
     */
    @Inject(
            method = "startAttack",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dai$beforeStartAttack(
            CallbackInfoReturnable<Boolean> callback
    ) {
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
        } else if (
                hit instanceof BlockHitResult blockHit
                        && hit.getType() == HitResult.Type.BLOCK
        ) {
            blockPos = blockHit.getBlockPos();
        }

        String itemId = "";
        if (minecraft.player != null) {
            var stack = minecraft.player.getMainHandItem();
            if (stack != null && !stack.isEmpty()) {
                var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null) {
                    itemId = id.toString();
                }
            }
        }

        dai$attackInputReactionSession =
                DAI_ReactionDispatcher.begin(
                        DAI_ReactionEventRegistry.PLAYER_ATTACK_INPUT,
                        entity,
                        blockPos,
                        itemId
                );

        if (dai$attackInputReactionSession == null) {
            return;
        }

        DAI_ReactionOutcome pre =
                dai$attackInputReactionSession.fire(
                        DAI_ReactionPhase.PRE
                );

        if (pre.stopsUnderlyingEvent()) {
            dai$finishSuppressedAttackInput(callback);
            return;
        }

        DAI_ReactionOutcome during =
                dai$attackInputReactionSession.fire(
                        DAI_ReactionPhase.DURING
                );

        if (during.stopsUnderlyingEvent()) {
            dai$finishSuppressedAttackInput(callback);
        }
    }

    @Inject(
            method = "startAttack",
            at = @At("RETURN")
    )
    private void dai$afterStartAttack(
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (dai$attackInputReactionSession == null) {
            return;
        }

        dai$attackInputReactionSession.fire(
                DAI_ReactionPhase.POST
        );
        dai$attackInputReactionSession.flush();
        dai$attackInputReactionSession = null;
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
        if (!leftClick) {
            dai$attackInputSuppressed = false;
            return;
        }

        if (
                dai$attackInputSuppressed
                        || DAI_MusashiDirectionalCombat.interceptVanillaAttack()
        ) {
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

    @Unique
    private void dai$finishSuppressedAttackInput(
            CallbackInfoReturnable<Boolean> callback
    ) {
        dai$attackInputReactionSession.flush();
        dai$attackInputReactionSession = null;
        dai$attackInputSuppressed = true;
        callback.setReturnValue(false);
        callback.cancel();
    }
}
