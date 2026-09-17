package io.github.j12h36h.dai.client.packs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.client.config.DAI_ClientConfig;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/** Fetches the ERAS/DAI Worlds catalog with persistent offline cache + bundled fallback. */
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
    private static volatile String status = "Loading DAI Worlds catalog...";
    private static volatile boolean online;
    private static volatile String source = "bundled";
    private static volatile Instant lastRefresh;

    private DAI_OfficialPackService() {}

    public static DAI_OfficialPackCatalog cachedOrFallback() {
        DAI_OfficialPackCatalog value = cached;
        if (value != null) return value;

        value = loadPersistentCache();
        if (value != null) {
            cached = value;
            source = "offline cache";
            status = "Using cached ERAS catalog.";
            return value;
        }

        value = loadFallback();
        cached = value;
        source = "bundled";
        status = "Using bundled DAI Worlds catalog.";
        return value;
    }

    public static String status() { return status; }
    public static boolean online() { return online; }
    public static String source() { return source; }
    public static Instant lastRefresh() { return lastRefresh; }

    public static CompletableFuture<DAI_OfficialPackCatalog> refresh() {
        if (!DAI_ClientConfig.worldCatalogRefresh()) {
            DAI_OfficialPackCatalog current = cachedOrFallback();
            status = "Online catalog refresh is disabled in DAI Settings.";
            online = false;
            return CompletableFuture.completedFuture(current);
        }

        Settings settings = loadSettings();
        if (settings.catalogUrl().isBlank()) {
            DAI_OfficialPackCatalog fallback = cachedOrFallback();
            status = "No ERAS catalog URL configured; using local catalog.";
            online = false;
            return CompletableFuture.completedFuture(fallback);
        }

        URI uri;
        try {
            uri = URI.create(settings.catalogUrl());
        } catch (Exception exception) {
            return fallbackFuture("Invalid ERAS catalog URL.", exception);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return fallbackFuture("ERAS catalog must use HTTPS.", null);
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(5, settings.timeoutSeconds())))
                .header("Accept", "application/json")
                .header("User-Agent", "DAI-Engine-Worlds/" + DAI_Core.FEATURE_LEVEL)
                .GET()
                .build();

        status = "Checking ERAS for DAI Worlds updates...";

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
                    if (catalog.packs().isEmpty()) {
                        throw new IllegalStateException("Catalog contains no packs/worlds.");
                    }

                    cached = catalog;
                    online = true;
                    source = "ERAS";
                    lastRefresh = Instant.now();
                    status = "DAI Worlds catalog updated from ERAS.";
                    persist(response.body());
                    return catalog;
                })
                .exceptionally(exception -> {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Could not refresh DAI Worlds catalog; retaining local cache.",
                            exception
                    );
                    online = false;
                    DAI_OfficialPackCatalog fallback = loadPersistentCache();
                    if (fallback != null) {
                        cached = fallback;
                        source = "offline cache";
                        status = "ERAS unavailable; using the last cached catalog.";
                        return fallback;
                    }
                    fallback = loadFallback();
                    cached = fallback;
                    source = "bundled";
                    status = "ERAS unavailable; using bundled catalog.";
                    return fallback;
                });
    }

    private static CompletableFuture<DAI_OfficialPackCatalog> fallbackFuture(
            String message,
            Exception exception
    ) {
        if (exception == null) DAI_Core.LOGGER.warn("<DAI>: {}", message);
        else DAI_Core.LOGGER.warn("<DAI>: {}", message, exception);
        online = false;
        DAI_OfficialPackCatalog fallback = loadPersistentCache();
        if (fallback != null) {
            cached = fallback;
            source = "offline cache";
            status = message + " Using cached catalog.";
        } else {
            fallback = loadFallback();
            cached = fallback;
            source = "bundled";
            status = message + " Using bundled catalog.";
        }
        return CompletableFuture.completedFuture(fallback);
    }

    private static Settings loadSettings() {
        JsonObject root = readObject(SETTINGS_RESOURCE);
        if (root == null) return new Settings("", 15);
        return new Settings(
                string(root, "catalog_url", ""),
                integer(root, "timeout_seconds", 15)
        );
    }

    private static DAI_OfficialPackCatalog loadPersistentCache() {
        Path path = cachePath();
        if (!Files.isRegularFile(path)) return null;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return null;
            DAI_OfficialPackCatalog value = DAI_OfficialPackCatalog.parse(parsed.getAsJsonObject());
            // DAI public-network catalogs use schema 3+. Ignore legacy
            // addons-only caches so they cannot make the Experience tab appear empty.
            if (value.schema() < 3) return null;
            return value.packs().isEmpty() ? null : value;
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not read cached DAI Worlds catalog '{}'.", path, exception);
            return null;
        }
    }

    private static void persist(String json) {
        try {
            Path target = cachePath();
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            DAI_PackFileOps.moveReplacing(temp, target);
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not persist ERAS catalog cache.", exception);
        }
    }

    private static Path cachePath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(DAI_Core.MODID)
                .resolve("worlds")
                .resolve("catalog-cache-" + DAI_Core.FEATURE_LEVEL + ".json");
    }

    private static DAI_OfficialPackCatalog loadFallback() {
        return DAI_OfficialPackCatalog.parse(readObject(FALLBACK_RESOURCE));
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
