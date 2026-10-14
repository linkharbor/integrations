# LinkHarbor Integrations

Ready-made connectors that push live data from your tools and games to your
[LinkHarbor](https://linkharbor.com) bio page — a live **game-server card** or
**status card** for your visitors, and **push notifications** for your
followers ("3 players joined", "the shop is open").

Everything here talks to one small, open HTTP endpoint — the LinkHarbor
**Custom (API)** integration. If your tool isn't listed, the contract in
[docs/INGEST_API.md](docs/INGEST_API.md) is all you need: one `POST` with a
JSON body.

| Connector | What it does | Status |
|---|---|---|
| [`minecraft-paper/`](minecraft-paper/) | Paper/Spigot plugin: player joins/leaves as events, live player list, version and address on the card | ✅ ready |
| [`steam-a2s/`](steam-a2s/) | Python agent for any Source-engine server (CS2, Rust, Valheim, ARK, Garry's Mod, TF2, Palworld, …): polls A2S, sends joins/leaves and the live state | ✅ ready |
| [`examples/`](examples/) | `curl` one-liners: status card, game-server card, plain events | ✅ |
| Discord bot | member joins / voice activity → status card | planned |

## How it works

```
your tool ──POST /v1/ingest/{id}──▶ LinkHarbor API ──▶ widget on your bio page (live)
            Authorization: Bearer <secret>          └─▶ push rules → notifications to followers
```

1. In the LinkHarbor dashboard open **Workspace → Integrations → Custom (API)**,
   give it a name and copy the **Ingest URL** and **Secret**.
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

Full reference: [docs/INGEST_API.md](docs/INGEST_API.md).

## Contributing

Connectors for other games and tools are welcome — keep them dependency-light,
never log the secret, and document the events they send so creators can build
push rules for them. Open an issue first if you plan something bigger.

## License

[MIT](LICENSE) — © 2026 EMI Solutions OÜ.
