package io.github.j12h36h.dai.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/** Small reflection helper for optional client observations, never gameplay policy. */
public final class DAI_Reflect {
    private DAI_Reflect() {}

    public static Object field(Object target, String... names) {
        if (target == null || names == null) return null;
        Class<?> type = target.getClass();
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            for (Class<?> at = type; at != null; at = at.getSuperclass()) {
                try {
                    Field field = at.getDeclaredField(name);
                    if (!field.canAccess(target)) field.trySetAccessible();
                    return field.get(target);
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
        }
        return null;
    }

    public static Object staticField(String className, String fieldName) {
        try {
            Class<?> type = Class.forName(className);
            Field field = type.getField(fieldName);
            return field.get(null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static Object invoke(Object target, String name, Object... args) {
        if (target == null || name == null || name.isBlank()) return null;
        Object[] values = args == null ? new Object[0] : args;
        Method best = null;
        for (Class<?> at = target.getClass(); at != null && best == null; at = at.getSuperclass()) {
            for (Method method : at.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != values.length) continue;
                if (!compatible(method.getParameterTypes(), values)) continue;
                best = method;
                break;
            }
        }
        if (best == null) return null;
        try {
            if (!best.canAccess(target)) best.trySetAccessible();
            return best.invoke(target, values);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static Object optionalValue(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    public static String text(Object value, String fallback) {
        if (value == null) return fallback;
        Object string = invoke(value, "getString");
        String out = String.valueOf(string == null ? value : string);
        return out == null || out.isBlank() || "null".equals(out) ? fallback : out;
    }

    public static double number(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try { return value == null ? fallback : Double.parseDouble(value.toString()); }
        catch (RuntimeException ignored) { return fallback; }
    }

    public static long longNumber(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? fallback : Long.parseLong(value.toString()); }
        catch (RuntimeException ignored) { return fallback; }
    }

    public static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value == null) return fallback;
        String text = value.toString().trim().toLowerCase(java.util.Locale.ROOT);
        if (text.equals("true") || text.equals("1") || text.equals("yes")) return true;
        if (text.equals("false") || text.equals("0") || text.equals("no")) return false;
        return fallback;
    }

    private static boolean compatible(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            if (args[i] == null) {
                if (types[i].isPrimitive()) return false;
                continue;
            }
            Class<?> expected = boxed(types[i]);
            if (!expected.isInstance(args[i])) return false;
        }
        return true;
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
