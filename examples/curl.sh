#!/usr/bin/env sh
# Minimal examples for the LinkHarbor ingest endpoint.
#   export LH_INGEST_URL="https://api.linkharbor.com/v1/ingest/123"
#   export LH_INGEST_SECRET="…"
set -eu
: "${LH_INGEST_URL:?set LH_INGEST_URL}"
: "${LH_INGEST_SECRET:?set LH_INGEST_SECRET}"

post() {
  curl -sS -X POST "$LH_INGEST_URL" \
    -H "Authorization: Bearer $LH_INGEST_SECRET" \
    -H "Content-Type: application/json" \
    -d "$1"
  echo
}

# 1. Status card: a shop that is open right now (no event, just the widget state)
post '{"state":{"type":"status","title":"Shop","state":"online","label":"Open until 18:00","url":"https://shop.example"}}'

# 2. Game server card + an event push rules can match ("player.joined")
post '{"event":"player.joined","event_id":"demo-1","data":{"player":"Steve"},
       "state":{"type":"game_server","name":"Survival SMP","online":true,"players_online":3,"players_max":20,
                "players":["Steve","Alex","Bob"],"version":"1.21.4","address":"play.example.com","url":"https://example.com/join"}}'

# 3. A plain event with custom variables for push templates ({title}, {url})
post '{"event":"blog.published","data":{"title":"New post","url":"https://blog.example/post"}}'
