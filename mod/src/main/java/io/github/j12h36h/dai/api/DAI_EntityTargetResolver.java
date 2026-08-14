package io.github.j12h36h.dai.api;

import io.github.j12h36h.dai.menus.system.DAI_TargetState;
import io.github.j12h36h.dai.reactions.DAI_ReactionRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Resolves the small, shared target vocabulary used by extension actions. */
public final class DAI_EntityTargetResolver {

    private DAI_EntityTargetResolver() {
        // Utility class.
    }

    public static Entity resolve(String requested) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        String target = normalize(requested);
        if (target.isEmpty() || target.equals("self") || target.equals("player")) {
            return minecraft.player;
        }

        if (target.equals("selected") || target.equals("target")) {
            return DAI_TargetState.selected();
        }

        if (target.equals("reaction") || target.equals("reaction_entity")) {
            var context = DAI_ReactionRuntime.current();
            return context == null ? null : context.entity();
        }

        return DAI_ReferenceStore.resolveEntity(target);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
