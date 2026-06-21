package org.tauasa.apps.photomosaic.provider.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client for the Google Photos Picker API (https://photospicker.googleapis.com).
 *
 * Flow: createSession → (user picks in browser) → pollUntilReady → listMediaItems →
 * downloadBytes for each → deleteSession.
 *
 * Library-wide listing/search is no longer available (removed April 2025): the user must
 * hand-pick images, which is what the picker does.
 */
public final class PhotosPickerClient {

    private static final String BASE = "https://photospicker.googleapis.com/v1";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final Credential credential;

    public PhotosPickerClient(Credential credential) {
        this.credential = credential;
    }

    public record PickingSession(String id, String pickerUri,
                                 long pollIntervalMillis, long timeoutMillis,
                                 boolean mediaItemsSet) {
    }

    public PickingSession createSession() throws IOException, InterruptedException {
        HttpRequest req = base("/sessions")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        return parseSession(send(req, "create session"));
    }

    public PickingSession getSession(String sessionId) throws IOException, InterruptedException {
        return parseSession(send(base("/sessions/" + sessionId).GET().build(), "get session"));
    }

    public void pollUntilReady(String sessionId, Consumer<Long> onWaiting)
            throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        while (true) {
            PickingSession s = getSession(sessionId);
            if (s.mediaItemsSet()) {
                return;
            }
            long elapsed = System.currentTimeMillis() - start;
            if (s.timeoutMillis() > 0 && elapsed > s.timeoutMillis()) {
                throw new IOException("Picker session timed out before any photos were selected.");
            }
            if (onWaiting != null) {
                onWaiting.accept(elapsed);
            }
            Thread.sleep(Math.max(1000, s.pollIntervalMillis()));
        }
    }

    public void deleteSession(String sessionId) {
        try {
            http.send(base("/sessions/" + sessionId).DELETE().build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    public List<PickedPhoto> listMediaItems(String sessionId) throws IOException, InterruptedException {
        List<PickedPhoto> out = new ArrayList<>();
        String pageToken = null;
        do {
            String url = "/mediaItems?sessionId=" + enc(sessionId) + "&pageSize=100"
                    + (pageToken != null ? "&pageToken=" + enc(pageToken) : "");
            JsonObject body = send(base(url).GET().build(), "list media items");
            if (body.has("mediaItems")) {
                JsonArray items = body.getAsJsonArray("mediaItems");
                for (var el : items) {
                    JsonObject item = el.getAsJsonObject();
                    JsonObject file = item.getAsJsonObject("mediaFile");
                    if (file == null) {
                        continue;
                    }
                    out.add(new PickedPhoto(
                            optString(item, "id"),
                            optString(file, "baseUrl"),
                            optString(file, "mimeType"),
                            optString(file, "filename")));
                }
            }
            pageToken = optString(body, "nextPageToken");
        } while (pageToken != null && !pageToken.isEmpty());
        return out;
    }

    /**
     * Download a picked photo's bytes at a bounded size. The Picker API requires the bearer
     * token on baseUrl requests and accepts {@code =w{W}-h{H}} sizing. Tiles are small, so we
     * keep the download modest.
     */
    public byte[] downloadBytes(PickedPhoto photo, int maxW, int maxH)
            throws IOException, InterruptedException {
        String url = photo.baseUrl() + "=w" + maxW + "-h" + maxH;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + accessToken())
                .GET()
                .build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Failed to download " + photo.filename() + " (HTTP " + resp.statusCode() + ")");
        }
        return resp.body();
    }

    // ---- plumbing ----------------------------------------------------------

    private HttpRequest.Builder base(String path) throws IOException {
        return HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + accessToken());
    }

    private JsonObject send(HttpRequest req, String what) throws IOException, InterruptedException {
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Picker API error during " + what
                    + " (HTTP " + resp.statusCode() + "): " + resp.body());
        }
        String b = resp.body();
        return (b == null || b.isBlank()) ? new JsonObject() : JsonParser.parseString(b).getAsJsonObject();
    }

    private String accessToken() throws IOException {
        String token = credential.getAccessToken();
        Long expires = credential.getExpiresInSeconds();
        if (token == null || (expires != null && expires <= 60)) {
            credential.refreshToken();
            token = credential.getAccessToken();
        }
        if (token == null) {
            throw new IOException("No valid Google access token; please sign in again.");
        }
        return token;
    }

    private static PickingSession parseSession(JsonObject o) {
        long poll = 5000, timeout = 0;
        if (o.has("pollingConfig")) {
            JsonObject pc = o.getAsJsonObject("pollingConfig");
            poll = parseDurationMillis(optString(pc, "pollInterval"), 5000);
            timeout = parseDurationMillis(optString(pc, "timeoutIn"), 0);
        }
        return new PickingSession(
                optString(o, "id"),
                optString(o, "pickerUri"),
                poll,
                timeout,
                o.has("mediaItemsSet") && o.get("mediaItemsSet").getAsBoolean());
    }

    /** Parse a protobuf Duration string such as "5s" or "12.500s" into millis. */
    private static long parseDurationMillis(String d, long fallback) {
        if (d == null || d.isBlank()) {
            return fallback;
        }
        try {
            return (long) (Double.parseDouble(d.replace("s", "").trim()) * 1000);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String optString(JsonObject o, String key) {
        return (o != null && o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
