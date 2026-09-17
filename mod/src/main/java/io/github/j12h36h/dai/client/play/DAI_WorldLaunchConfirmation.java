package io.github.j12h36h.dai.client.play;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;

/**
 * Accepts intermediary Minecraft confirmation screens only when the caller has
 * already established that DAI owns the current world-launch transition.
 *
 * Reflection strings are deliberately avoided for Minecraft fields. Literal
 * mapped field names are not stable across dev/production runtimes, which made
 * the first auto-confirm patch capable of detecting BackupConfirmScreen while
 * still failing to invoke its callback.
 */
public final class DAI_WorldLaunchConfirmation {

    private DAI_WorldLaunchConfirmation() {}

    public static boolean isBackupPrompt(Screen screen) {
        return screen instanceof BackupConfirmScreen;
    }

    public static boolean isGenericPrompt(Screen screen) {
        return screen instanceof ConfirmScreen;
    }

    public static boolean isAffirmativePrompt(Screen screen) {
        return isBackupPrompt(screen) || isGenericPrompt(screen);
    }

    /**
     * Accepts a DAI-owned prompt without leaving a backup operation running
     * behind the loading veil. BackupConfirmScreen therefore uses Minecraft's
     * "Skip & Join" behavior: backup=false, eraseCache=false.
     */
    public static boolean acceptAffirmative(Screen screen) {
        if (screen instanceof BackupConfirmScreen backupConfirm) {
            if (invokeBackupListener(backupConfirm, false, false)) {
                return true;
            }

            // Fallback to the actual rendered button. This route survives even
            // if the callback field/interface changes but Minecraft still
            // exposes the standard Skip & Join action.
            if (pressButtonContaining(backupConfirm, "skip", "join")) {
                return true;
            }

            // Last resort: Backup & Join is still preferable to an invisible
            // permanent deadlock if a build/localization does not expose the
            // skip label in English.
            return pressButtonContaining(backupConfirm, "backup", "join");
        }

        if (screen instanceof ConfirmScreen confirm) {
            if (invokeBooleanConsumer(confirm, true)) {
                return true;
            }
            return pressFirstButton(confirm);
        }

        return false;
    }

    /**
     * Invokes the positive/continue action on a DAI-owned presentation prompt.
     * Dedicated experimental-warning screens are not always ConfirmScreen
     * subclasses, so after the strongly-typed paths above we fall back to the
     * screen's own rendered buttons while leaving its controller callback intact.
     */
    public static boolean acceptPresentedAffirmative(Screen screen) {
        if (screen == null) return false;
        if (acceptAffirmative(screen)) return true;
        if (pressButtonContainingAny(screen, "continue", "proceed", "yes", "create", "understand")) {
            return true;
        }
        return pressFirstButton(screen);
    }

    /** Invokes the negative/back action on a DAI-owned presentation prompt. */
    public static boolean declinePresented(Screen screen) {
        if (screen == null) return false;
        if (screen instanceof ConfirmScreen confirm && invokeBooleanConsumer(confirm, false)) {
            return true;
        }
        if (pressButtonContainingAny(screen, "no", "back", "cancel", "return")) {
            return true;
        }
        return pressLastButton(screen);
    }

    private static boolean invokeBackupListener(
            BackupConfirmScreen confirm,
            boolean createBackup,
            boolean eraseCache
    ) {
        try {
            for (Class<?> type = confirm.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    if (!field.canAccess(confirm) && !field.trySetAccessible()) continue;

                    Object candidate = field.get(confirm);
                    if (candidate == null) continue;

                    Method proceed = findBooleanPairMethod(candidate.getClass());
                    if (proceed == null) continue;
                    if (!proceed.canAccess(candidate) && !proceed.trySetAccessible()) continue;

                    proceed.invoke(candidate, createBackup, eraseCache);
                    return true;
                }
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Signature-based backup confirmation callback failed: {}",
                    exception.toString()
            );
        }
        return false;
    }

    private static boolean invokeBooleanConsumer(ConfirmScreen confirm, boolean value) {
        try {
            for (Class<?> type = confirm.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    if (!field.canAccess(confirm) && !field.trySetAccessible()) continue;

                    Object candidate = field.get(confirm);
                    if (candidate instanceof BooleanConsumer consumer) {
                        consumer.accept(value);
                        return true;
                    }
                }
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Signature-based generic confirmation callback failed: {}",
                    exception.toString()
            );
        }
        return false;
    }

    private static Method findBooleanPairMethod(Class<?> concreteType) {
        for (Class<?> type = concreteType; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (isBooleanPairCallback(method)) return method;
            }
            for (Class<?> iface : type.getInterfaces()) {
                for (Method method : iface.getDeclaredMethods()) {
                    if (isBooleanPairCallback(method)) return method;
                }
            }
        }
        return null;
    }

    private static boolean isBooleanPairCallback(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        return method.getReturnType() == Void.TYPE
                && parameters.length == 2
                && parameters[0] == Boolean.TYPE
                && parameters[1] == Boolean.TYPE;
    }

    private static boolean pressButtonContaining(Screen screen, String... requiredWords) {
        try {
            List<? extends GuiEventListener> children = screen.children();
            for (GuiEventListener child : children) {
                if (!(child instanceof Button button)) continue;
                String label = button.getMessage().getString().toLowerCase(Locale.ROOT);
                boolean matches = true;
                for (String required : requiredWords) {
                    if (!label.contains(required.toLowerCase(Locale.ROOT))) {
                        matches = false;
                        break;
                    }
                }
                if (!matches) continue;
                if (invokeButtonPress(button)) {
                    return true;
                }
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not press world-launch confirmation button: {}",
                    exception.toString()
            );
        }
        return false;
    }

    private static boolean pressButtonContainingAny(Screen screen, String... words) {
        if (screen == null || words == null) return false;
        for (String word : words) {
            if (word != null && !word.isBlank() && pressButtonContaining(screen, word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Minecraft 26.2 changed Button#onPress to accept InputWithModifiers.
     * Discover that overload by its remappable parameter type instead of
     * hard-coding a mapped method name, then invoke it with no modifiers.
     * The standard confirmation-screen button callbacks do not require an
     * input object; null represents an unmodified synthetic press here.
     */
    private static boolean invokeButtonPress(Button button) {
        try {
            for (Class<?> type = button.getClass(); type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (method.getReturnType() != Void.TYPE
                            || parameters.length != 1
                            || parameters[0] != InputWithModifiers.class) {
                        continue;
                    }
                    if (!method.canAccess(button) && !method.trySetAccessible()) {
                        continue;
                    }
                    method.invoke(button, new Object[]{null});
                    return true;
                }
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not invoke synthetic world-launch button press: {}",
                    exception.toString()
            );
        }
        return false;
    }

    private static boolean pressFirstButton(Screen screen) {
        try {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof Button button && invokeButtonPress(button)) {
                    return true;
                }
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not press generic confirmation button: {}",
                    exception.toString()
            );
        }
        return false;
    }

    private static boolean pressLastButton(Screen screen) {
        try {
            List<? extends GuiEventListener> children = screen.children();
            for (int i = children.size() - 1; i >= 0; i--) {
                GuiEventListener child = children.get(i);
                if (child instanceof Button button && invokeButtonPress(button)) {
                    return true;
                }
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not press negative confirmation button: {}",
                    exception.toString()
            );
        }
        return false;
    }
}
