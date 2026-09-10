package io.github.j12h36h.dai.client.creator;

import com.google.gson.JsonObject;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;

/**
 * Deprecated compatibility facade for callers compiled against the former
 * Automation Creator. Editing now lives in the generic Creator document model.
 */
@Deprecated
public final class DAI_AutomationCreatorRuntime {
    private DAI_AutomationCreatorRuntime() {}

    private static void select() { DAI_CreatorRuntime.selectSchema("decisions_and_impulses:action"); }
    public static String id() { select(); return DAI_CreatorRuntime.id(); }
    public static JsonObject draft() { select(); return DAI_CreatorRuntime.draft(); }
    public static void create(String requested) { select(); DAI_CreatorRuntime.createSelected(requested, net.minecraft.world.phys.Vec3.ZERO); }
    public static boolean load(String requested) { select(); return DAI_CreatorRuntime.loadSelected(requested); }
    public static boolean replaceRaw(String raw) { select(); return DAI_CreatorRuntime.replaceRawJson(raw); }
    public static void set(String path, String value) { select(); DAI_CreatorRuntime.set(path, value); }
    public static DAI_ActionDefinition validate() { return null; }
    public static boolean applyLive(String requested) { return false; }
    public static boolean test(String requested) { return false; }
    public static String compactJson() { return DAI_CreatorRuntime.draft().toString(); }
    public static String prettyJson() { return DAI_CreatorRuntime.rawJson(); }
}
