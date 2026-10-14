package com.linkharbor.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;

/**
 * LinkHarbor for Paper.
 *
 * Sends player joins/leaves as events and the full server state (online,
 * players, version, address) to a LinkHarbor "Custom (API)" integration
 * (POST /v1/ingest/{id}). The dashboard renders the state as a game-server
 * card on the bio page and can turn the events into push notifications —
 * immediately, with a cooldown, or batched ("3 players joined").
 *
 * Everything network-related runs off the main thread.
 */
public final class LinkHarborPlugin extends JavaPlugin implements Listener {

    private LinkHarborClient client;
    private ServerStateBuilder state;
    private BukkitTask heartbeat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!configure()) {
            getLogger().warning("LinkHarbor is not configured yet: set ingest_url and ingest_secret in plugins/LinkHarbor/config.yml, then run /linkharbor reload.");
        }
        getServer().getPluginManager().registerEvents(this, this);
        if (client != null && getConfig().getBoolean("events.server_started", true)) {
            // One tick later so the player list and MOTD are final.
            Bukkit.getScheduler().runTask(this, () -> send("server.started", Map.of(), true));
        }
    }

    @Override
    public void onDisable() {
        if (heartbeat != null) heartbeat.cancel();
        if (client != null && getConfig().getBoolean("events.server_stopped", true)) {
            // Synchronous on purpose: the scheduler is gone once we return.
            client.sendBlocking(IngestPayload.of("server.stopped", Map.of(), state.offline()));
        }
    }

    /** Reads config.yml; returns false when the ingest credentials are missing. */
    private boolean configure() {
        reloadConfig();
        FileConfiguration cfg = getConfig();
        if (heartbeat != null) { heartbeat.cancel(); heartbeat = null; }

        String url = cfg.getString("ingest_url", "").trim();
        String secret = cfg.getString("ingest_secret", "").trim();
        state = new ServerStateBuilder(cfg);
        if (url.isEmpty() || secret.isEmpty() || url.endsWith("/ingest/0")) {
            client = null;
            return false;
        }
        client = new LinkHarborClient(this, url, secret, cfg.getBoolean("debug", false));

        int minutes = cfg.getInt("heartbeat_minutes", 10);
        if (minutes > 0) {
            long ticks = minutes * 60L * 20L;
            heartbeat = Bukkit.getScheduler().runTaskTimer(this, () -> send(null, Map.of(), false), ticks, ticks);
        }
        return true;
    }

    // ── Events ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent e) {
        if (client == null || !getConfig().getBoolean("events.player_joined", true)) return;
        send("player.joined", Map.of("player", e.getPlayer().getName()), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (client == null || !getConfig().getBoolean("events.player_left", true)) return;
        Player leaving = e.getPlayer();
        // The quitting player is still in getOnlinePlayers() at this point.
        IngestPayload payload = IngestPayload.of("player.left", Map.of("player", leaving.getName()), state.current(leaving));
        client.sendAsync(payload);
    }

    /**
     * Builds the payload on the main thread (Bukkit API access) and ships it
     * asynchronously. event == null → state-only heartbeat (no push rules).
     */
    private void send(String event, Map<String, String> data, boolean withEvent) {
        if (client == null) return;
        IngestPayload payload = IngestPayload.of(withEvent ? event : null, data, state.current(null));
        client.sendAsync(payload);
    }

    // ── /linkharbor ─────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "reload" -> {
                boolean ok = configure();
                sender.sendMessage(ok ? "[LinkHarbor] Config reloaded." : "[LinkHarbor] Reloaded, but ingest_url/ingest_secret are missing.");
            }
            case "push" -> {
                if (client == null) { sender.sendMessage("[LinkHarbor] Not configured."); return true; }
                send(null, Map.of(), false);
                sender.sendMessage("[LinkHarbor] State sent.");
            }
            case "status" -> {
                if (client == null) {
                    sender.sendMessage("[LinkHarbor] Not configured — set ingest_url and ingest_secret.");
                } else {
                    sender.sendMessage("[LinkHarbor] Configured. Last result: " + client.lastResult());
                }
            }
            default -> sender.sendMessage("/linkharbor <reload|status|push>");
        }
        return true;
    }
}
