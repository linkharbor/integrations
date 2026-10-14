#!/usr/bin/env python3
"""LinkHarbor Steam / Source server agent.

Queries a Source-engine game server (CS2, Rust, Valheim, ARK, Garry's Mod,
Team Fortress 2, Palworld, … — anything that answers A2S) and pushes the
state to a LinkHarbor "Custom (API)" integration so your bio page shows a
live game-server card and your followers can get push notifications.

    pip install -r requirements.txt
    export LH_INGEST_URL="https://api.linkharbor.com/v1/ingest/123"
    export LH_INGEST_SECRET="…"
    python3 agent.py --host 203.0.113.10 --port 27015 --name "My Rust Server" \
        --address "203.0.113.10:28015" --url "https://discord.gg/xyz" --interval 60

Events sent (each can drive a push rule in the dashboard):
    server.online    the server answered after being unreachable (or on first run)
    server.offline   the server stopped answering
    player.joined    data.player = name (one event per new player)
    player.left      data.player = name

The full state is sent with every event and additionally every --heartbeat
minutes (default 10) so the card never goes stale.
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

try:
    import a2s  # python-a2s
except ImportError:  # pragma: no cover
    print("Missing dependency: pip install -r requirements.txt", file=sys.stderr)
    sys.exit(1)

log = logging.getLogger("linkharbor")


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--host", required=True, help="game server IP / hostname")
    p.add_argument("--port", type=int, default=27015, help="A2S query port (default 27015)")
    p.add_argument("--name", default="", help="card title (default: the server's own name)")
    p.add_argument("--address", default="", help="address shown on the card, e.g. host:port")
    p.add_argument("--url", default="", help="link behind the card's button (Discord, website)")
    p.add_argument("--cta", default="Join server", help="button label")
    p.add_argument("--game", default="", help="game label (default: from A2S)")
    p.add_argument("--image-url", default="", help="banner image for the large layout")
    p.add_argument("--interval", type=int, default=60, help="seconds between queries (default 60)")
    p.add_argument("--heartbeat", type=int, default=10, help="minutes between state-only updates (0 = only on change)")
    p.add_argument("--no-player-names", action="store_true", help="don't send player names")
    p.add_argument("--ingest-url", default=os.environ.get("LH_INGEST_URL", ""))
    p.add_argument("--ingest-secret", default=os.environ.get("LH_INGEST_SECRET", ""))
    p.add_argument("--once", action="store_true", help="query once, send, exit")
    p.add_argument("-v", "--verbose", action="store_true")
    a = p.parse_args()
    if not a.ingest_url or not a.ingest_secret:
        p.error("set LH_INGEST_URL and LH_INGEST_SECRET (or --ingest-url / --ingest-secret)")
    return a


class Ingest:
    def __init__(self, url: str, secret: str) -> None:
        self.url, self.secret = url, secret

    def send(self, event: str | None, data: dict | None, state: dict) -> bool:
        body: dict = {"state": state}
        if event:
            body["event"] = event
            body["event_id"] = f"{event}-{uuid.uuid4()}"
        if data:
            body["data"] = data
        raw = json.dumps(body).encode()
        req = urllib.request.Request(
            self.url, data=raw, method="POST",
            headers={"Authorization": f"Bearer {self.secret}", "Content-Type": "application/json",
                     "User-Agent": "LinkHarbor-SteamAgent/1.0"},
        )
        for attempt in (1, 2):
            try:
                with urllib.request.urlopen(req, timeout=10) as res:
                    log.debug("← %s %s", res.status, res.read(200).decode(errors="replace"))
                    return True
            except urllib.error.HTTPError as e:
                text = e.read(300).decode(errors="replace")
                if e.code == 401:
                    log.error("401 from LinkHarbor — wrong secret or integration disconnected")
                    return False
                if e.code < 500:
                    log.error("%s from LinkHarbor: %s", e.code, text)
                    return False
                log.warning("%s from LinkHarbor (attempt %d)", e.code, attempt)
            except Exception as e:  # network
                log.warning("request failed (attempt %d): %s", attempt, e)
            time.sleep(2)
        return False


def query(host: str, port: int, with_players: bool) -> tuple[dict | None, list[str]]:
    """Returns (info, player names) or (None, []) when the server is unreachable."""
    try:
        info = a2s.info((host, port), timeout=3.0)
    except Exception as e:
        log.debug("A2S_INFO failed: %s", e)
        return None, []
    names: list[str] = []
    if with_players:
        try:
            names = [p.name for p in a2s.players((host, port), timeout=3.0) if p.name]
        except Exception as e:
            log.debug("A2S_PLAYER failed: %s", e)
    return {
        "server_name": getattr(info, "server_name", ""),
        "map": getattr(info, "map_name", ""),
        "game": getattr(info, "game", "") or getattr(info, "folder", ""),
        "players": int(getattr(info, "player_count", 0)),
        "max_players": int(getattr(info, "max_players", 0)),
        "version": str(getattr(info, "version", "") or ""),
    }, names


def build_state(a: argparse.Namespace, info: dict | None, names: list[str], last_name: str) -> dict:
    s: dict = {"type": "game_server", "name": a.name or (info or {}).get("server_name") or last_name or "Game server"}
    if a.address:
        s["address"] = a.address
    if a.url:
        s["url"] = a.url
    if a.cta:
        s["cta"] = a.cta
    if a.image_url:
        s["image_url"] = a.image_url
    if info is None:
        s.update(online=False, players_online=0, players=[])
        return s
    s.update(
        online=True,
        players_online=info["players"],
        players_max=info["max_players"],
        players=[] if a.no_player_names else names[:50],
        map=info["map"],
        game=a.game or info["game"],
        version=info["version"],
    )
    return s


def main() -> int:
    a = parse_args()
    logging.basicConfig(level=logging.DEBUG if a.verbose else logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    ingest = Ingest(a.ingest_url, a.ingest_secret)

    online: bool | None = None
    known: set[str] = set()
    last_name = ""
    last_sent = 0.0
    heartbeat_s = a.heartbeat * 60

    while True:
        info, names = query(a.host, a.port, not a.no_player_names)
        now_online = info is not None
        if info:
            last_name = info["server_name"] or last_name
        state = build_state(a, info, names, last_name)
        current = set(names)

        events: list[tuple[str, dict | None]] = []
        if online is None or now_online != online:
            events.append(("server.online" if now_online else "server.offline", None))
        if now_online and online is not None and not a.no_player_names:
            for n in sorted(current - known):
                events.append(("player.joined", {"player": n}))
            for n in sorted(known - current):
                events.append(("player.left", {"player": n}))

        if events:
            for ev, data in events:
                log.info("%s %s", ev, (data or {}).get("player", ""))
                ingest.send(ev, data, state)
            last_sent = time.time()
        elif heartbeat_s and time.time() - last_sent >= heartbeat_s:
            log.info("heartbeat: %s players online", state.get("players_online", 0))
            ingest.send(None, None, state)
            last_sent = time.time()

        online = now_online
        known = current if now_online else set()
        if a.once:
            return 0
        time.sleep(max(5, a.interval))


if __name__ == "__main__":
    sys.exit(main())
