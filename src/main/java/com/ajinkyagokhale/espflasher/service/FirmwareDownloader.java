package com.ajinkyagokhale.espflasher.service;

import com.ajinkyagokhale.espflasher.model.FirmwareDefinition;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class FirmwareDownloader {

    private static final String CACHE_DIR =
            System.getProperty("user.home") + "/.esp-flasher/firmware-cache";

    private static final Path VERSION_CACHE_FILE =
            Paths.get(System.getProperty("user.home"), ".esp-flasher", "version-cache.properties");

    // 12-hour TTL — long enough to avoid the rate limit, short enough to catch new releases
    private static final long VERSION_TTL_MS = 12L * 60L * 60L * 1000L;

    // In-memory version cache — populated on first fetch, kept for app lifetime
    private final Map<String, String> versionCache = new ConcurrentHashMap<>();

    private Properties diskCache;
    private boolean diskCacheLoaded;

    // Optional sink for diagnostic messages (e.g. UI log area). Null = silent.
    private Consumer<String> logger;

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    private void log(String msg) {
        if (logger != null) logger.accept(msg);
    }

    // ── Internet check ────────────────────────────────────
    public boolean isOnline() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(3))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com"))
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(
                    request, HttpResponse.BodyHandlers.discarding()
            );
            return response.statusCode() < 500;
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    // ── Fetch latest version (Atom feed first, REST fallback, disk-cached) ─────
    public String fetchLatestVersion(FirmwareDefinition firmware) {
        String repo = firmware.getGithubRepo();

        // 1. In-memory cache (this session)
        String cached = versionCache.get(repo);
        if (cached != null) return cached;

        // 2. Disk cache (survives restarts, 12h TTL)
        String fromDisk = readDiskCache(repo);
        if (fromDisk != null) {
            versionCache.put(repo, fromDisk);
            return fromDisk;
        }

        // 3. Atom feeds — not rate-limited, no auth needed
        String tag = fetchAtomTag(repo, "releases.atom");
        if (tag == null) tag = fetchAtomTag(repo, "tags.atom");

        if (tag == null) return "unknown";

        if (tag.startsWith("v") || tag.startsWith("V")) tag = tag.substring(1);
        versionCache.put(repo, tag);
        writeDiskCache(repo, tag);
        return tag;
    }

    private String fetchAtomTag(String repo, String suffix) {
        try {
            String url = "https://github.com/" + repo + "/" + suffix;
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/atom+xml")
                    .build();

            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                log("[version] " + repo + " — " + suffix + " HTTP " + response.statusCode());
                return null;
            }

            String body = response.body();

            // Prefer the /releases/tag/<TAG> URL fragment (present in releases.atom)
            String marker = "/releases/tag/";
            int idx = body.indexOf(marker);
            if (idx != -1) {
                int start = idx + marker.length();
                int end = start;
                while (end < body.length()) {
                    char c = body.charAt(end);
                    if (c == '"' || c == '<' || c == ' ' || c == '\n' || c == '\r') break;
                    end++;
                }
                String tag = body.substring(start, end);
                if (!tag.isEmpty()) return tag;
            }

            // Fallback: first <entry>'s <title> (works for tags.atom and unusual releases.atom)
            int entryIdx = body.indexOf("<entry");
            if (entryIdx == -1) {
                log("[version] " + repo + " — " + suffix + " had no entries");
                return null;
            }
            int titleStart = body.indexOf("<title>", entryIdx);
            if (titleStart == -1) return null;
            titleStart += "<title>".length();
            int titleEnd = body.indexOf("</title>", titleStart);
            if (titleEnd == -1) return null;
            String tag = body.substring(titleStart, titleEnd).trim();
            return tag.isEmpty() ? null : tag;

        } catch (Exception e) {
            log("[version] " + repo + " — " + suffix + " fetch failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    // ── Disk-cache helpers ─────────────────────────────────
    private synchronized String readDiskCache(String repo) {
        loadDiskCacheIfNeeded();
        String entry = diskCache.getProperty(repo);
        if (entry == null) return null;
        int sep = entry.indexOf('|');
        if (sep <= 0) return null;
        try {
            long ts = Long.parseLong(entry.substring(0, sep));
            if (System.currentTimeMillis() - ts > VERSION_TTL_MS) return null;
            String version = entry.substring(sep + 1);
            return version.isEmpty() ? null : version;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private synchronized void writeDiskCache(String repo, String version) {
        loadDiskCacheIfNeeded();
        diskCache.setProperty(repo, System.currentTimeMillis() + "|" + version);
        try {
            Path parent = VERSION_CACHE_FILE.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream out = Files.newOutputStream(VERSION_CACHE_FILE)) {
                diskCache.store(out, "ESP Flasher firmware version cache");
            }
        } catch (IOException e) {
            log("[version] could not persist cache: " + e.getMessage());
        }
    }

    private void loadDiskCacheIfNeeded() {
        if (diskCacheLoaded) return;
        diskCache = new Properties();
        if (Files.exists(VERSION_CACHE_FILE)) {
            try (InputStream in = Files.newInputStream(VERSION_CACHE_FILE)) {
                diskCache.load(in);
            } catch (IOException e) {
                log("[version] could not read cache: " + e.getMessage());
            }
        }
        diskCacheLoaded = true;
    }

    // ── Fetch download URL for specific bin ──────────────
    public String fetchDownloadUrl(FirmwareDefinition firmware, String chip) {
        String repo = firmware.getGithubRepo();
        String binName = firmware.getBinForChip(chip);
        if (binName == null) return null;

        // GitHub's "/releases/latest/download/<asset>" is a public redirect — no API, no rate limits.
        // The actual download() call follows the redirect to the asset's S3 URL.
        String directUrl = "https://github.com/" + repo + "/releases/latest/download/" + binName;
        if (assetExists(directUrl)) return directUrl;

        // Fallback: parse releases.atom to find the latest tag, then build a tagged-asset URL.
        String tag = fetchAtomTag(repo, "releases.atom");
        if (tag != null) {
            String taggedUrl = "https://github.com/" + repo + "/releases/download/" + tag + "/" + binName;
            if (assetExists(taggedUrl)) return taggedUrl;
            log("[download] asset '" + binName + "' not found at " + taggedUrl);
        }

        log("[download] no published asset named '" + binName + "' for " + repo);
        return null;
    }

    /** Returns true if a GET (HEAD isn't always honoured by GitHub redirects) lands on a 2xx response. */
    private boolean assetExists(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) {
            log("[download] assetExists check failed: " + e.getMessage());
            return false;
        }
    }

    // ── Download bin file ────────────────────────────────
    public String download(String downloadUrl, String fileName,
                           DownloadListener listener) throws Exception {
        // create cache dir
        Files.createDirectories(Paths.get(CACHE_DIR));
        String destPath = CACHE_DIR + "/" + fileName;

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .build();

        HttpResponse<InputStream> response = client.send(
                request, HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() >= 400) {
            throw new Exception("HTTP " + response.statusCode() + " downloading " + downloadUrl);
        }

        long totalBytes = response.headers()
                .firstValueAsLong("content-length")
                .orElse(-1);

        try (InputStream in = response.body();
             OutputStream out = new FileOutputStream(destPath)) {

            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (listener != null) {
                    listener.onProgress(downloaded, totalBytes);
                }
            }
        }

        return destPath;
    }

    // ── Listener interface ───────────────────────────────
    public interface DownloadListener {
        void onProgress(long downloaded, long total);
    }

    public String downloadAndExtract(String downloadUrl, String zipName,
                                     DownloadListener listener) throws Exception {
        return downloadAndExtract(downloadUrl, zipName, null, null, listener);
    }

    public String downloadAndExtract(String downloadUrl, String zipName, String chip,
                                     String version, DownloadListener listener) throws Exception {

        String dirName = (version != null && !version.isBlank())
                ? zipName.replace(".zip", "") + "-v" + version
                : zipName.replace(".zip", "");
        String extractDir = CACHE_DIR + "/" + dirName;

        // ── Cache hit: extracted dir already has the bin for this chip ─────
        File existing = new File(extractDir);
        if (existing.isDirectory()) {
            String hit = pickBinForChip(existing, chip);
            if (hit != null) {
                log("[download] cache hit: " + hit);
                return hit;
            }
        }

        String zipPath = download(downloadUrl, zipName, listener);
        log("[download] saved " + zipPath + " (" + new File(zipPath).length() + " bytes)");

        long size = new File(zipPath).length();
        if (size < 1024) {
            // Likely an HTML error page, not a zip
            String preview = new String(
                    Files.readAllBytes(Paths.get(zipPath)),
                    StandardCharsets.UTF_8
            );
            throw new Exception("Downloaded file is too small to be a zip ("
                    + size + " bytes). Content: " + preview.substring(0, Math.min(200, preview.length())));
        }

        Files.createDirectories(Paths.get(extractDir));

        java.util.List<String> entries = new java.util.ArrayList<>();

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new FileInputStream(zipPath))) {

            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                entries.add(entry.getName() + " (" + entry.getSize() + " bytes)");

                String name = entry.getName();
                String lower = name.toLowerCase();
                if (lower.endsWith(".bin")) {
                    Path out = Paths.get(extractDir, new File(name).getName());
                    try (OutputStream os = new FileOutputStream(out.toString())) {
                        zis.transferTo(os);
                    }
                }
            }
        }

        String picked = pickBinForChip(new File(extractDir), chip);
        if (picked != null) return picked;

        log("[download] zip entries: " + entries);
        boolean requireFactory = zipName.toLowerCase().contains("ottelo");
        throw new Exception((requireFactory ? "No factory .bin" : "No .bin")
                + " for chip '" + chip + "' inside " + zipName + ". Entries: " + entries);
    }

    /**
     * Pick the best matching factory bin in an extracted dir for the given chip.
     * Preference order: factory bin matching chip → any bin matching chip → any factory bin.
     * Returns null if the directory has no bin we'd be confident flashing.
     */
    private String pickBinForChip(File dir, String chip) {
        File[] files = dir.listFiles();
        if (files == null) return null;

        File factoryForChip = null;
        File anyForChip = null;

        for (File f : files) {
            String lower = f.getName().toLowerCase();
            if (!lower.endsWith(".bin")) continue;
            if (!binMatchesChip(lower, chip)) continue;   // never flash a bin for the wrong chip
            if (lower.contains("factory") && factoryForChip == null) factoryForChip = f;
            else if (anyForChip == null) anyForChip = f;
        }

        if (factoryForChip != null) return factoryForChip.getAbsolutePath();
        return anyForChip != null ? anyForChip.getAbsolutePath() : null;
    }

    private static boolean binMatchesChip(String binNameLower, String chip) {
        if (chip == null || chip.isBlank() || chip.equals("auto")) return false;
        if (chip.equals("esp8266")) {
            // tasmota.bin / *8266*.bin — exclude any tasmota32* variants
            return binNameLower.contains("8266")
                    || (binNameLower.contains("tasmota") && !binNameLower.contains("tasmota32"));
        }
        if (chip.equals("esp32")) {
            // match "32" but NOT followed by a chip-family letter (c/s/h)
            int i = binNameLower.indexOf("32");
            while (i != -1) {
                int next = i + 2;
                if (next >= binNameLower.length()) return true;
                char c = binNameLower.charAt(next);
                if (c != 'c' && c != 's' && c != 'h') return true;
                i = binNameLower.indexOf("32", next);
            }
            return false;
        }
        // esp32c6, esp32c3, esp32s2, esp32s3, esp32h2 → match "32c6", "32c3", etc.
        if (chip.startsWith("esp32") && chip.length() > 5) {
            return binNameLower.contains("32" + chip.substring(5));
        }
        return binNameLower.contains(chip);
    }
}