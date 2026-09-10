package io.github.j12h36h.dai.presentation.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DAI_ScreenOverrideRegistry {
    private static Map<Identifier, DAI_ScreenOverrideDefinition> definitions = Map.of();
    private static List<Map.Entry<Identifier, DAI_ScreenOverrideDefinition>> ordered = List.of();

    private DAI_ScreenOverrideRegistry() {}

    public static synchronized void replace(Map<Identifier, DAI_ScreenOverrideDefinition> source) {
        LinkedHashMap<Identifier, DAI_ScreenOverrideDefinition> copy = new LinkedHashMap<>();
        if (source != null) source.forEach((id, definition) -> {
            if (id != null && definition != null && definition.enabled()) copy.put(id, definition);
        });
        definitions = Map.copyOf(copy);
        ArrayList<Map.Entry<Identifier, DAI_ScreenOverrideDefinition>> list = new ArrayList<>(definitions.entrySet());
        list.sort(Comparator.<Map.Entry<Identifier, DAI_ScreenOverrideDefinition>>comparingInt(e -> e.getValue().priority()).reversed()
                .thenComparing(e -> e.getKey().toString()));
        ordered = List.copyOf(list);
    }

    public static Match resolve(Screen screen) {
        if (screen == null) return null;
        for (Map.Entry<Identifier, DAI_ScreenOverrideDefinition> entry : ordered) {
            if (entry.getValue().matches(screen)) return new Match(entry.getKey(), entry.getValue());
        }
        return null;
    }

    public static int size() { return definitions.size(); }
    public static boolean isEmpty() { return definitions.isEmpty(); }
    public record Match(Identifier id, DAI_ScreenOverrideDefinition definition) {}
}
