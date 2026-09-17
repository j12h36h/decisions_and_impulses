package io.github.j12h36h.dai.client.menus;

import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.title.DAI_TitleActionDispatcher;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * DAI-owned presentation of Minecraft's normal in-game pause actions.
 *
 * <p>This screen intentionally does not embed or skin PauseScreen. It owns the
 * layout so DAI can keep evolving it, while each action still forwards into
 * Minecraft's existing screen/controller implementation.</p>
 */
public final class DAI_VanillaPauseScreen extends Screen {

    private static final String ADVANCEMENTS =
            "net.minecraft.client.gui.screens.advancements.AdvancementsScreen";
    private static final String STATISTICS =
            "net.minecraft.client.gui.screens.achievement.StatsScreen";
    private static final String OPTIONS =
            "net.minecraft.client.gui.screens.options.OptionsScreen";
    private static final String SHARE_TO_LAN =
            "net.minecraft.client.gui.screens.ShareToLanScreen";

    private final Screen parent;
    private String status = "MINECRAFT CONTROLS // DAI PRESENTATION";

    public DAI_VanillaPauseScreen(Screen parent) {
        super(Component.literal("Vanilla"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        DAI_PresentationProfileService.Profile p = DAI_PresentationProfileService.selected();

        int panelW = Math.max(270, Math.min(430, width - 30));
        int x = (width - panelW) / 2;
        int top = 55;
        int gap = 6;
        int buttonH = 24;
        int half = (panelW - gap) / 2;
        int y = top;

        add(x, y, panelW, buttonH, "RETURN TO GAME", p.primary(), this::resumeGame);
        y += buttonH + gap;

        add(x, y, half, buttonH, "ADVANCEMENTS", p.secondary(), () -> openVanilla(ADVANCEMENTS));
        add(x + half + gap, y, panelW - half - gap, buttonH,
                "STATISTICS", p.secondary(), () -> openVanilla(STATISTICS));
        y += buttonH + gap;

        add(x, y, half, buttonH, "OPTIONS", p.primary(), () -> openVanilla(OPTIONS));
        add(x + half + gap, y, panelW - half - gap, buttonH,
                "OPEN TO LAN", p.secondary(), () -> openVanilla(SHARE_TO_LAN));
        y += buttonH + gap;

        add(x, y, half, buttonH, "SEND FEEDBACK", p.secondary(), () ->
                DAI_TitleActionDispatcher.openExternal("https://aka.ms/javafeedback"));
        add(x + half + gap, y, panelW - half - gap, buttonH,
                "REPORT BUGS", p.secondary(), () ->
                        DAI_TitleActionDispatcher.openExternal("https://aka.ms/snapshotbugs"));
        y += buttonH + gap;

        add(x, y, half, buttonH, "BACK TO DAI", p.secondary(), () ->
                Minecraft.getInstance().gui.setScreen(parent));
        add(x + half + gap, y, panelW - half - gap, buttonH,
                "SAVE & QUIT TO TITLE", 0xFFE85B64, DAI_GameMenuScreen::returnToTitle);
    }

    private void add(int x, int y, int w, int h, String label, int accent, Runnable action) {
        addRenderableWidget(new DAI_UniverseButton(
                x, y, w, h,
                Component.literal(label),
                ignored -> action.run(),
                DAI_UniverseButton.Shape.CHIP,
                accent
        ));
    }

    private void resumeGame() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gui != null) minecraft.gui.setScreen(null);
    }

