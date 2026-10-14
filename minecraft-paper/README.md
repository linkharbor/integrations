# LinkHarbor for Paper

A tiny Paper plugin (works on Paper, Purpur, Pufferfish and other Paper forks,
Minecraft 1.20+, Java 21) that pushes your server's live state and player
joins/leaves to your LinkHarbor bio page.

**On the page:** a game-server card — Online/Offline, "3 / 20" players, player
names, version, copyable address, a button to your Discord or website.
**For followers:** push notifications via rules you define, e.g. "{count}
players joined: {player_list}" every 10 minutes.

## Install

1. Download `LinkHarbor-<version>.jar` from the
   [releases](https://github.com/linkharbor/linkharbor-integrations/releases)
   (or build it, see below) and drop it into `plugins/`.
2. Start the server once — it writes `plugins/LinkHarbor/config.yml`.
3. In the LinkHarbor dashboard: **Workspace → Integrations → Custom (API)**,
   name it (e.g. "Survival SMP"), copy the **Ingest URL** and **Secret**.
4. Paste both into `config.yml`, set `server.address` (what players type to
   join) and optionally `server.url` (Discord invite / website), then run
   `/linkharbor reload` (or restart).
5. Add an **Integration** block to your bio page and pick the integration.

`/linkharbor status` shows the last response from LinkHarbor; `/linkharbor push`
sends the current state right away.

## Events sent

| Event | When | Template variables |
|---|---|---|
| `player.joined` | a player joins | `{player}`, `{players_online}`, `{players_max}`, `{name}`, `{players}` |
| `player.left` | a player leaves | same |
| `server.started` | plugin enabled | `{name}`, `{players_max}` |
| `server.stopped` | plugin disabled / server stopping | `{name}` |

Every event carries the full state, and a state-only heartbeat goes out every
`heartbeat_minutes` (default 10) so the card never goes stale.

Suggested push rule (dashboard → Integrations → Push rules → Custom (API) →
`player.joined`): batch window 10 min, title "🎮 {count} players joined", body
"{player_list} are now on {name}", minimum pause 5 min.

## Build

```bash
cd minecraft-paper
mvn -q package          # → target/LinkHarbor-1.0.0.jar
```

Requires Maven and JDK 21. The plugin has no runtime dependencies beyond the
Paper API (Gson and the HTTP client come with the server/JDK).

## Privacy

Only what you see in `config.yml` leaves the server: player **names** (switch
off with `send_player_names: false`), counts, version, your address/URL. No
UUIDs, IPs or chat. Requests go to the Ingest URL only.
