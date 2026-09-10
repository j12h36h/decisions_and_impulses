package io.github.j12h36h.dai.input;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.content.DAI_ContentRegistry;
import io.github.j12h36h.dai.content.DAI_ContentStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Merged datapack/resource-pack input-profile registry. */
public final class DAI_InputProfileRegistry {
    private static Map<Identifier, DAI_InputProfileDefinition> data = Map.of();
    private static Map<Identifier, DAI_InputProfileDefinition> resources = Map.of();
    private static List<Map.Entry<Identifier, DAI_InputProfileDefinition>> ordered = List.of();

    private DAI_InputProfileRegistry() {}

    public static synchronized void replaceData(Map<Identifier, DAI_InputProfileDefinition> source) {
        data = copy(source);
        rebuild();
    }

    public static synchronized void replaceResources(Map<Identifier, DAI_InputProfileDefinition> source) {
        resources = copy(source);
        rebuild();
    }

    public static Match resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (Map.Entry<Identifier, DAI_InputProfileDefinition> entry : ordered) {
            if (matches(entry.getValue().match(), stack)) return new Match(entry.getKey(), entry.getValue());
        }
        return null;
    }

    public static boolean isEmpty() { return ordered.isEmpty(); }
    public static int size() { return ordered.size(); }

    private static void rebuild() {
        LinkedHashMap<Identifier, DAI_InputProfileDefinition> merged = new LinkedHashMap<>(data);
        merged.putAll(resources);
        ArrayList<Map.Entry<Identifier, DAI_InputProfileDefinition>> list = new ArrayList<>(merged.entrySet());
        list.removeIf(e -> e.getValue() == null || !e.getValue().enabled());
        list.sort(Comparator.<Map.Entry<Identifier, DAI_InputProfileDefinition>>comparingInt(e -> e.getValue().priority())
                .reversed().thenComparing(e -> e.getKey().toString()));
        ordered = List.copyOf(list);
    }

    private static Map<Identifier, DAI_InputProfileDefinition> copy(Map<Identifier, DAI_InputProfileDefinition> source) {
        if (source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<Identifier, DAI_InputProfileDefinition> out = new LinkedHashMap<>();
        source.forEach((id, definition) -> {
            if (id != null && definition != null && definition.enabled()) out.put(id, definition);
        });
        return Map.copyOf(out);
    }

    private static boolean matches(JsonObject matcher, ItemStack stack) {
        if (matcher == null || matcher.size() == 0) return false;
        String contentId = DAI_ContentStack.id(stack);
        Identifier nativeId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemId = nativeId == null ? "" : nativeId.toString();
        DAI_ContentRegistry.Entry entry = DAI_ContentStack.resolve(stack);

        boolean hasRule = false;
        if (matcher.has("content_ids")) {
            hasRule = true;
            if (!contains(matcher.get("content_ids"), contentId)) return false;
        }
        if (matcher.has("item_ids")) {
            hasRule = true;
            if (!contains(matcher.get("item_ids"), itemId)) return false;
        }
        if (matcher.has("capabilities")) {
            hasRule = true;
            if (entry == null || !allStringsPresent(matcher.get("capabilities"), entry.definition().capabilities())) return false;
        }
        if (matcher.has("tags")) {
            hasRule = true;
            if (entry == null || !allStringsPresent(matcher.get("tags"), entry.definition().tags())) return false;
        }
        return hasRule;
    }

    private static boolean contains(JsonElement values, String actual) {
        if (values == null) return false;
        if (values.isJsonPrimitive()) return same(values, actual);
        if (!values.isJsonArray()) return false;
        for (JsonElement value : values.getAsJsonArray()) if (same(value, actual)) return true;
        return false;
    }

    private static boolean allStringsPresent(JsonElement values, List<String> actual) {
        if (values == null) return true;
        List<String> normalized = actual == null ? List.of() : actual.stream().map(DAI_InputProfileDefinition::normalized).toList();
        if (values.isJsonPrimitive()) return normalized.contains(DAI_InputProfileDefinition.normalized(values.getAsString()));
        if (!values.isJsonArray()) return false;
        for (JsonElement value : values.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !normalized.contains(DAI_InputProfileDefinition.normalized(value.getAsString()))) return false;
        }
        return true;
    }

    private static boolean same(JsonElement value, String actual) {
        try { return value.isJsonPrimitive() && value.getAsString().trim().equalsIgnoreCase(actual == null ? "" : actual.trim()); }
        catch (RuntimeException ignored) { return false; }
    }

    public record Match(Identifier id, DAI_InputProfileDefinition definition) {}
}
