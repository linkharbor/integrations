package com.linkharbor.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Thin HTTP client for the LinkHarbor ingest endpoint.
 *
 *   POST {ingest_url}
 *   Authorization: Bearer {ingest_secret}
 *   Content-Type: application/json
 *
 * Failures are logged, never thrown — a LinkHarbor outage must not affect
 * the game server. One retry after 2 s on network errors / 5xx.
 */
final class LinkHarborClient {

    private final Plugin plugin;
    private final Logger log;
    private final URI url;
    private final String secret;
    private final boolean debug;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private volatile String lastResult = "nothing sent yet";

    LinkHarborClient(Plugin plugin, String url, String secret, boolean debug) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.url = URI.create(url);
        this.secret = secret;
        this.debug = debug;
    }

    void sendAsync(IngestPayload payload) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> sendBlocking(payload));
    }

    void sendBlocking(IngestPayload payload) {
        String body = payload.body();
        if (debug) log.info("[LinkHarbor] → " + payload.describe() + " " + body);
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(url)
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + secret)
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "LinkHarbor-Minecraft/" + plugin.getDescription().getVersion())
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                int code = res.statusCode();
                lastResult = code + " for " + payload.describe();
                if (code >= 200 && code < 300) {
                    if (debug) log.info("[LinkHarbor] ← " + code + " " + res.body());
                    return;
                }
                if (code == 401) {
                    log.warning("[LinkHarbor] 401 — the ingest secret is wrong or the integration was disconnected. Check config.yml.");
                    return;
                }
                if (code < 500) {
                    log.warning("[LinkHarbor] " + code + " " + res.body());
                    return;
                }
                log.warning("[LinkHarbor] " + code + " from LinkHarbor (attempt " + attempt + ")");
            } catch (Exception ex) {
                lastResult = "error: " + ex.getMessage();
                log.warning("[LinkHarbor] request failed (attempt " + attempt + "): " + ex.getMessage());
            }
            try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }
    }

    String lastResult() {
        return lastResult;
    }
}
