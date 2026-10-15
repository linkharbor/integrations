# LinkHarbor Integrations

Connect your tools and games to your [LinkHarbor](https://linkharbor.com) bio
page: a live **game-server card** or **status card** for your visitors, and
**push notifications** for your followers ("3 players joined", "the shop is
open").

Every connector talks to one small, open HTTP endpoint — the LinkHarbor
**Custom (API)** integration — documented in
[docs/INGEST_API.md](docs/INGEST_API.md). One repo per game, each with its own
installer or single-file agent:

| Repo | Game / platform | How it runs | Events |
|---|---|---|---|
| [integration-minecraft](https://github.com/linkharbor/integration-minecraft) | Minecraft Java (Paper, Purpur, …) | plugin jar in `plugins/` | joins/leaves with names, start/stop |
| [integration-minecraft-bedrock](https://github.com/linkharbor/integration-minecraft-bedrock) | Minecraft Bedrock, plugin-free Java | Python agent, pings from outside | online/offline, player count |
| [integration-steam](https://github.com/linkharbor/integration-steam) | Rust, ARK, Valheim, Palworld, CS2, Garry's Mod, TF2, Satisfactory, 7 Days to Die, DayZ, … (A2S) | Python agent | online/offline, joins/leaves |
| [integration-fivem](https://github.com/linkharbor/integration-fivem) | FiveM (GTA V), RedM (RDR2) | Python agent, HTTP endpoints | online/offline, joins/leaves |

Planned: Discord bot (member joins, voice activity → status card), Terraria
(TShock), Factorio (RCON). Want another game? Open an issue here, or send a
pull request with a new repo link — see [Contributing](#contributing).

## How it works

```
your tool ──POST /v1/ingest/{id}──▶ LinkHarbor API ──▶ widget on your bio page (live)
            Authorization: Bearer <secret>          └─▶ push rules → notifications to followers
```

1. Dashboard → **Workspace → Integrations → Custom (API)**, give it a name,
   copy the **Ingest URL** and **Secret**.
2. Put both into the connector's configuration (see its README).
3. Add an **Integration** block to your page and pick the integration — the
   card renders as soon as the first state arrives.
4. Optional: **Push rules** turn events into notifications. A *batch window*
   collects events ("{count} players joined: {player_list}") and a *minimum
   pause* keeps busy servers from spamming.

Integrations are available on LinkHarbor Pro and Agency plans.

## The payload in 20 seconds

```json
{
  "event":    "player.joined",
  "event_id": "join-7f3a",
  "data":     { "player": "Steve" },
  "state": {
    "type": "game_server",
    "name": "Survival SMP", "online": true,
    "players_online": 3, "players_max": 20, "players": ["Steve", "Alex", "Bob"],
    "version": "1.21.4", "address": "play.example.com", "url": "https://example.com/join"
  }
}
```

* `event` — any name you like; push rules match on it. Optional.
* `data` — variables for push templates (`{player}`). Optional.
* `state` — replaces the widget. `type` is `game_server` or `status`. Optional.
* At least one of `event` / `state` is required.

Full reference: [docs/INGEST_API.md](docs/INGEST_API.md). Copy-paste
examples: [examples/curl.sh](examples/curl.sh).

## Contributing

New connectors live in their own repo (`integration-<game>`), MIT-licensed,
dependency-light, never logging the secret, with a README that lists the
events they send so creators can build push rules. Open an issue here first
if you plan something bigger; we are happy to move a good connector under
this organisation.

## License

[MIT](LICENSE) — © 2026 EMI Solutions OÜ.
