package com.linkharbor.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Builds the `state` object LinkHarbor expects for a game_server widget:
 *
 *   { "type": "game_server", "name", "online", "players_online",
 *     "players_max", "players": [...], "version", "address", "url", "cta",
 *     "image_url", "description", "game" }
 *
 * Must be called on the main thread (reads the player list).
 */
final class ServerStateBuilder {

    private final String name, address, url, cta, imageUrl, description, game;
    private final boolean sendPlayerNames;

    ServerStateBuilder(FileConfiguration cfg) {
        this.name = cfg.getString("server.name", "").trim();
        this.address = cfg.getString("server.address", "").trim();
        this.url = cfg.getString("server.url", "").trim();
        this.cta = cfg.getString("server.cta", "Join server").trim();
        this.imageUrl = cfg.getString("server.image_url", "").trim();
        this.description = cfg.getString("server.description", "").trim();
        this.game = cfg.getString("server.game", "Minecraft").trim();
        this.sendPlayerNames = cfg.getBoolean("send_player_names", true);
    }

    /** Current state; `exclude` is a player who is leaving right now (still listed by Bukkit). */
    JsonObject current(Player exclude) {
        JsonObject s = base();
        s.addProperty("online", true);
        JsonArray players = new JsonArray();
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (exclude != null && p.getUniqueId().equals(exclude.getUniqueId())) continue;
            count++;
            if (sendPlayerNames && players.size() < 50) players.add(p.getName());
        }
        s.addProperty("players_online", count);
        s.addProperty("players_max", Bukkit.getMaxPlayers());
        s.add("players", players);
        s.addProperty("version", Bukkit.getMinecraftVersion());
        return s;
    }

    /** State for server.stopped: offline, nobody online. */
    JsonObject offline() {
        JsonObject s = base();
        s.addProperty("online", false);
        s.addProperty("players_online", 0);
        s.addProperty("players_max", Bukkit.getMaxPlayers());
        s.add("players", new JsonArray());
        s.addProperty("version", Bukkit.getMinecraftVersion());
        return s;
    }

    private JsonObject base() {
        JsonObject s = new JsonObject();
        s.addProperty("type", "game_server");
        s.addProperty("name", name.isEmpty() ? motd() : name);
        if (!address.isEmpty()) s.addProperty("address", address);
        if (!url.isEmpty()) s.addProperty("url", url);
        if (!cta.isEmpty()) s.addProperty("cta", cta);
        if (!imageUrl.isEmpty()) s.addProperty("image_url", imageUrl);
        if (!description.isEmpty()) s.addProperty("description", description);
        if (!game.isEmpty()) s.addProperty("game", game);
        return s;
    }

    private static String motd() {
        try {
            String plain = PlainTextComponentSerializer.plainText().serialize(Bukkit.motd());
            String first = plain.split("\n", 2)[0].trim();
            return first.isEmpty() ? "Minecraft Server" : first;
        } catch (Throwable t) {
            return "Minecraft Server";
        }
    }
}