    /**
     * Open a vanilla sub-screen while resolving its current 26.x constructor
     * from live Minecraft objects. This keeps Advancements/Statistics wired to
     * their real client data rather than reimplementing those systems in DAI.
     */
    private void openVanilla(String className) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        try {
            Class<?> raw = Class.forName(className);
            if (!Screen.class.isAssignableFrom(raw)) {
                throw new IllegalStateException(className + " is not a Screen");
            }

            List<Constructor<?>> constructors = new ArrayList<>(List.of(raw.getDeclaredConstructors()));
            constructors.sort(Comparator.comparingInt((Constructor<?> c) -> c.getParameterCount()).reversed());
            for (Constructor<?> constructor : constructors) {
                Object[] args = resolveArguments(constructor.getParameterTypes(), minecraft);
                if (args == null) continue;
                try {
                    if (!constructor.canAccess(null) && !constructor.trySetAccessible()) continue;
                    Object instance = constructor.newInstance(args);
                    minecraft.gui.setScreen((Screen) instance);
                    return;
                } catch (ReflectiveOperationException | RuntimeException rejected) {
                    DAI_Core.debug(
                            "<DAI>: Vanilla pause constructor '{}' rejected resolved arguments: {}",
                            constructor,
                            rejected.getClass().getSimpleName()
                    );
                }
            }

            // Options and Share-to-LAN normally resolve above. Keep the title
            // dispatcher's adjacent-mapping fallback for unusually mapped builds.
            DAI_TitleActionDispatcher.openReflective(this, className);
            if (minecraft.gui.screen() == this) {
                status = "UNAVAILABLE // " + simple(className);
            }
        } catch (Exception exception) {
            status = "UNAVAILABLE // " + simple(className);
            DAI_Core.LOGGER.warn("<DAI>: Could not open vanilla pause function '{}'.", className, exception);
        }
    }

    private Object[] resolveArguments(Class<?>[] types, Minecraft minecraft) {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            Object value = resolveKnown(type, minecraft);
            if (value == Missing.INSTANCE) return null;
            args[i] = value;
        }
        return args;
    }

    private Object resolveKnown(Class<?> type, Minecraft minecraft) {
        if (type.isInstance(this)) return this;
        if (parent != null && type.isInstance(parent)) return parent;
        if (type.isInstance(minecraft)) return minecraft;
        if (minecraft.options != null && type.isInstance(minecraft.options)) return minecraft.options;
        if (minecraft.player != null && type.isInstance(minecraft.player)) return minecraft.player;

        Object connection = minecraft.player == null ? null : minecraft.player.connection;
        if (connection != null && type.isInstance(connection)) return connection;

        Object discovered = discoverRuntimeValue(type, minecraft, minecraft.player, connection);
        if (discovered != null) return discovered;

        if (type == boolean.class || type == Boolean.class) return false;
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 0L;
        if (type == float.class || type == Float.class) return 0.0F;
        if (type == double.class || type == Double.class) return 0.0D;
        if (type == byte.class || type == Byte.class) return (byte) 0;
        if (type == short.class || type == Short.class) return (short) 0;
        if (type == char.class || type == Character.class) return '\0';
        if (Optional.class.isAssignableFrom(type)) return Optional.empty();
        if (Runnable.class.isAssignableFrom(type)) return (Runnable) () -> Minecraft.getInstance().gui.setScreen(this);

        // Nullable reference parameters are common on vanilla screens. We only
        // use null after trying all known live objects first.
        return type.isPrimitive() ? Missing.INSTANCE : null;
    }

    private static Object discoverRuntimeValue(Class<?> wanted, Object... roots) {
        String[] safeGetters = {
                "getAdvancements", "getStats", "getReportingContext",
                "getTelemetryManager", "getPlayerSocialManager", "getSocialManager"
        };
        for (Object root : roots) {
            if (root == null) continue;
            for (String name : safeGetters) {
                try {
                    Method method = root.getClass().getMethod(name);
                    if (method.getParameterCount() != 0) continue;
                    if (!wanted.isAssignableFrom(method.getReturnType())) continue;
                    Object value = method.invoke(root);
                    if (value != null && wanted.isInstance(value)) return value;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Another getter/root may own the requested vanilla object.
                }
            }
        }
        return null;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile p = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, p, System.nanoTime());
        graphics.fill(0, 0, width, height, 0x6E000000);

        int panelW = Math.max(270, Math.min(430, width - 30));
        int panelX = (width - panelW) / 2;
        int panelY = 39;
        int panelH = Math.min(height - 52, 190);
        graphics.fill(panelX - 8, panelY, panelX + panelW + 8, panelY + panelH, 0xC5080A10);
        graphics.outline(panelX - 8, panelY, panelW + 16, panelH,
                (p.secondary() & 0x00FFFFFF) | 0x99000000);
        graphics.fill(panelX - 8, panelY, panelX - 5, panelY + panelH, p.primary());

        graphics.centeredText(font, Component.literal("VANILLA"), width / 2, 13, p.primary());
        graphics.centeredText(font, Component.literal("MINECRAFT PAUSE FUNCTIONS"), width / 2, 25, p.secondary());
        graphics.centeredText(font, Component.literal(status), width / 2,
                Math.min(height - 13, panelY + panelH + 7), 0xFFB9A8C8);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // DAI universe renderer owns the complete background.
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private static String simple(String className) {
        if (className == null) return "SCREEN";
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1).toUpperCase() : className.toUpperCase();
    }

    private enum Missing { INSTANCE }
}
