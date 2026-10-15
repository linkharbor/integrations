# LinkHarbor Integrations

Connect your tools and games to your [LinkHarbor](https://linkharbor.com) bio page. Visitors see a live game-server card or status card. Followers get push notifications, for example "3 players joined" or "the shop is open".

Every connector talks to one small HTTP endpoint, the LinkHarbor Custom (API) integration. The contract is in [docs/INGEST_API.md](docs/INGEST_API.md). Each game and tool has its own repo with its own installer or single-file agent:

| Repo | Game or platform | How it runs | Events |
|---|---|---|---|
| [integration-minecraft](https://github.com/linkharbor/integration-minecraft) | Minecraft Java (Paper, Purpur and other forks) | plugin jar in `plugins/` | joins and leaves with names, start and stop |
| [integration-minecraft-bedrock](https://github.com/linkharbor/integration-minecraft-bedrock) | Minecraft Bedrock, Java without a plugin | Python agent, pings from outside | online/offline, player count |
| [integration-steam](https://github.com/linkharbor/integration-steam) | Rust, ARK, Valheim, Palworld, CS2, Garry's Mod, TF2, Satisfactory, 7 Days to Die, DayZ and other A2S games | Python agent | online/offline, joins and leaves |
| [integration-fivem](https://github.com/linkharbor/integration-fivem) | FiveM (GTA V), RedM (RDR2) | Python agent, HTTP endpoints | online/offline, joins and leaves |
| [integration-terraria](https://github.com/linkharbor/integration-terraria) | Terraria (TShock) | Python agent, REST API | online/offline, joins and leaves |
| [integration-factorio](https://github.com/linkharbor/integration-factorio) | Factorio | Python agent, RCON | online/offline, joins and leaves |
| [integration-ark](https://github.com/linkharbor/integration-ark) | ARK: Survival Evolved / Ascended | Python agent, RCON | online/offline, joins and leaves |
| [integration-rust](https://github.com/linkharbor/integration-rust) | Rust | Python agent, WebRCON | online/offline, joins and leaves |
| [integration-roblox](https://github.com/linkharbor/integration-roblox) | Roblox experiences | Python agent, public API | player count, visit milestones |
| [integration-discord](https://github.com/linkharbor/integration-discord) | Discord servers | bot | members online, joins, voice, announcements |
| [integration-kick](https://github.com/linkharbor/integration-kick) | Kick channels | Python agent, Kick API | live status, viewers |
| [integration-obs](https://github.com/linkharbor/integration-obs) | OBS Studio | Python agent, obs-websocket | streaming, recording, scene |
| [integration-uptime](https://github.com/linkharbor/integration-uptime) | any website | Python agent, HTTP check | up, down, slow |
| [integration-cli](https://github.com/linkharbor/integration-cli) | anything you can script | `lh` command for shells and cron jobs | whatever you send |

Want another game or tool? Open an issue here. See [Contributing](#contributing) if you want to build one.

## How it works

```
your tool  --POST /v1/ingest/{id}-->  LinkHarbor API  -->  widget on your bio page
           Authorization: Bearer <secret>             -->  push rules -> notifications to followers
```

1. In the dashboard open Workspace, Integrations, Custom (API). Give it a name and copy the Ingest URL and the Secret.
2. Put both into the connector's configuration. Each README says where.
3. Add an Integration block to your page and pick the integration. The card renders as soon as the first state arrives.
4. Optional: push rules turn events into notifications. A batch window collects events into one message ("{count} players joined: {player_list}"), a minimum pause keeps busy servers from sending too often.

Integrations are available on the LinkHarbor Pro and Agency plans.

## The payload

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

* `event`: any name you like. Push rules match on it. Optional.
* `data`: variables for push templates, such as `{player}`. Optional.
* `state`: replaces the widget. `type` is `game_server` or `status`. Optional.
* At least one of `event` and `state` is required.

Full reference: [docs/INGEST_API.md](docs/INGEST_API.md). Copy-and-paste examples: [examples/curl.sh](examples/curl.sh).

## Contributing

New connectors live in their own repo named `integration-<game>`, MIT-licensed, with as few dependencies as possible, never logging the secret, and with a README that lists the events they send so creators can build push rules. Open an issue here first if you plan something bigger. We are happy to move a good connector into this organisation.

## License

[MIT](LICENSE). © 2026 EMI Solutions OÜ.
