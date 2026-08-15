package io.github.j12h36h.dai.client.packs;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Converts an official CurseForge file id + filename into its ForgeCDN URL. */
public final class DAI_CurseForgeDownload {

    private DAI_CurseForgeDownload() {}

    public static URI resolve(DAI_OfficialPackCatalog.ComponentEntry component) {
        if (component == null) return null;

        if (!component.downloadUrl().isBlank()) {
            URI uri = URI.create(component.downloadUrl());
            return isAllowed(uri) ? uri : null;
        }

        int fileId = component.curseForgeFileId();
        if (fileId <= 0 || component.fileName().isBlank()) return null;

        int first = fileId / 1000;
        int second = fileId % 1000;
        String path = encodePathSegment(component.fileName());

        return URI.create(
                "https://mediafilez.forgecdn.net/files/"
                        + first
                        + "/"
                        + String.format("%03d", second)
                        + "/"
                        + path
        );
    }

    public static boolean isAllowed(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        String normalized = host.toLowerCase();
        return normalized.equals("curseforge.com")
                || normalized.endsWith(".curseforge.com")
                || normalized.equals("forgecdn.net")
                || normalized.endsWith(".forgecdn.net");
    }

    private static String encodePathSegment(String value) {
        StringBuilder output = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                output.append((char) c);
            } else {
                output.append('%');
                output.append(Character.toUpperCase(Character.forDigit((c >>> 4) & 0xF, 16)));
                output.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return output.toString();
    }
}
