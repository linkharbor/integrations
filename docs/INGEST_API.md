# LinkHarbor Ingest API (Custom (API) integration)

The generic way to push data into a LinkHarbor bio page. One endpoint, one
secret per integration, plain JSON.

## Connect

Dashboard → **Workspace → Integrations → Custom (API)** → name it → copy
**Ingest URL** (`https://dock.at-harbor.com/v1/ingest/{id}`) and **Secret**
(64 hex characters). Both are shown again in the integration's detail panel.
Disconnecting and reconnecting issues a new secret.

## Request

```http
POST https://dock.at-harbor.com/v1/ingest/{id}
Authorization: Bearer <secret>          # or: X-LinkHarbor-Secret: <secret>
Content-Type: application/json
```

Body: a JSON object, at most 64 KB, with at least one of `event` / `state`.

| Field | Required | Meaning |
|---|---|---|
| `event` | no | Free-form event name, e.g. `player.joined`. Folded to lowercase `[a-z0-9._-]`, max 80 chars (`"Player Joined"` → `player_joined`). Push rules match on it. Omitted → the event is called `state.updated`. |
| `event_id` | no | Idempotency key (≤ 255 chars). Sending the same `event_id` again returns `duplicate: true` and changes nothing — safe for retries. |
| `data` | no | Object of scalar values → variables for push templates (`{player}`). Lists of scalars become `"a, b"`, nested objects are dropped, max 40 keys, strings ≤ 500 chars. |
| `state` | no | The complete widget state. **Replaces** the previous one (no merge). `state.type` selects the card. |

### Response

```json
{ "ok": true, "event_id": 4711, "duplicate": false, "event_type": "player.joined" }
```

| HTTP | `error` | When |
|---|---|---|
| 200 | — | processed (also for duplicates) |
| 400 | `invalid_json` | body is not a JSON object |
| 401 | `invalid_secret` | unknown integration **or** wrong secret (deliberately indistinguishable) |
| 401 | `signature_invalid` | `X-LinkHarbor-Signature` present but wrong |
| 413 | `payload_too_large` | body over 64 KB |
| 422 | `validation_failed` | neither `event` nor `state`, or processing failed (`error` says why) |

### Optional signature

If you prefer not to send the secret as a bearer token you may add
`X-LinkHarbor-Signature: sha256=<hex HMAC-SHA256 of the raw body>` keyed with
the integration's *signing key* (shown once at connect). When present it is
verified; when absent the bearer secret alone authenticates.

### How often?

As often as you like — but every call stores an event row. Send the state
when something changed (join/leave) plus a heartbeat every few minutes.

## Widget states

### `game_server`

| Field | Type | Limit |
|---|---|---|
| `name` | string | 80 |
| `online` | bool (`true`/`false`, also `"yes"`/`1`) | default `true` |
| `players_online`, `players_max` | int ≥ 0 | `players_online` falls back to `count(players)` |
| `players` | string[] | 50 names × 32 chars, de-duplicated |
| `version`, `map`, `game` | string | 40 / 80 / 60 |
| `address` | string | 120 — rendered as a copyable code box |
| `description` | string | 200 |
| `image_url`, `url` | `http(s)://` | 512 — without `url` the card has no button |
| `cta` | string | 40 — button label (default "Join server") |

### `status`

| Field | Type | Limit |
|---|---|---|
| `title` | string | 80 |
| `state` | `online` `offline` `busy` `away` `maintenance` `unknown` | badge colour: green / red / orange / grey |
| `label` | string | 40 — badge text (small layout) or caption under the value |
| `value` | string | 40 — the big number ("12") |
| `text` | string | 200 |
| `image_url`, `url`, `cta` | as above | |

Unknown fields are dropped; an unknown `type` leaves the widget untouched.

## Push rules

Push rules (dashboard → Integrations → Push rules) match `provider: custom` +
your event name. Templates use `{variable}` — everything from `data` plus the
scalar state fields (`{players_online}`, `{name}`); `data` wins on name
clashes; `{provider_name}` and `{event}` always exist.

Two timing options per rule:

| Option | Effect |
|---|---|
| **Minimum pause** (`cooldown_sec`) | at most one push per rule every N seconds; events inside the pause are skipped |
| **Batch window** (`batch_window_sec`) | collect events for N seconds, then send **one** push. One collected event → the normal templates; several → the batch templates with `{count}`, every `{key}` of the last event and `{key_list}` (distinct values, comma-separated: "Steve, Alex, Bob") |

Example — one notification per 10 minutes, at most every 5 minutes:

```
title:        {player} joined
body:         {players_online} players on {name}
batch window: 10 min
batch title:  {count} players joined
batch body:   {player_list} are now on {name}
pause:        5 min
```

→ "3 players joined" / "Steve, Alex, Bob are now on Survival SMP".
