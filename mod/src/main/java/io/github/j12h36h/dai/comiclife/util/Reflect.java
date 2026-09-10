package io.github.j12h36h.dai.comiclife.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

public final class Reflect {
    private Reflect() {}

    public static Object invoke(Object target, String name, Object... args) {
        if (target == null || name == null || name.isBlank()) return null;
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        Object receiver = target instanceof Class<?> ? null : target;
        Method method = findMethod(type, name, args);
        if (method == null) return null;
        try {
            if (!method.canAccess(receiver)) method.setAccessible(true);
            return method.invoke(receiver, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object field(Object target, String... names) {
        if (target == null || names == null) return null;
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        Object receiver = target instanceof Class<?> ? null : target;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            Class<?> cursor = type;
            while (cursor != null) {
                try {
                    Field field = cursor.getDeclaredField(name);
                    if (!field.canAccess(Modifier.isStatic(field.getModifiers()) ? null : receiver)) field.setAccessible(true);
                    return field.get(Modifier.isStatic(field.getModifiers()) ? null : receiver);
                } catch (Throwable ignored) {
                    cursor = cursor.getSuperclass();
                }
            }
        }
        return null;
    }

    public static Object staticField(String className, String... names) {
        try {
            return field(Class.forName(className), names);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String text(Object value, String fallback) {
        if (value == null) return fallback;
        if (value instanceof String s) return s;
        Object text = invoke(value, "getString");
        if (text != null) return String.valueOf(text);
        return String.valueOf(value);
    }

    public static double number(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value != null) {
            try { return Double.parseDouble(String.valueOf(value)); }
            catch (RuntimeException ignored) {}
        }
        return fallback;
    }

    public static long longNumber(Object value, long fallback) {
        if (value instanceof Number n) return n.longValue();
        if (value != null) {
            try { return Long.parseLong(String.valueOf(value)); }
            catch (RuntimeException ignored) {}
        }
        return fallback;
    }

    public static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        if (value != null) {
            String s = String.valueOf(value).trim().toLowerCase();
            if (s.equals("true") || s.equals("1") || s.equals("yes")) return true;
            if (s.equals("false") || s.equals("0") || s.equals("no")) return false;
        }
        return fallback;
    }

    public static Object optionalValue(Object maybeOptional) {
        if (maybeOptional instanceof Optional<?> optional) return optional.orElse(null);
        return maybeOptional;
    }

    private static Method findMethod(Class<?> type, String name, Object[] args) {
        Class<?> cursor = type;
        while (cursor != null) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (!method.getName().equals(name)) continue;
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != args.length) continue;
                boolean fits = true;
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (!compatible(parameterTypes[i], args[i])) { fits = false; break; }
                }
                if (fits) return method;
            }
            cursor = cursor.getSuperclass();
        }
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean fits = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (!compatible(parameterTypes[i], args[i])) { fits = false; break; }
            }
            if (fits) return method;
        }
        return null;
    }

    private static boolean compatible(Class<?> expected, Object actual) {
        if (actual == null) return !expected.isPrimitive();
        Class<?> actualClass = actual.getClass();
        if (expected.isAssignableFrom(actualClass)) return true;
        if (!expected.isPrimitive()) return false;
        return (expected == boolean.class && actualClass == Boolean.class)
                || (expected == byte.class && actualClass == Byte.class)
                || (expected == short.class && (actualClass == Short.class || actualClass == Byte.class))
                || (expected == int.class && Number.class.isAssignableFrom(actualClass))
                || (expected == long.class && Number.class.isAssignableFrom(actualClass))
                || (expected == float.class && Number.class.isAssignableFrom(actualClass))
                || (expected == double.class && Number.class.isAssignableFrom(actualClass))
                || (expected == char.class && actualClass == Character.class);
    }
}
