package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.reactions.DAI_ReactionDispatchSession;
import io.github.j12h36h.dai.client.reactions.DAI_ReactionDispatcher;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventRegistry;
import io.github.j12h36h.dai.reactions.DAI_ReactionOutcome;
import io.github.j12h36h.dai.reactions.DAI_ReactionPhase;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class Mixin_MultiPlayerGameMode {

    @Unique
    private DAI_ReactionDispatchSession dai$attackReactionSession;

    @Unique
    private DAI_ReactionDispatchSession dai$useBlockReactionSession;

    @Unique
    private DAI_ReactionDispatchSession dai$useItemReactionSession;

    @Unique
    private DAI_ReactionDispatchSession dai$interactEntityReactionSession;

    @Unique
    private DAI_ReactionDispatchSession dai$breakBlockReactionSession;

    @Inject(
            method = "attack",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dai$beforeAttackEntity(
            Player player,
            Entity target,
            CallbackInfo callback
    ) {

        dai$attackReactionSession =
                DAI_ReactionDispatcher.begin(
                        DAI_ReactionEventRegistry.PLAYER_ATTACK_ENTITY,
                        target
                );

        if (dai$attackReactionSession == null) {
            return;
        }

        DAI_ReactionOutcome pre =
                dai$attackReactionSession.fire(
                        DAI_ReactionPhase.PRE
                );

        if (pre.stopsUnderlyingEvent()) {

            dai$finishSuppressedAttack(
                    callback
            );

            return;
        }

        DAI_ReactionOutcome during =
                dai$attackReactionSession.fire(
                        DAI_ReactionPhase.DURING
                );

        if (during.stopsUnderlyingEvent()) {

            dai$finishSuppressedAttack(
                    callback
            );
        }
    }

    @Inject(
            method = "attack",
            at = @At("RETURN")
    )
    private void dai$afterAttackEntity(
            Player player,
            Entity target,
            CallbackInfo callback
    ) {

        if (dai$attackReactionSession == null) {
            return;
        }

        dai$attackReactionSession.fire(
                DAI_ReactionPhase.POST
        );

        dai$attackReactionSession.flush();

        dai$attackReactionSession =
                null;
    }

    @Inject(
            method = "startDestroyBlock",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dai$beforeStartBreakBlock(
            BlockPos pos,
            Direction direction,
            CallbackInfoReturnable<Boolean> callback
    ) {
        Player player = Minecraft.getInstance().player;
        dai$breakBlockReactionSession = DAI_ReactionDispatcher.begin(
                DAI_ReactionEventRegistry.PLAYER_START_BREAK_BLOCK,
                null,
                pos,
                dai$itemId(player, InteractionHand.MAIN_HAND)
        );
        if (dai$shouldSuppress(dai$breakBlockReactionSession)) {
            dai$breakBlockReactionSession.flush();
            dai$breakBlockReactionSession = null;
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = "startDestroyBlock",
            at = @At("RETURN")
    )
    private void dai$afterStartBreakBlock(
            BlockPos pos,
            Direction direction,
            CallbackInfoReturnable<Boolean> callback
    ) {
        dai$finish(dai$breakBlockReactionSession);
        dai$breakBlockReactionSession = null;
    }

    @Inject(
            method = "useItemOn",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dai$beforeUseBlock(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> callback
    ) {
        dai$useBlockReactionSession = DAI_ReactionDispatcher.begin(
                DAI_ReactionEventRegistry.PLAYER_USE_BLOCK,
                null,
                hit.getBlockPos(),
                dai$itemId(player, hand)
        );
        if (dai$shouldSuppress(dai$useBlockReactionSession)) {
            dai$useBlockReactionSession.flush();
            dai$useBlockReactionSession = null;
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(
            method = "useItemOn",
            at = @At("RETURN")
    )
    private void dai$afterUseBlock(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> callback
    ) {
        dai$finish(dai$useBlockReactionSession);
        dai$useBlockReactionSession = null;
    }

    @Inject(
            method = "useItem",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dai$beforeUseItem(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> callback
    ) {
        dai$useItemReactionSession = DAI_ReactionDispatcher.begin(
                DAI_ReactionEventRegistry.PLAYER_USE_ITEM,
                null,
                null,
                dai$itemId(player, hand)
        );
        if (dai$shouldSuppress(dai$useItemReactionSession)) {
            dai$useItemReactionSession.flush();
            dai$useItemReactionSession = null;
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(
            method = "useItem",
            at = @At("RETURN")
    )
    private void dai$afterUseItem(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> callback
    ) {
        dai$finish(dai$useItemReactionSession);
        dai$useItemReactionSession = null;
    }

    @Inject(
            method = "interact",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dai$beforeInteractEntity(
            Player player,
            Entity target,
            EntityHitResult hit,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> callback
    ) {
        dai$interactEntityReactionSession = DAI_ReactionDispatcher.begin(
                DAI_ReactionEventRegistry.PLAYER_INTERACT_ENTITY,
                target,
                null,
                dai$itemId(player, hand)
        );
        if (dai$shouldSuppress(dai$interactEntityReactionSession)) {
            dai$interactEntityReactionSession.flush();
            dai$interactEntityReactionSession = null;
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(
            method = "interact",
            at = @At("RETURN")
    )
    private void dai$afterInteractEntity(
            Player player,
            Entity target,
            EntityHitResult hit,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> callback
    ) {
        dai$finish(dai$interactEntityReactionSession);
        dai$interactEntityReactionSession = null;
    }

    @Unique
    private String dai$itemId(Player player, InteractionHand hand) {
        if (player == null || hand == null) return "";
        var stack = player.getItemInHand(hand);
        if (stack == null || stack.isEmpty()) return "";
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    @Unique
    private boolean dai$shouldSuppress(DAI_ReactionDispatchSession session) {
        if (session == null) return false;
        DAI_ReactionOutcome pre = session.fire(DAI_ReactionPhase.PRE);
        if (pre.stopsUnderlyingEvent()) return true;
        DAI_ReactionOutcome during = session.fire(DAI_ReactionPhase.DURING);
        return during.stopsUnderlyingEvent();
    }

    @Unique
    private void dai$finish(DAI_ReactionDispatchSession session) {
        if (session == null) return;
        session.fire(DAI_ReactionPhase.POST);
        session.flush();
    }

    @Unique
    private void dai$finishSuppressedAttack(
            CallbackInfo callback
    ) {

        dai$attackReactionSession.flush();

        dai$attackReactionSession =
                null;

        callback.cancel();
    }
}
