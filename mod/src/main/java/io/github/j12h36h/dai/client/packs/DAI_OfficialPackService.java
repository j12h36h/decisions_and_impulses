package io.github.j12h36h.dai.client.packs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Fetches the curated catalog from the D.A.I. website with a built-in fallback. */
public final class DAI_OfficialPackService {

    private static final String SETTINGS_RESOURCE =
            "data/decisions_and_impulses/dai_pack_browser/browser.json";
    private static final String FALLBACK_RESOURCE =
            "data/decisions_and_impulses/dai_pack_browser/official_packs.json";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static volatile DAI_OfficialPackCatalog cached;
    private static volatile String status = "Loading official catalog...";

    private DAI_OfficialPackService() {}

    public static DAI_OfficialPackCatalog cachedOrFallback() {
        DAI_OfficialPackCatalog value = cached;
        if (value != null) return value;
        value = loadFallback();
        cached = value;
        return value;
    }

    public static String status() {
        return status;
    }

    public static CompletableFuture<DAI_OfficialPackCatalog> refresh() {
        Settings settings = loadSettings();
        if (settings.catalogUrl().isBlank()) {
            DAI_OfficialPackCatalog fallback = loadFallback();
            cached = fallback;
            status = "Using bundled official catalog.";
            return CompletableFuture.completedFuture(fallback);
        }

        URI uri;
        try {
            uri = URI.create(settings.catalogUrl());
        } catch (Exception exception) {
            return fallbackFuture("Invalid catalog URL.", exception);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return fallbackFuture("Official catalog must use HTTPS.", null);
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(5, settings.timeoutSeconds())))
                .header("Accept", "application/json")
                .header("User-Agent", "Decisions-and-Impulses-Pack-Browser/1.0")
                .GET()
                .build();

        status = "Checking the D.A.I. website...";

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("HTTP " + response.statusCode());
                    }

                    JsonElement parsed = JsonParser.parseString(response.body());
                    if (!parsed.isJsonObject()) {
                        throw new IllegalStateException("Catalog response is not a JSON object.");
                    }

                    DAI_OfficialPackCatalog catalog =
                            DAI_OfficialPackCatalog.parse(parsed.getAsJsonObject());
                    cached = catalog;
                    status = "Official catalog loaded from the D.A.I. website.";
                    return catalog;
                })
                .exceptionally(exception -> {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Could not refresh official pack catalog; using bundled fallback.",
                            exception
                    );
                    DAI_OfficialPackCatalog fallback = loadFallback();
                    cached = fallback;
                    status = "Website catalog unavailable; using bundled catalog.";
                    return fallback;
                });
    }

    private static CompletableFuture<DAI_OfficialPackCatalog> fallbackFuture(
            String message,
            Exception exception
    ) {
        if (exception == null) {
            DAI_Core.LOGGER.warn("<DAI>: {}", message);
        } else {
            DAI_Core.LOGGER.warn("<DAI>: {}", message, exception);
        }
        DAI_OfficialPackCatalog fallback = loadFallback();
        cached = fallback;
        status = message + " Using bundled catalog.";
        return CompletableFuture.completedFuture(fallback);
    }

    private static Settings loadSettings() {
        JsonObject root = readObject(SETTINGS_RESOURCE);
        if (root == null) return new Settings("", 15);

        String url = string(root, "catalog_url", "");
        int timeout = integer(root, "timeout_seconds", 15);
        return new Settings(url, timeout);
    }

    private static DAI_OfficialPackCatalog loadFallback() {
        JsonObject root = readObject(FALLBACK_RESOURCE);
        return DAI_OfficialPackCatalog.parse(root);
    }

    private static JsonObject readObject(String resource) {
        try (InputStream stream = DAI_OfficialPackService.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) return null;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed to read bundled pack-browser resource '{}'.", resource, exception);
            return null;
        }
    }

    private static String string(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsString(); } catch (Exception ignored) { return fallback; }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsInt(); } catch (Exception ignored) { return fallback; }
    }

    private record Settings(String catalogUrl, int timeoutSeconds) {}
}
