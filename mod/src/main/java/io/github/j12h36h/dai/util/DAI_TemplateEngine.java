package io.github.j12h36h.dai.util;

import java.util.Locale;
import java.util.Map;

/** Deterministic pack-authored text/resource templating. No project prose lives in Java. */
public final class DAI_TemplateEngine {
    private DAI_TemplateEngine() {}

    public static String resolve(String template, Map<String, ?> variables) {
        if (template == null || template.isEmpty()) return "";
        StringBuilder out = new StringBuilder(template.length() + 32);
        int cursor = 0;
        while (cursor < template.length()) {
            int open = template.indexOf('{', cursor);
            if (open < 0) {
                out.append(template, cursor, template.length());
                break;
            }
            int close = template.indexOf('}', open + 1);
            if (close < 0) {
                out.append(template, cursor, template.length());
                break;
            }
            out.append(template, cursor, open);
            String token = template.substring(open + 1, close).trim();
            out.append(resolveToken(token, variables));
            cursor = close + 1;
        }
        return out.toString();
    }

    private static String resolveToken(String token, Map<String, ?> variables) {
        if (token.isBlank()) return "{}";
        String transform = "raw";
        String key = token;
        int colon = token.indexOf(':');
        if (colon > 0) {
            transform = token.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            key = token.substring(colon + 1).trim();
        }
        Object raw = variables == null ? null : variables.get(key);
        String value = raw == null ? "" : String.valueOf(raw);
        return switch (transform) {
            case "upper" -> value.toUpperCase(Locale.ROOT);
            case "lower" -> value.toLowerCase(Locale.ROOT);
            case "friendly" -> friendly(value);
            case "roman" -> roman(parseInt(value, 0));
            default -> value;
        };
    }

    public static String friendly(String value) {
        String text = value == null ? "" : value.trim();
        int colon = text.indexOf(':');
        if (colon >= 0 && colon + 1 < text.length()) text = text.substring(colon + 1);
        text = text.replace('/', ' ').replace('_', ' ').replace('-', ' ').trim();
        if (text.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) out.append(word.substring(1));
        }
        return out.toString();
    }

    public static String roman(int number) {
        if (number <= 0 || number > 3999) return Integer.toString(number);
        int[] values = {1000,900,500,400,100,90,50,40,10,9,5,4,1};
        String[] symbols = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
        StringBuilder out = new StringBuilder();
        int n = number;
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) { n -= values[i]; out.append(symbols[i]); }
        }
        return out.toString();
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); }
        catch (RuntimeException ignored) { return fallback; }
    }
}
