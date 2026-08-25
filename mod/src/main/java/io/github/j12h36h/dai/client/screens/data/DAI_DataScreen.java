package io.github.j12h36h.dai.client.screens.data;

import io.github.j12h36h.dai.api.DAI_StateValue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Runtime renderer for a {@code dai_screens} definition. */
public final class DAI_DataScreen extends Screen {
    private final String definitionId;
    private final DAI_DataScreenDefinition definition;
    private final List<DAI_DataScreenDefinition.Widget> visualWidgets = new ArrayList<>();
    private int left;
    private int top;

    public DAI_DataScreen(String definitionId, DAI_DataScreenDefinition definition) {
        super(Component.literal(definition == null ? "DAI Screen" : definition.title()));
        this.definitionId = definitionId == null ? "" : definitionId;
        this.definition = definition;
    }

    @Override
    protected void init() {
        super.init();
        visualWidgets.clear();
        left = Math.max(0, (width - definition.width()) / 2);
        top = Math.max(0, (height - definition.height()) / 2);

        for (DAI_DataScreenDefinition.Widget widget : definition.widgets()) {
            if (widget == null) continue;
            int x = left + widget.x();
            int y = top + widget.y();
            switch (widget.type()) {
                case "button" -> addRenderableWidget(Button.builder(
                        Component.literal(widget.label()),
                        ignored -> fire(widget.action())
                ).bounds(x, y, widget.width(), widget.height()).build());
                case "text_input", "input", "text" -> addTextInput(widget, x, y, false);
                case "number_input", "number" -> addTextInput(widget, x, y, true);
                case "toggle", "checkbox" -> addToggle(widget, x, y);
                case "selector", "dropdown", "tabs", "radio" -> addSelector(widget, x, y);
                case "slider" -> addRenderableWidget(new StateSlider(widget, x, y));
                default -> visualWidgets.add(widget);
            }
        }
    }

    private void addTextInput(DAI_DataScreenDefinition.Widget widget, int x, int y, boolean number) {
        EditBox box = new EditBox(font, x, y, widget.width(), widget.height(), Component.literal(widget.label()));
        box.setMaxLength(widget.maxLength());
        DAI_StateValue current = DAI_DataScreenState.get(widget.state());
        if (number) {
            if (current.type() == DAI_StateValue.Type.NUMBER) box.setValue(format(current.numberValue()));
            box.setResponder(value -> {
                try { DAI_DataScreenState.setNumber(widget.state(), Double.parseDouble(value.trim())); }
                catch (RuntimeException ignored) { }
            });
        } else {
            if (current.type() == DAI_StateValue.Type.STRING) box.setValue(current.stringValue());
            box.setResponder(value -> DAI_DataScreenState.setString(widget.state(), value));
        }
        addRenderableWidget(box);
    }

    private void addToggle(DAI_DataScreenDefinition.Widget widget, int x, int y) {
        Button[] holder = new Button[1];
        holder[0] = Button.builder(toggleText(widget), ignored -> {
            boolean next = !booleanState(widget.state());
            DAI_DataScreenState.setBoolean(widget.state(), next);
            holder[0].setMessage(toggleText(widget));
            fire(widget.action());
        }).bounds(x, y, widget.width(), widget.height()).build();
        addRenderableWidget(holder[0]);
    }

    private Component toggleText(DAI_DataScreenDefinition.Widget widget) {
        String prefix = widget.label().isBlank() ? widget.id() : widget.label();
        return Component.literal(prefix + ": " + (booleanState(widget.state()) ? "ON" : "OFF"));
    }

    private void addSelector(DAI_DataScreenDefinition.Widget widget, int x, int y) {
        Button[] holder = new Button[1];
        holder[0] = Button.builder(selectorText(widget), ignored -> {
            List<String> options = widget.options();
            if (!options.isEmpty()) {
                String current = stringState(widget.state());
                int index = options.indexOf(current);
                String next = options.get(Math.floorMod(index + 1, options.size()));
                DAI_DataScreenState.setString(widget.state(), next);
                holder[0].setMessage(selectorText(widget));
                fire(widget.action());
            }
        }).bounds(x, y, widget.width(), widget.height()).build();
        addRenderableWidget(holder[0]);
    }

    private Component selectorText(DAI_DataScreenDefinition.Widget widget) {
        String prefix = widget.label().isBlank() ? widget.id() : widget.label();
        return Component.literal(prefix + (prefix.isBlank() ? "" : ": ") + stringState(widget.state()));
    }

    private boolean booleanState(String key) {
        DAI_StateValue value = DAI_DataScreenState.get(key);
        return value.type() == DAI_StateValue.Type.BOOLEAN && value.booleanValue();
    }

