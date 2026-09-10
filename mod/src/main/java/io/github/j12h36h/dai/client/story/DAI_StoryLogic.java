package io.github.j12h36h.dai.client.story;

import com.google.gson.JsonObject;
import io.github.j12h36h.dai.logics.action.DAI_ActionArguments;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/** Generic DAI actions for pack-authored story/archive profiles. */
public final class DAI_StoryLogic {
    private DAI_StoryLogic() {}

    public static void open(DAI_ActionDefinition action) {
        DAI_StoryRuntime.openLibrary(action == null ? "" : action.arguments().string("profile", ""));
    }

    public static void event(DAI_ActionDefinition action) {
        if (action == null) return;
        DAI_ActionArguments args = action.arguments();
        DAI_StoryRuntime.recordExplicit(
                args.string("profile", ""),
                args.string("event", args.string("id", "event")),
                Math.max(0, Math.min(100, args.integer("importance", 50))),
                args.string("caption", ""),
                args.string("narration", ""),
                args.string("scene", ""),
                stringMap(args.object("fields"))
        );
    }

    public static void start(DAI_ActionDefinition action) {
        DAI_StoryRuntime.startExplicit(action == null ? "" : action.arguments().string("profile", ""));
    }

    public static void end(DAI_ActionDefinition action) {
        DAI_StoryRuntime.endExplicit(action == null ? "" : action.arguments().string("profile", ""));
    }

    private static Map<String,String> stringMap(JsonObject object) {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        if (object == null) return out;
        object.entrySet().forEach(entry -> {
            try { out.put(entry.getKey(), entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString() : entry.getValue().toString()); }
            catch (RuntimeException ignored) {}
        });
        return out;
    }
}
