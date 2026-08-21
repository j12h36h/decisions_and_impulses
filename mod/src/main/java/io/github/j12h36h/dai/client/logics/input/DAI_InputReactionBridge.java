package io.github.j12h36h.dai.client.logics.input;

import io.github.j12h36h.dai.client.reactions.DAI_ReactionDispatchSession;
import io.github.j12h36h.dai.client.reactions.DAI_ReactionDispatcher;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventRegistry;
import io.github.j12h36h.dai.reactions.DAI_ReactionPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Publishes the current physical mouse-button state to the datapack reaction
 * layer once per gameplay tick.  The event itself is observational; datapacks
 * select the desired held-state combination through input_attack_held and
 * input_use_held conditions.
 */
public final class DAI_InputReactionBridge {

    private DAI_InputReactionBridge() {
        // Utility class.
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        String itemId = "";
        var stack = minecraft.player.getMainHandItem();
        if (stack != null && !stack.isEmpty()) {
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) {
                itemId = id.toString();
            }
        }

        DAI_ReactionDispatchSession session =
                DAI_ReactionDispatcher.begin(
                        DAI_ReactionEventRegistry.PLAYER_INPUT_TICK,
                        null,
                        null,
                        itemId
                );

        if (session == null) {
            return;
        }

        session.fire(DAI_ReactionPhase.DURING);
        session.flush();
    }
}
