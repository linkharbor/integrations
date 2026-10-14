# LinkHarbor Steam / Source server agent

A single-file Python agent that polls any Source-engine game server (CS2,
Rust, Valheim, ARK, Garry's Mod, Team Fortress 2, Palworld, Satisfactory, …)
over the Steam A2S query protocol and pushes the live state to your LinkHarbor
bio page — plus join/leave events for push notifications.

Runs anywhere with Python 3.10+ and network access to the server's query
port: on the game server itself, a VPS, a Raspberry Pi.

## Setup

```bash
git clone https://github.com/linkharbor/linkharbor-integrations
cd linkharbor-integrations/steam-a2s
pip install -r requirements.txt

export LH_INGEST_URL="https://api.linkharbor.com/v1/ingest/123"   # from the dashboard
export LH_INGEST_SECRET="…"

python3 agent.py --host 203.0.113.10 --port 27015 \
  --name "My Rust Server" --address "203.0.113.10:28015" \
  --url "https://discord.gg/xyz" --interval 60
```

`--port` is the **query** port (A2S), which is often the game port but not
always (Rust: game port + 1 by default; ARK: 27015; Valheim: game port + 1).
Try `--once -v` to see what the server answers.

Run it as a service: see `linkharbor-agent.service` (systemd).

## Events sent

| Event | When | Template variables |
|---|---|---|
| `server.online` | first successful query, or back after being unreachable | `{name}`, `{players_online}`, `{players_max}`, `{map}` |
| `server.offline` | the server stopped answering | `{name}` |
| `player.joined` | a new name appears in the player list | `{player}` + the above |
| `player.left` | a name disappears | `{player}` + the above |

The state is sent with every event and every `--heartbeat` minutes (default
10). `--no-player-names` sends counts only.

## Options

```
--host HOST            game server IP / hostname (required)
--port PORT            A2S query port (default 27015)
--name NAME            card title (default: the server's own name)
--address ADDRESS      address shown on the card
--url URL              link behind the card's button
--cta TEXT             button label (default "Join server")
--game TEXT            game label (default from A2S)
--image-url URL        banner image (large layout)
--interval SECONDS     polling interval (default 60, min 5)
--heartbeat MINUTES    state-only update interval (default 10, 0 = only on change)
--no-player-names      don't send player names
--once                 query once, send, exit
-v                     verbose logging
```