    private String stringState(String key) {
        DAI_StateValue value = DAI_DataScreenState.get(key);
        if (value.type() == DAI_StateValue.Type.STRING) return value.stringValue();
        if (value.type() == DAI_StateValue.Type.NUMBER) return format(value.numberValue());
        if (value.type() == DAI_StateValue.Type.BOOLEAN) return Boolean.toString(value.booleanValue());
        return "";
    }

    private double numberState(String key, double fallback) {
        DAI_StateValue value = DAI_DataScreenState.get(key);
        return value.type() == DAI_StateValue.Type.NUMBER ? value.numberValue() : fallback;
    }

    private static void fire(String action) {
        if (action != null && !action.isBlank()) DAI_ActionQueue.enqueueDeferredReference(action.trim());
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int right = Math.min(width, left + definition.width());
        int bottom = Math.min(height, top + definition.height());
        graphics.fill(left, top, right, bottom, 0xD00B0710);
        graphics.outline(left, top, Math.max(1, right - left), Math.max(1, bottom - top), 0xFFFF8B32);
        graphics.text(font, Component.literal(definition.title()), left + 8, top + 7, 0xFFFFB06A);

        for (DAI_DataScreenDefinition.Widget widget : visualWidgets) renderVisual(graphics, widget);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderVisual(GuiGraphicsExtractor graphics, DAI_DataScreenDefinition.Widget widget) {
        int x = left + widget.x();
        int y = top + widget.y();
        switch (widget.type()) {
            case "panel", "scroll_container", "container" -> {
                graphics.fill(x, y, x + widget.width(), y + widget.height(), widget.background());
                graphics.outline(x, y, widget.width(), widget.height(), widget.color());
                if (!widget.label().isBlank()) graphics.text(font, Component.literal(widget.label()), x + 4, y + 4, widget.color());
            }
            case "progress", "progress_bar" -> {
                double min = widget.min();
                double max = Math.max(min + 0.000001D, widget.max());
                double current = numberState(widget.state(), min);
                double t = Math.max(0.0D, Math.min(1.0D, (current - min) / (max - min)));
                graphics.fill(x, y, x + widget.width(), y + widget.height(), widget.background());
                graphics.fill(x + 1, y + 1, x + 1 + (int)Math.round((widget.width() - 2) * t), y + widget.height() - 1, widget.color());
                graphics.outline(x, y, widget.width(), widget.height(), 0xFFFFFFFF);
                if (!widget.label().isBlank()) graphics.centeredText(font, Component.literal(widget.label() + " " + format(current)), x + widget.width()/2, y + Math.max(1, (widget.height()-font.lineHeight)/2), 0xFFFFFFFF);
            }
            case "item_slot", "item" -> {
                Identifier id = Identifier.tryParse(widget.item());
                if (id != null) {
                    var item = BuiltInRegistries.ITEM.getValue(id);
                    if (item != null) graphics.item(new ItemStack(item), x, y);
                }
                if (!widget.label().isBlank()) graphics.text(font, Component.literal(widget.label()), x + 19, y + 4, widget.color());
            }
            case "label" -> graphics.text(font, Component.literal(widget.label()), x, y, widget.color());
            default -> { }
        }
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public boolean isPauseScreen() { return definition.pauseGame(); }
    @Override public boolean shouldCloseOnEsc() { return definition.closeOnEscape(); }

    private static String format(double value) {
        if (Math.rint(value) == value) return Long.toString((long)value);
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private final class StateSlider extends AbstractSliderButton {
        private final DAI_DataScreenDefinition.Widget widget;
        private StateSlider(DAI_DataScreenDefinition.Widget widget, int x, int y) {
            super(x, y, widget.width(), widget.height(), Component.empty(), normalized(widget));
            this.widget = widget;
            updateMessage();
        }
        private double actual() { return widget.min() + value * (widget.max() - widget.min()); }
        @Override protected void updateMessage() {
            String prefix = widget.label().isBlank() ? widget.id() : widget.label();
            setMessage(Component.literal(prefix + (prefix.isBlank() ? "" : ": ") + format(actual())));
        }
        @Override protected void applyValue() {
            double actual = actual();
            if (widget.step() > 0.0D) actual = widget.min() + Math.round((actual - widget.min()) / widget.step()) * widget.step();
            actual = Math.max(widget.min(), Math.min(widget.max(), actual));
            DAI_DataScreenState.setNumber(widget.state(), actual);
            fire(widget.action());
        }
        private static double normalized(DAI_DataScreenDefinition.Widget widget) {
            double span = widget.max() - widget.min();
            if (span <= 0.0D) return 0.0D;
            double current = DAI_DataScreenState.get(widget.state()).type() == DAI_StateValue.Type.NUMBER
                    ? DAI_DataScreenState.get(widget.state()).numberValue() : widget.min();
            return Math.max(0.0D, Math.min(1.0D, (current - widget.min()) / span));
        }
    }
}
