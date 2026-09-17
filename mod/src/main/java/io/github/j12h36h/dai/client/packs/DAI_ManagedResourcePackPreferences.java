package io.github.j12h36h.dai.client.packs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.client.experience.DAI_ExperienceRuntime;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.experience.DAI_ExperienceLaunchState;
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
 * DAI exposes managed packs as optional repository entries and keeps their
 * vanilla selection synchronized automatically. Optional registration is
 * important because an Experience Pack may disable or whitelist ADDON resource
 * companions without affecting unrelated player-selected resource packs.
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

            // Managed packs are validated by DAI before installation. Never
            // let an old vanilla incompatibility choice keep an allowed,
            // updated managed pack disabled.
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
                    "<DAI>: Could not persist managed resource-pack selection; repository registration will still be available.",
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
            minecraft.execute(() -> reconcileLiveSelectionNow(minecraft));
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not schedule managed resource-pack option reconciliation.",
                    exception
            );
        }
    }

    /**
     * Re-applies the active/pending experience ADDON policy to managed resource
     * packs and reloads client resources. This keeps combo ADDON packs from
     * leaking visual overrides into experiences that disable or do not
     * whitelist them.
     */
    public static void applyExperiencePolicyAndReload() {
        reconcileSavedSelection();

        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) return;
            minecraft.execute(() -> {
                boolean changed = reconcileLiveSelectionNow(minecraft);
                if (!changed) return;
                minecraft.reloadResourcePacks().whenComplete((ignored, error) -> {
                    if (error != null) {
                        DAI_Core.LOGGER.warn(
                                "<DAI>: Resource reload failed while applying experience ADDON policy.",
                                error
                        );
                    } else {
                        DAI_Core.LOGGER.info(
                                "<DAI>: Applied experience ADDON resource-pack policy."
                        );
                    }
                });
            });
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not schedule experience ADDON resource-pack policy reload.",
                    exception
            );
        }
    }

    static boolean reconcileLiveSelectionNow(Minecraft minecraft) {
        try {
            if (minecraft == null || minecraft.options == null) return false;

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

            if (!changed) return false;

            minecraft.options.resourcePacks = nextEnabled;
            minecraft.options.incompatibleResourcePacks = nextIncompatible;
            minecraft.options.save();

            DAI_Core.LOGGER.info(
                    "<DAI>: Persisted {} managed resource pack(s) into the live Minecraft options selection.",
                    autoEnable ? desired.size() : 0
            );
            return true;
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not reconcile the live Minecraft resource-pack selection.",
                    exception
            );
            return false;
        }
    }

    /**
     * True when this installed public pack belongs in the current visual stack.
     *
     * Experience Pack companions are mutually isolated: installing ten games
     * does not globally enable all ten resource packs. Only the pack owning the
     * pending/active experience is selected. ADDON companions retain the
     * experience's allow/whitelist policy and remain available to ordinary
     * Minecraft + DAI worlds when no authored experience is active.
     */
    public static boolean allowedForCurrentExperience(DAI_PackInstallManager.InstalledPack installed) {
        if (installed == null) return false;

        DAI_ExperienceDefinition experience = null;
        DAI_ExperienceLaunchState.Pending pending = DAI_ExperienceLaunchState.pending();
        if (pending != null) experience = pending.definition();
        if (experience == null) experience = DAI_ExperienceRuntime.active();

        if (installed.isExperiencePack()) {
            return experience != null && (installed.ownsExperience(experience.id())
                    || identityFallbackOwnsExperience(installed, experience.id()));
        }

        if (!installed.isAddon()) {
            // Preserve compatibility for old manifest entries that predate the
            // public Experience Pack / Addon classification.
            return true;
        }

        if (experience == null) return true;

        DAI_ExperienceDefinition.AddonPolicy policy = experience.addons();
        return policy != null && policy.allows(installed.id());
    }

    /**
     * Older installed manifests can predate experience_ids. Keep official
     * Experience Packs usable by matching their stable public-pack id against
     * the authored experience namespace/path. This is deliberately a fallback;
     * explicit scanned ownership always wins when available.
     */
    private static boolean identityFallbackOwnsExperience(
            DAI_PackInstallManager.InstalledPack installed,
            String experienceId
    ) {
        if (installed == null || experienceId == null || experienceId.isBlank()) return false;
        String packToken = localToken(installed.id());
        String normalized = experienceId.trim().toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        String namespace = colon > 0 ? normalized.substring(0, colon) : normalized;
        String path = colon >= 0 && colon + 1 < normalized.length()
                ? normalized.substring(colon + 1)
                : normalized;
        String namespaceToken = localToken(namespace);
        String pathToken = localToken(path);
        if (packToken.length() < 4) return false;
        return packToken.equals(namespaceToken)
                || packToken.equals(pathToken)
                || pathToken.startsWith(packToken)
                || pathToken.endsWith(packToken);
    }

    private static String localToken(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int colon = value.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < value.length()) value = value.substring(colon + 1);
        return value.replaceAll("[^a-z0-9]+", "");
    }

    /** Pack ids that should be active for the current pending/active experience context. */
    static Set<String> desiredPackIdsForCurrentContext() {
        return Set.copyOf(installedManagedPackIds());
    }

    private static Set<String> installedManagedPackIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (DAI_PackInstallManager.InstalledPack installed
                : DAI_PackInstallManager.installedPacks()) {
            if (!allowedForCurrentExperience(installed)) continue;
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
