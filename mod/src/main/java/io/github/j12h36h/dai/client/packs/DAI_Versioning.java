package io.github.j12h36h.dai.client.packs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small tolerant comparator for DAI/pack feature versions. */
public final class DAI_Versioning {
    private DAI_Versioning() {}

    public static int compare(String left, String right) {
        List<String> a = tokens(left);
        List<String> b = tokens(right);
        int size = Math.max(a.size(), b.size());
        for (int i = 0; i < size; i++) {
            String av = i < a.size() ? a.get(i) : "0";
            String bv = i < b.size() ? b.get(i) : "0";
            boolean an = numeric(av);
            boolean bn = numeric(bv);
            int cmp;
            if (an && bn) {
                cmp = Long.compare(number(av), number(bv));
            } else if (an != bn) {
                cmp = an ? 1 : -1;
            } else {
                cmp = av.compareToIgnoreCase(bv);
            }
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    public static boolean atLeast(String value, String minimum) {
        return minimum == null || minimum.isBlank() || compare(value, minimum) >= 0;
    }

    public static boolean atMost(String value, String maximum) {
        return maximum == null || maximum.isBlank() || compare(value, maximum) <= 0;
    }

    private static List<String> tokens(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("v")) value = value.substring(1);
        String[] split = value.split("[^a-z0-9]+");
        List<String> out = new ArrayList<>();
        for (String token : split) if (!token.isBlank()) out.add(token);
        return out.isEmpty() ? List.of("0") : out;
    }

    private static boolean numeric(String value) {
        if (value.isBlank()) return false;
        for (int i = 0; i < value.length(); i++) if (!Character.isDigit(value.charAt(i))) return false;
        return true;
    }

    private static long number(String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return Long.MAX_VALUE; }
    }
}
