package io.github.j12h36h.dai.client.screens.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Locale;

/** Data-driven client screen authored from data/<namespace>/dai_screens/*.json. */
public record DAI_DataScreenDefinition(
        String title,
        int width,
        int height,
        boolean closeOnEscape,
        boolean pauseGame,
        List<Widget> widgets
) {
    public static final Codec<DAI_DataScreenDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("title", "DAI Screen").forGetter(DAI_DataScreenDefinition::title),
            Codec.INT.optionalFieldOf("width", 320).forGetter(DAI_DataScreenDefinition::width),
            Codec.INT.optionalFieldOf("height", 220).forGetter(DAI_DataScreenDefinition::height),
            Codec.BOOL.optionalFieldOf("close_on_escape", true).forGetter(DAI_DataScreenDefinition::closeOnEscape),
            Codec.BOOL.optionalFieldOf("pause_game", false).forGetter(DAI_DataScreenDefinition::pauseGame),
            Widget.CODEC.listOf().optionalFieldOf("widgets", List.of()).forGetter(DAI_DataScreenDefinition::widgets)
    ).apply(instance, DAI_DataScreenDefinition::new));

    public DAI_DataScreenDefinition {
        title = title == null ? "DAI Screen" : title;
        width = Math.max(80, Math.min(4096, width));
        height = Math.max(60, Math.min(4096, height));
        widgets = widgets == null ? List.of() : List.copyOf(widgets);
    }

    public record Widget(
            String type,
            String id,
            int x,
            int y,
            int width,
            int height,
            String label,
            String state,
            String action,
            String item,
            double min,
            double max,
            double step,
            List<String> options,
            int color,
            int background,
            int maxLength
    ) {
        private record Core(String type, String id, int x, int y, int width, int height, String label, String state) {}
        private record Payload(String action, String item, double min, double max, double step, List<String> options, int color, int background, int maxLength) {}

        private static final com.mojang.serialization.MapCodec<Core> CORE_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("type", "label").forGetter(Core::type),
                Codec.STRING.optionalFieldOf("id", "").forGetter(Core::id),
                Codec.INT.optionalFieldOf("x", 0).forGetter(Core::x),
                Codec.INT.optionalFieldOf("y", 0).forGetter(Core::y),
                Codec.INT.optionalFieldOf("width", 100).forGetter(Core::width),
                Codec.INT.optionalFieldOf("height", 20).forGetter(Core::height),
                Codec.STRING.optionalFieldOf("label", "").forGetter(Core::label),
                Codec.STRING.optionalFieldOf("state", "").forGetter(Core::state)
        ).apply(instance, Core::new));

        private static final com.mojang.serialization.MapCodec<Payload> PAYLOAD_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("action", "").forGetter(Payload::action),
                Codec.STRING.optionalFieldOf("item", "").forGetter(Payload::item),
                Codec.DOUBLE.optionalFieldOf("min", 0.0D).forGetter(Payload::min),
                Codec.DOUBLE.optionalFieldOf("max", 1.0D).forGetter(Payload::max),
                Codec.DOUBLE.optionalFieldOf("step", 0.0D).forGetter(Payload::step),
                Codec.STRING.listOf().optionalFieldOf("options", List.of()).forGetter(Payload::options),
                Codec.INT.optionalFieldOf("color", 0xFFFFFFFF).forGetter(Payload::color),
                Codec.INT.optionalFieldOf("background", 0xA0101018).forGetter(Payload::background),
                Codec.INT.optionalFieldOf("max_length", 2048).forGetter(Payload::maxLength)
        ).apply(instance, Payload::new));

        public static final Codec<Widget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                RecordCodecBuilder.of(Widget::core, CORE_CODEC),
                RecordCodecBuilder.of(Widget::payload, PAYLOAD_CODEC)
        ).apply(instance, Widget::fromParts));

        private Core core() { return new Core(type, id, x, y, width, height, label, state); }
        private Payload payload() { return new Payload(action, item, min, max, step, options, color, background, maxLength); }
        private static Widget fromParts(Core c, Payload p) {
            return new Widget(c.type(), c.id(), c.x(), c.y(), c.width(), c.height(), c.label(), c.state(),
                    p.action(), p.item(), p.min(), p.max(), p.step(), p.options(), p.color(), p.background(), p.maxLength());
        }

        public Widget {
            type = normalize(type, "label");
            id = id == null ? "" : id.trim();
            width = Math.max(1, Math.min(4096, width));
            height = Math.max(1, Math.min(4096, height));
            label = label == null ? "" : label;
            state = state == null ? "" : state.trim();
            action = action == null ? "" : action.trim();
            item = item == null ? "" : item.trim();
            if (!Double.isFinite(min)) min = 0.0D;
            if (!Double.isFinite(max)) max = 1.0D;
            if (max < min) { double swap = min; min = max; max = swap; }
            if (!Double.isFinite(step) || step < 0.0D) step = 0.0D;
            options = options == null ? List.of() : options.stream().filter(v -> v != null).toList();
            maxLength = Math.max(1, Math.min(32767, maxLength));
        }

        private static String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
