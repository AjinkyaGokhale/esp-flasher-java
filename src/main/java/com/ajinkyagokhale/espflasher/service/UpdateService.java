package com.ajinkyagokhale.espflasher.service;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Desktop;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class UpdateService {

    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/AjinkyaGokhale/esp-flasher-java/releases/latest";

    public record Release(String version, String downloadUrl) {}

    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String currentVersion() {
        try (InputStream in = getClass().getResourceAsStream("/version.properties")) {
            if (in == null) return "0.0.0";
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("version", "0.0.0").trim();
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    public Optional<Release> latestRelease() {
        String ext = assetExtension();
        if (ext == null) return Optional.empty();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LATEST_RELEASE_API))
                    .header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();

            JSONObject json = new JSONObject(response.body());
            String version = json.optString("tag_name", "");
            JSONArray assets = json.optJSONArray("assets");
            if (version.isEmpty() || assets == null) return Optional.empty();

            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.optString("name", "");
                String url = asset.optString("browser_download_url", "");
                if (name.toLowerCase().endsWith(ext) && !url.isEmpty()) {
                    return Optional.of(new Release(version, url));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean isNewer(String latest, String current) {
        int[] a = parse(latest);
        int[] b = parse(current);
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    public boolean downloadAndLaunch(Release release, ProgressListener listener, AtomicBoolean cancelled)
            throws Exception {
        String ext = assetExtension();
        Path target = Files.createTempFile("esp-flasher-update", ext == null ? ".bin" : ext);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(release.downloadUrl()))
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(
                request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Download failed with status " + response.statusCode());
        }

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        long downloaded = 0;
        byte[] buffer = new byte[1 << 16];
        boolean canceled = false;
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (cancelled.get()) {
                    canceled = true;
                    break;
                }
                out.write(buffer, 0, read);
                downloaded += read;
                listener.onProgress(downloaded, total);
            }
        }

        if (canceled) {
            Files.deleteIfExists(target);
            return false;
        }
        Desktop.getDesktop().open(target.toFile());
        System.exit(0);
        return true;
    }

    private int[] parse(String version) {
        if (version == null) return new int[0];
        String cleaned = version.trim().replaceFirst("^[vV]", "");
        String[] parts = cleaned.split("[.\\-+]");
        int[] result = new int[parts.length];
        int n = 0;
        for (String part : parts) {
            if (part.matches("\\d+")) {
                result[n++] = Integer.parseInt(part);
            } else {
                break;
            }
        }
        int[] trimmed = new int[n];
        System.arraycopy(result, 0, trimmed, 0, n);
        return trimmed;
    }

    private String assetExtension() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) return ".dmg";
        if (os.contains("win")) return ".msi";
        return null;
    }
}
