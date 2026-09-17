package io.github.j12h36h.dai.sync;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-side raw JSON snapshots for client-visible datapack capabilities. */
public final class DAI_ClientDatapackSyncRepository {
    public static final String OBJECTIVES = "objectives";
    public static final String LOGICS = "logics";
    public static final String REACTION_EVENTS = "reaction_events";
    public static final String REACTIONS = "reactions";
    public static final String DATA_SCREENS = "dai_screens";
    public static final String SCREEN_OVERRIDES = "screen_overrides";
    public static final String SCENES = "scene_environments";
    public static final String SHELL_PRESENTATIONS = "dai_shell_presentations";
    public static final String TITLE_SCREENS = "dai_title_screens";

    public static final List<String> KINDS = List.of(
            OBJECTIVES, LOGICS, REACTION_EVENTS, REACTIONS,
            DATA_SCREENS, SCREEN_OVERRIDES, SCENES,
            SHELL_PRESENTATIONS, TITLE_SCREENS
    );

    private static final Map<String, Map<Identifier, JsonObject>> DATA = new LinkedHashMap<>();

    private DAI_ClientDatapackSyncRepository() {}

    public static synchronized void replace(String kind, Map<Identifier, JsonObject> definitions) {
        LinkedHashMap<Identifier, JsonObject> copy = new LinkedHashMap<>();
        if (definitions != null) definitions.forEach((id, json) -> {
            if (id != null && json != null) copy.put(id, json.deepCopy());
        });
        DATA.put(normalize(kind), Map.copyOf(copy));
    }

    public static synchronized Map<Identifier, JsonObject> snapshot(String kind) {
        return DATA.getOrDefault(normalize(kind), Map.of());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
