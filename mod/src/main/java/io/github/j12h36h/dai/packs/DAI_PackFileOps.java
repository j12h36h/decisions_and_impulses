package io.github.j12h36h.dai.packs;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

final class DAI_PackFileOps {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final long MAX_DOWNLOAD_BYTES = 512L * 1024L * 1024L;

    private DAI_PackFileOps() {}

    static void download(URI uri, Path target) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(90))
                .header("User-Agent", "Decisions-and-Impulses-Pack-Browser/1.0")
                .GET()
                .build();

        HttpResponse<InputStream> response = CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download returned HTTP " + response.statusCode());
        }

        Files.createDirectories(target.getParent());
        long written = 0L;
        try (InputStream input = response.body(); var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                written += read;
                if (written > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("Download exceeded 512 MiB safety limit.");
                }
                output.write(buffer, 0, read);
            }
        }
    }

    static void validateZip(Path zip) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            if (!file.entries().hasMoreElements()) {
                throw new IOException("Downloaded ZIP is empty.");
            }
        }
    }

    static void validateHash(Path file, String expected) throws Exception {
        if (expected == null || expected.isBlank()) return;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expected.trim())) {
            throw new IOException("SHA-256 verification failed.");
        }
    }

    static void extractZip(Path zip, Path destination) throws IOException {
        Files.createDirectories(destination);
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                Path target = destination.resolve(entry.getName()).normalize();
                if (!target.startsWith(destination)) {
                    throw new IOException("Unsafe ZIP path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static Path locatePackRoot(Path extracted) throws IOException {
        if (Files.isRegularFile(extracted.resolve("pack.mcmeta"))) return extracted;
        try (Stream<Path> children = Files.list(extracted)) {
            List<Path> dirs = children.filter(Files::isDirectory).toList();
            if (dirs.size() == 1 && Files.isRegularFile(dirs.get(0).resolve("pack.mcmeta"))) {
                return dirs.get(0);
            }
        }
        return null;
    }

    static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String safeFileName(String value) {
        String name = value == null ? "pack.zip" : value.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (name.isBlank()) name = "pack.zip";
        if (!name.toLowerCase().endsWith(".zip")) name += ".zip";
        return name;
    }

    static String safePathName(String value) {
        String name = value == null ? "pack" : value.toLowerCase();
        name = name.replace(':', '_').replaceAll("[^a-z0-9._-]", "_");
        return name.isBlank() ? "pack" : name;
    }

    static void deleteTreeQuietly(Path root) {
        try { deleteTree(root); } catch (Exception ignored) {}
    }

    static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
