package io.github.j12h36h.dai.client.packs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Keeps Minecraft's persisted resource-pack selection in sync with DAI-owned
 * managed packs.
 *
 * DAI also exposes managed packs as required repository entries. That makes
 * them active for the current client, but vanilla only persists its visible
 * enabled-pack list when Options updates the resource-pack selection (normally
 * after leaving the Resource Packs screen). Rewriting only the two pack-list
 * fields here closes that gap without touching any unrelated option.
 */
public final class DAI_ManagedResourcePackPreferences {

    private static final String PREFIX = "dai_managed:";
    private static final String RESOURCE_PACKS = "resourcePacks";
    private static final String INCOMPATIBLE_RESOURCE_PACKS = "incompatibleResourcePacks";

    private DAI_ManagedResourcePackPreferences() {}

    /** Stable repository id used both by the pack finder and options.txt. */
    public static String packId(
            DAI_PackInstallManager.InstalledPack installed,
            DAI_PackInstallManager.InstalledComponent component
    ) {
        return PREFIX
                + safe(installed == null ? null : installed.id())
                + "/"
                + safe(component == null ? null : component.id());
    }

    /**
     * Reconciles persisted selection with the current managed-pack manifest.
     * Existing non-DAI resource-pack choices are preserved exactly.
     */
    public static synchronized void reconcileSavedSelection() {
        try {
            Set<String> desired = installedManagedPackIds();
            boolean autoEnable = DAI_Config.autoEnableManagedResourcePacks();

            Path options = gameDirectory().resolve("options.txt");
            List<String> lines = Files.isRegularFile(options)
                    ? new ArrayList<>(Files.readAllLines(options, StandardCharsets.UTF_8))
                    : new ArrayList<>();

            Selection enabled = read(lines, RESOURCE_PACKS);
            Selection incompatible = read(lines, INCOMPATIBLE_RESOURCE_PACKS);
            if (!enabled.valid() || !incompatible.valid()) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: options.txt contains a malformed resource-pack list; leaving it untouched."
                );
                return;
            }

            LinkedHashSet<String> nextEnabled = new LinkedHashSet<>(enabled.values());

            // Forget DAI ids that no longer exist (or all DAI ids when the
            // automatic behavior has been disabled by configuration).
            nextEnabled.removeIf(id -> isManaged(id)
                    && (!desired.contains(id) || !autoEnable));

            if (autoEnable) {
                nextEnabled.addAll(desired);
            }

            // A managed pack is deliberately registered as required and is
            // validated by DAI before installation. Never let an old vanilla
            // incompatibility choice keep an updated managed pack disabled.
            LinkedHashSet<String> nextIncompatible =
                    new LinkedHashSet<>(incompatible.values());
            nextIncompatible.removeIf(DAI_ManagedResourcePackPreferences::isManaged);

            boolean changed = false;
            changed |= write(lines, RESOURCE_PACKS, enabled.index(), nextEnabled);
            changed |= write(
                    lines,
                    INCOMPATIBLE_RESOURCE_PACKS,
                    incompatible.index(),
                    nextIncompatible
            );

            if (!changed) return;

