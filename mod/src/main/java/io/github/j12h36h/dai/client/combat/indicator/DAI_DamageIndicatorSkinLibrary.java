package io.github.j12h36h.dai.client.combat.indicator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Loads client-side indicator skins from assets/&lt;namespace&gt;/dai/damage_indicators/*.json. */
public final class DAI_DamageIndicatorSkinLibrary extends SimplePreparableReloadListener<List<DAI_DamageIndicatorSkin>> {
    public static final String DIRECTORY = "dai/damage_indicators";
    private static volatile DAI_DamageIndicatorSkin ACTIVE;
    private static volatile List<DAI_DamageIndicatorSkin> SKINS = List.of();

    @Override
    protected List<DAI_DamageIndicatorSkin> prepare(ResourceManager manager, ProfilerFiller profiler) {
        ArrayList<DAI_DamageIndicatorSkin> loaded = new ArrayList<>();
        if (manager == null) return loaded;
        Map<Identifier, Resource> resources = manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier skinId = toSkinId(entry.getKey());
            if (skinId == null) continue;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                DAI_DamageIndicatorSkin skin = DAI_DamageIndicatorSkin.parse(skinId, json);
                if (skin != null) loaded.add(skin);
            } catch (Throwable exception) {
                DAI_Core.LOGGER.warn("<DAI>: Could not load damage-indicator skin '{}' from '{}'.", skinId, entry.getKey(), exception);
            }
        }
        return loaded;
    }

    @Override
    protected void apply(List<DAI_DamageIndicatorSkin> loaded, ResourceManager manager, ProfilerFiller profiler) {
        ArrayList<DAI_DamageIndicatorSkin> sorted = new ArrayList<>(loaded == null ? List.of() : loaded);
        sorted.sort(Comparator.comparingInt(DAI_DamageIndicatorSkin::priority).reversed().thenComparing(s -> s.id().toString()));
        SKINS = List.copyOf(sorted);
        ACTIVE = sorted.isEmpty() ? null : sorted.getFirst();
        DAI_Core.LOGGER.info("<DAI>: Loaded {} damage-indicator skin(s); active={}", SKINS.size(), ACTIVE == null ? "none" : ACTIVE.id());
    }

    public static DAI_DamageIndicatorSkin active() { return ACTIVE; }
    public static List<DAI_DamageIndicatorSkin> skins() { return SKINS; }

    private static Identifier toSkinId(Identifier resourceId) {
        String path = resourceId.getPath();
        String prefix = DIRECTORY + "/";
        if (!path.startsWith(prefix) || !path.endsWith(".json")) return null;
        String relative = path.substring(prefix.length(), path.length() - 5);
        return relative.isBlank() ? null : Identifier.fromNamespaceAndPath(resourceId.getNamespace(), relative);
    }
}
