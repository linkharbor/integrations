# LinkHarbor Ingest API

The generic way to push data into a LinkHarbor bio page: one endpoint, one secret per integration, plain JSON. This is what every connector in the [LinkHarbor integrations](https://github.com/linkharbor/integrations) family uses, and what you use if you write your own.

## Connect

In the dashboard open Workspace, Integrations, Custom (API). Give the integration a name. You get an Ingest URL in the form `https://dock.at-harbor.com/v1/ingest/{id}` and a 64-character Secret. Both are shown again in the integration's detail panel. Disconnecting and reconnecting issues a new secret.

## Request

```http
POST https://dock.at-harbor.com/v1/ingest/{id}
Authorization: Bearer <secret>
Content-Type: application/json
```

Clients that cannot set the `Authorization` header may send `X-LinkHarbor-Secret: <secret>` instead.

The body is a JSON object of at most 64 KB with at least one of `event` and `state`.

| Field | Required | Meaning |
|---|---|---|
| `event` | no | Free-form event name such as `player.joined`. Folded to lowercase `a-z0-9._-`, at most 80 characters, so `"Player Joined"` becomes `player_joined`. Push rules match on it. Without `event` the event is called `state.updated`. |
| `event_id` | no | Idempotency key, at most 255 characters. Sending the same `event_id` again returns `duplicate: true` and changes nothing, so retries are safe. |
| `data` | no | Object of scalar values that become variables for push templates, such as `{player}`. Lists of scalars become `"a, b"`, nested objects are dropped, at most 40 keys, strings at most 500 characters. |
| `state` | no | The complete widget state. It replaces the previous one; there is no merge. `state.type` selects the card. |

### Response

```json
{ "ok": true, "event_id": 4711, "duplicate": false, "event_type": "player.joined" }
```

| HTTP | `error` | When |
|---|---|---|
| 200 | | processed, also for duplicates |
| 400 | `invalid_json` | the body is not a JSON object |
| 401 | `invalid_secret` | unknown integration or wrong secret; the two are deliberately indistinguishable |
| 401 | `signature_invalid` | `X-LinkHarbor-Signature` is present but wrong |
| 413 | `payload_too_large` | the body is over 64 KB |
| 422 | `validation_failed` | neither `event` nor `state` given, or processing failed; `error` says why |

### Optional signature

If you prefer not to send the secret as a bearer token, add `X-LinkHarbor-Signature: sha256=<hex HMAC-SHA256 of the raw body>` keyed with the integration's signing key, which is shown once at connect time. When the header is present it is verified. When it is absent the bearer secret alone authenticates.

### How often

As often as you like, but every call stores an event row. Send the state when something changed, such as a join or leave, plus a heartbeat every few minutes so the card never goes stale.

## Widget states

### `game_server`

| Field | Type | Limit |
|---|---|---|
| `name` | string | 80 characters |
| `online` | bool; `"yes"` and `1` are accepted too | default `true` |
| `players_online`, `players_max` | integer, 0 or more | `players_online` falls back to the length of `players` |
| `players` | list of strings | 50 names of 32 characters, duplicates removed |
| `version`, `map`, `game` | string | 40, 80 and 60 characters |
| `address` | string | 120 characters, rendered as a copyable code box |
| `description` | string | 200 characters |
| `image_url`, `url` | `http(s)://` URL | 512 characters; without `url` the card has no button |
| `cta` | string | 40 characters, the button label, default "Join server" |

### `status`

| Field | Type | Limit |
|---|---|---|
| `title` | string | 80 characters |
| `state` | one of `online`, `offline`, `busy`, `away`, `maintenance`, `unknown` | badge colour green, red, orange, orange, orange, grey |
| `label` | string | 40 characters; the badge text in the small layout, the caption under the value otherwise |
| `value` | string | 40 characters, the big number such as `"12"` |
| `text` | string | 200 characters |
| `image_url`, `url`, `cta` | as above | |

Unknown fields are dropped. An unknown `type` leaves the widget untouched.

## Push rules

Push rules live in the dashboard under Integrations, Push rules. A rule matches `provider: custom` and your event name. Templates use `{variable}`: everything from `data` plus the scalar state fields such as `{players_online}` and `{name}`. `data` wins when names clash. `{provider_name}` and `{event}` always exist.

Two timing options per rule:

| Option | Effect |
|---|---|
| Minimum pause (`cooldown_sec`) | at most one push per rule every N seconds; events inside the pause are skipped |
| Batch window (`batch_window_sec`) | collect events for N seconds, then send one push. One collected event uses the normal templates. Several use the batch templates with `{count}`, every `{key}` of the last event and `{key_list}`, the distinct values of `key` joined with commas, for example "Steve, Alex, Bob" |

Example, one notification per ten minutes and at most every five minutes:

```
title:         {player} joined
body:          {players_online} players on {name}
batch window:  10 min
batch title:   {count} players joined
batch body:    {player_list} are now on {name}
minimum pause: 5 min
```

Result: "3 players joined" / "Steve, Alex, Bob are now on Survival SMP".