            Files.createDirectories(options.getParent());
            Path temp = options.resolveSibling(options.getFileName() + ".dai.tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temp,
                        options,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (Exception atomicMoveUnavailable) {
                Files.move(temp, options, StandardCopyOption.REPLACE_EXISTING);
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Reconciled managed resource-pack selection in options.txt (enabled={}, autoEnable={}).",
                    autoEnable ? desired.size() : 0,
                    autoEnable
            );
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not persist managed resource-pack selection; required-pack registration will still be used.",
                    exception
            );
        }
    }


    /**
     * Applies the same reconciliation to Minecraft's live Options instance and
     * saves it. This prevents the client from overwriting the disk-level fix
     * with a stale in-memory list when it later exits.
     */
    public static void reconcileLiveSelection() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) return;

            minecraft.execute(() -> {
                try {
                    if (minecraft.options == null) return;

                    Set<String> desired = installedManagedPackIds();
                    boolean autoEnable = DAI_Config.autoEnableManagedResourcePacks();

                    LinkedHashSet<String> enabled =
                            new LinkedHashSet<>(minecraft.options.resourcePacks);
                    enabled.removeIf(id -> isManaged(id)
                            && (!desired.contains(id) || !autoEnable));
                    if (autoEnable) enabled.addAll(desired);

                    LinkedHashSet<String> incompatible =
                            new LinkedHashSet<>(minecraft.options.incompatibleResourcePacks);
                    incompatible.removeIf(DAI_ManagedResourcePackPreferences::isManaged);

                    List<String> nextEnabled = new ArrayList<>(enabled);
                    List<String> nextIncompatible = new ArrayList<>(incompatible);

                    boolean changed =
                            !nextEnabled.equals(minecraft.options.resourcePacks)
                                    || !nextIncompatible.equals(
                                    minecraft.options.incompatibleResourcePacks
                            );

                    if (!changed) return;

                    minecraft.options.resourcePacks = nextEnabled;
                    minecraft.options.incompatibleResourcePacks = nextIncompatible;
                    minecraft.options.save();

                    DAI_Core.LOGGER.info(
                            "<DAI>: Persisted {} managed resource pack(s) into the live Minecraft options selection.",
                            autoEnable ? desired.size() : 0
                    );
                } catch (Exception exception) {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Could not reconcile the live Minecraft resource-pack selection.",
                            exception
                    );
                }
            });
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not schedule managed resource-pack option reconciliation.",
                    exception
            );
        }
    }

    private static Set<String> installedManagedPackIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (DAI_PackInstallManager.InstalledPack installed
                : DAI_PackInstallManager.installedPacks()) {
            for (DAI_PackInstallManager.InstalledComponent component
                    : installed.components()) {
                if (!"resource_pack".equals(component.type())) continue;

                Path root = DAI_PackInstallManager.resolveManagedResourceRoot(
                        component.path()
                );
                if (root == null
                        || !Files.isDirectory(root)
                        || !Files.isRegularFile(root.resolve("pack.mcmeta"))) {
                    continue;
                }

                ids.add(packId(installed, component));
            }
        }
        return ids;
    }

    private static Selection read(List<String> lines, String key) {
        String prefix = key + ":";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith(prefix)) continue;
            ParsedList parsed = parseArray(line.substring(prefix.length()));
            return new Selection(i, parsed.values(), parsed.valid());
        }
        return new Selection(-1, List.of(), true);
    }

    private static ParsedList parseArray(String raw) {
        List<String> values = new ArrayList<>();
        try {
            JsonElement parsed = JsonParser.parseString(raw == null ? "[]" : raw.trim());
            if (!parsed.isJsonArray()) return new ParsedList(List.of(), false);
            for (JsonElement element : parsed.getAsJsonArray()) {
                if (element != null && element.isJsonPrimitive()) {
                    values.add(element.getAsString());
                }
            }
            return new ParsedList(values, true);
        } catch (Exception ignored) {
            return new ParsedList(List.of(), false);
        }
    }

    private static boolean write(
            List<String> lines,
            String key,
            int existingIndex,
            Set<String> values
    ) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        String next = key + ":" + array;

        if (existingIndex >= 0) {
            if (next.equals(lines.get(existingIndex))) return false;
            lines.set(existingIndex, next);
            return true;
        }

        lines.add(next);
        return true;
    }

    private static boolean isManaged(String id) {
        return id != null && id.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    private static String safe(String value) {
        if (value == null) return "pack";
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace(':', '_')
                .replaceAll("[^a-z0-9._/-]", "_");
        return normalized.isBlank() ? "pack" : normalized;
    }

    private static Path gameDirectory() {
        Path config = FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
        Path parent = config.getParent();
        return parent == null
                ? Path.of(".").toAbsolutePath().normalize()
                : parent;
    }

    private record Selection(int index, List<String> values, boolean valid) {}

    private record ParsedList(List<String> values, boolean valid) {}
}
