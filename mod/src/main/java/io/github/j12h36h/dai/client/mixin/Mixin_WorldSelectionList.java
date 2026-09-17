package io.github.j12h36h.dai.client.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps DAI's reserved shell save out of every vanilla single-player world list.
 *
 * The shell remains a real Minecraft save because DAI uses it as the backing
 * ClientLevel for the 3-D menu universe, but it is engine state rather than a
 * player-created world and must never be presented as something the player can
 * join, edit, recreate, or delete.
 */
@Mixin(WorldSelectionList.class)
public abstract class Mixin_WorldSelectionList {

    private static final String DAI_SHELL_WORLD_PREFIX = "DAI_Engine_Shell";

    @Inject(method = "filterAccepts", at = @At("HEAD"), cancellable = true)
    private void dai$hideInternalShellWorld(
            String search,
            LevelSummary summary,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (summary == null) {
            return;
        }

        String levelId = summary.getLevelId();
        if (isDaiInternalShellWorld(levelId)) {
            cir.setReturnValue(false);
        }
    }

    private static boolean isDaiInternalShellWorld(String levelId) {
        if (levelId == null || levelId.isBlank()) {
            return false;
        }

        /*
         * Exact name covers the normal reserved shell. Prefix matching also
         * covers DAI's collision-safe / schema-refresh variants, e.g.
         * DAI_Engine_Shell_2 or DAI_Engine_Shell_schema2.
         *
         * The prefix is reserved for DAI Engine internal worlds.
         */
        // Reserve the whole prefix so Minecraft-generated names such as
        // "DAI_Engine_Shell (3)" can never leak into player-facing lists.
        return levelId.regionMatches(
                true,
                0,
                DAI_SHELL_WORLD_PREFIX,
                0,
                DAI_SHELL_WORLD_PREFIX.length()
        );
    }
}
