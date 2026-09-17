package io.github.j12h36h.dai.client.packs;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Makes Minecraft's live PackRepository match the resource-pack ids already
 * chosen in Options before a world is opened.
 *
 * Updating Options alone is not sufficient for an already-running client:
 * Minecraft reads the persisted list into the repository during startup, while
 * a later reload preserves the repository's current selected ids. DAI world
 * transitions therefore explicitly rebuild and select the desired pack stack
 * immediately before the resource reload/world-open barrier.
 */
public final class DAI_ResourcePackRepositorySync {

    private DAI_ResourcePackRepositorySync() {}

    /**
     * Rebuilds the client pack repository, selects the current Options pack
     * list, and promotes DAI/world-requested packs to highest resource priority.
     *
     * PackRepository resource order is low -> high priority, so promoted ids
     * are appended after the normal user list. Required vanilla/mod packs are
     * inserted by PackRepository itself when setSelected rebuilds the stack.
     */
    public static boolean syncFromOptions(
            Minecraft minecraft,
            Collection<String> promoteToHighestPriority
    ) {
        if (minecraft == null || minecraft.options == null) return false;

        try {
            PackRepository repository = minecraft.getResourcePackRepository();
            if (repository == null) return false;

            List<String> before = List.copyOf(repository.getSelectedIds());

            // Re-run all repository sources first. This is important for DAI
            // managed packs whose availability depends on the pending/active
            // experience context.
            repository.reload();
            Set<String> available = new LinkedHashSet<>(repository.getAvailableIds());

            LinkedHashSet<String> desired = new LinkedHashSet<>();
            for (String id : minecraft.options.resourcePacks) {
                if (id == null || id.isBlank()) continue;
                if (available.contains(id)) desired.add(id);
                else DAI_Core.LOGGER.debug(
                        "<DAI>: Resource pack '{}' is selected in Options but is not currently available.",
                        id
                );
            }

            LinkedHashSet<String> promoted = new LinkedHashSet<>();
            if (promoteToHighestPriority != null) {
                for (String id : promoteToHighestPriority) {
                    if (id == null || id.isBlank() || !available.contains(id)) continue;
                    promoted.add(id);
                }
            }

            // Remove then append so requested world/experience packs are the
            // final/highest-priority entries in the explicit selection.
            for (String id : promoted) desired.remove(id);
            desired.addAll(promoted);

            List<String> explicitSelection = new ArrayList<>(desired);
            repository.setSelected(explicitSelection);
            List<String> after = List.copyOf(repository.getSelectedIds());

            // Keep Options in the exact same explicit order. This also prevents
            // a later Options#save from writing an old ordering back to disk.
            if (!explicitSelection.equals(minecraft.options.resourcePacks)) {
                minecraft.options.resourcePacks = new ArrayList<>(explicitSelection);
                minecraft.options.save();
            }

            boolean changed = !before.equals(after);
            if (changed || !promoted.isEmpty()) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Prepared live resource-pack stack before world transition; promoted={} selected={}",
                        promoted.isEmpty() ? "none" : String.join(", ", promoted),
                        after.isEmpty() ? "none" : String.join(", ", after)
                );
            }
            return changed;
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not synchronize the live resource-pack repository before world transition.",
                    exception
            );
            return false;
        }
    }
}
