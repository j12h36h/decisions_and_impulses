package io.github.j12h36h.dai.client.packs;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Serializes experience-owned client resource-pack transitions.
 *
 * The experience launch state is prepared first, then this class updates both
 * DAI managed packs and ordinary file/... companion packs, performs one client
 * resource reload, and only then lets the world-open flow continue. This makes
 * the selected experience visuals live before its integrated server/world is
 * opened instead of racing the login/loading sequence.
 */
public final class DAI_ExperienceResourcePackLifecycle {

    private static int generation;

    private DAI_ExperienceResourcePackLifecycle() {}

    /**
     * Applies the current pending/active experience resource-pack context and
     * invokes {@code afterReady} on the Minecraft thread once the reload has
     * completed. If no selection changed, launch continues immediately.
     */
    public static void applyBeforeWorldLaunch(Runnable afterReady) {
        if (afterReady == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            afterReady.run();
            return;
        }

        final int token = ++generation;
        minecraft.execute(() -> {
            boolean changed = false;
            boolean repositoryChanged = false;
            try {
                // Snapshot the player's pre-world pack stack before the
                // experience mutates it, then layer the world's own requested
                // packs on top of the experience-required stack.
                DAI_WorldResourcePackSelection.captureBaselineForArmed(minecraft);
                Set<String> worldRequested = DAI_WorldResourcePackSelection.requestedForArmed();
                changed |= DAI_ManagedResourcePackPreferences.reconcileLiveSelectionNow(minecraft);
                changed |= DAI_CompanionResourcePackPreferences.reconcileLiveSelectionNow(minecraft);
                changed |= DAI_WorldResourcePackSelection.applyArmedSelectionNow(minecraft);

                LinkedHashSet<String> priority = new LinkedHashSet<>();
                priority.addAll(DAI_ManagedResourcePackPreferences.desiredPackIdsForCurrentContext());
                priority.addAll(DAI_CompanionResourcePackPreferences.desiredPackIdsForCurrentContext());
                priority.addAll(worldRequested);
                repositoryChanged = DAI_ResourcePackRepositorySync.syncFromOptions(
                        minecraft,
                        priority
                );
            } catch (RuntimeException exception) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Could not prepare the experience resource-pack stack before world launch.",
                        exception
                );
            }

            if (!changed && !repositoryChanged) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Experience resource-pack stack ready before target world launch (optionsChanged={}, repositoryChanged={}).",
                        changed, repositoryChanged
                );
                afterReady.run();
                return;
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Reloading selected experience resource packs before target world launch."
            );
            minecraft.reloadResourcePacks().whenComplete((ignored, error) -> minecraft.execute(() -> {
                if (token != generation) {
                    DAI_Core.LOGGER.debug(
                            "<DAI>: Ignoring superseded experience resource-pack launch continuation."
                    );
                    return;
                }
                if (error != null) {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Experience resource-pack reload failed before world launch; continuing with the selected options stack.",
                            error
                    );
                } else {
                    DAI_Core.LOGGER.info(
                            "<DAI>: Experience resource packs are live; continuing target world launch."
                    );
                }
                afterReady.run();
            }));
        });
    }

    /**
     * Immediately removes the just-unloaded experience's owned packs from the
     * live selection (or switches directly to a new pending experience), then
     * reloads client resources. Non-DAI/user-selected packs are untouched.
     */
    public static void applyAfterWorldUnload() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        final int token = ++generation;
        DAI_WorldResourcePackSelection.beginRestoreBarrier();
        minecraft.execute(() -> {
            boolean changed = false;
            boolean repositoryChanged = false;
            try {
                // Restore the player's pre-world stack first. Reconcile DAI's
                // global policy afterwards so a stale snapshot cannot override
                // pack installs/settings changed while the world was open.
                changed |= DAI_WorldResourcePackSelection.restoreLiveSelectionNow(minecraft);
                changed |= DAI_ManagedResourcePackPreferences.reconcileLiveSelectionNow(minecraft);
                changed |= DAI_CompanionResourcePackPreferences.reconcileLiveSelectionNow(minecraft);

                LinkedHashSet<String> priority = new LinkedHashSet<>();
                priority.addAll(DAI_ManagedResourcePackPreferences.desiredPackIdsForCurrentContext());
                priority.addAll(DAI_CompanionResourcePackPreferences.desiredPackIdsForCurrentContext());
                repositoryChanged = DAI_ResourcePackRepositorySync.syncFromOptions(
                        minecraft,
                        priority
                );
            } catch (RuntimeException exception) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Could not clear the unloaded experience resource-pack stack.",
                        exception
                );
            }
            if (!changed && !repositoryChanged) {
                DAI_WorldResourcePackSelection.finishRestoreBarrier();
                return;
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Experience world unloaded; reloading client resources after removing its owned resource packs."
            );
            minecraft.reloadResourcePacks().whenComplete((ignored, error) -> minecraft.execute(() -> {
                DAI_WorldResourcePackSelection.finishRestoreBarrier();
                if (token != generation) return;
                if (error != null) {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Resource reload failed while clearing the unloaded experience pack stack.",
                            error
                    );
                } else {
                    DAI_Core.LOGGER.info(
                            "<DAI>: Unloaded experience resource packs are disabled."
                    );
                }
            }));
        });
    }
}
