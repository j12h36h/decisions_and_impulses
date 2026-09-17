package io.github.j12h36h.dai.client.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellPresentationRepository;
import io.github.j12h36h.dai.client.data.DAI_ClientDataBootstrap;
import io.github.j12h36h.dai.client.screens.data.DAI_DataScreenDefinition;
import io.github.j12h36h.dai.client.screens.data.DAI_DataScreenRegistry;
import io.github.j12h36h.dai.client.title.DAI_TitleScreenRepository;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionLoader;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.network.DAI_ClientDatapackSyncPayload;
import io.github.j12h36h.dai.presentation.scene.DAI_SceneDefinition;
import io.github.j12h36h.dai.presentation.scene.DAI_SceneRegistry;
import io.github.j12h36h.dai.presentation.screen.DAI_ScreenOverrideDefinition;
import io.github.j12h36h.dai.presentation.screen.DAI_ScreenOverrideRegistry;
import io.github.j12h36h.dai.reactions.DAI_ReactionDefinition;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventDefinition;
import io.github.j12h36h.dai.reactions.DAI_ReactionEventRegistry;
import io.github.j12h36h.dai.reactions.DAI_ReactionLibrary;
import io.github.j12h36h.dai.sync.DAI_ClientDatapackSyncRepository;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.LinkedHashMap;
import java.util.Map;

/** Applies server-authored datapack JSON to the physical client's runtime registries. */
public final class DAI_ClientDatapackSyncRuntime {
    private static final Map<String, LinkedHashMap<Identifier, JsonObject>> INCOMING = new LinkedHashMap<>();
    private static boolean initialized;

    private DAI_ClientDatapackSyncRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener(DAI_ClientDatapackSyncRuntime::onLoggingOut);
    }

    private static synchronized void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        INCOMING.clear();
        DAI_ClientDataBootstrap.reloadLocalData();
        DAI_ShellPresentationRepository.replaceServerDefinitions(Map.of());
        DAI_TitleScreenRepository.replaceServerDefinitions(Map.of());
    }

    public static synchronized void handle(DAI_ClientDatapackSyncPayload payload) {
        if (payload == null) return;
        String phase = normalize(payload.phase());
        String kind = normalize(payload.kind());
        if (!DAI_ClientDatapackSyncRepository.KINDS.contains(kind)) return;

        switch (phase) {
            case "begin" -> INCOMING.put(kind, new LinkedHashMap<>());
            case "entry" -> add(kind, payload.id(), payload.json());
            case "end" -> finish(kind);
            default -> DAI_Core.LOGGER.warn("<DAI>: Ignored unknown client datapack sync phase '{}'.", phase);
        }
    }

    private static void add(String kind, String rawId, String rawJson) {
        LinkedHashMap<Identifier, JsonObject> values = INCOMING.get(kind);
        if (values == null) return;
        Identifier id = Identifier.tryParse(rawId == null ? "" : rawId.trim());
        if (id == null || rawJson == null || rawJson.isBlank()) return;
        try {
            var parsed = JsonParser.parseString(rawJson);
            if (parsed.isJsonObject()) values.put(id, parsed.getAsJsonObject());
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.warn("<DAI>: Ignored malformed synced '{}' JSON '{}'.", kind, rawId, exception);
        }
    }

    private static void finish(String kind) {
        LinkedHashMap<Identifier, JsonObject> raw = INCOMING.remove(kind);
        if (raw == null) return;

        try {
            switch (kind) {
                case DAI_ClientDatapackSyncRepository.OBJECTIVES ->
                        DAI_ActionLoader.applyDefinitions(decode(raw, DAI_ActionDefinition.CODEC), true, "server-sync:objectives");
                case DAI_ClientDatapackSyncRepository.LOGICS ->
                        DAI_ActionLoader.applyDefinitions(decode(raw, DAI_ActionDefinition.CODEC), false, "server-sync:logics");
                case DAI_ClientDatapackSyncRepository.REACTION_EVENTS -> applyReactionEvents(raw);
                case DAI_ClientDatapackSyncRepository.REACTIONS -> applyReactions(raw);
                case DAI_ClientDatapackSyncRepository.DATA_SCREENS -> applyDataScreens(raw);
                case DAI_ClientDatapackSyncRepository.SCREEN_OVERRIDES ->
                        DAI_ScreenOverrideRegistry.replace(decode(raw, DAI_ScreenOverrideDefinition.CODEC));
                case DAI_ClientDatapackSyncRepository.SCENES ->
                        DAI_SceneRegistry.replaceData(decode(raw, DAI_SceneDefinition.CODEC));
                case DAI_ClientDatapackSyncRepository.SHELL_PRESENTATIONS ->
                        DAI_ShellPresentationRepository.replaceServerDefinitions(toStringMap(raw));
                case DAI_ClientDatapackSyncRepository.TITLE_SCREENS ->
                        DAI_TitleScreenRepository.replaceServerDefinitions(toStringMap(raw));
                default -> { }
            }
            DAI_Core.LOGGER.info("<DAI>: Applied {} server-synced '{}' datapack definition(s).", raw.size(), kind);
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.error("<DAI>: Failed applying server-synced '{}' datapack data.", kind, exception);
        }
    }

    private static void applyReactionEvents(Map<Identifier, JsonObject> raw) {
        DAI_ReactionEventRegistry.resetToFallbacks();
        Map<Identifier, DAI_ReactionEventDefinition> decoded = decode(raw, DAI_ReactionEventDefinition.CODEC);
        decoded.forEach((sourceId, definition) -> {
            String id = definition.id().isBlank() ? sourceId.toString() : definition.id();
            DAI_ReactionEventRegistry.register(definition.withId(id));
        });
    }

    private static void applyReactions(Map<Identifier, JsonObject> raw) {
        DAI_ReactionLibrary.clear();
        decode(raw, DAI_ReactionDefinition.CODEC).forEach(DAI_ReactionLibrary::register);
    }

    private static void applyDataScreens(Map<Identifier, JsonObject> raw) {
        DAI_DataScreenRegistry.clear();
        decode(raw, DAI_DataScreenDefinition.CODEC).forEach(DAI_DataScreenRegistry::register);
    }

    private static Map<String, JsonObject> toStringMap(Map<Identifier, JsonObject> raw) {
        LinkedHashMap<String, JsonObject> output = new LinkedHashMap<>();
        raw.forEach((id, json) -> output.put(id.toString(), json.deepCopy()));
        return Map.copyOf(output);
    }

    private static <T> Map<Identifier, T> decode(Map<Identifier, JsonObject> raw, Codec<T> codec) {
        LinkedHashMap<Identifier, T> output = new LinkedHashMap<>();
        raw.forEach((id, json) -> {
            try {
                T value = codec.parse(JsonOps.INSTANCE, json).getOrThrow(error -> new IllegalArgumentException(error));
                if (value != null) output.put(id, value);
            } catch (RuntimeException exception) {
                DAI_Core.LOGGER.warn("<DAI>: Failed decoding synced definition '{}'.", id, exception);
            }
        });
        return output;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
