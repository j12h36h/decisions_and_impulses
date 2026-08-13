package io.github.j12h36h.dai.logics.mixin;

import io.github.j12h36h.dai.reactions.DAI_ReactionDispatchSession;
import io.github.j12h36h.dai.reactions.DAI_ReactionDispatcher;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventRegistry;
import io.github.j12h36h.dai.reactions.DAI_ReactionOutcome;
import io.github.j12h36h.dai.reactions.DAI_ReactionPhase;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class Mixin_MultiPlayerGameMode {

    @Unique
    private DAI_ReactionDispatchSession dai$attackReactionSession;

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
