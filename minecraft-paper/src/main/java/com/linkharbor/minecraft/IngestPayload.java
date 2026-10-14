package com.linkharbor.minecraft;

import com.google.gson.JsonObject;

import java.util.Map;
import java.util.UUID;

/** One request body for POST /v1/ingest/{id}. */
final class IngestPayload {

    private final JsonObject json = new JsonObject();

    private IngestPayload() {}

    /**
     * @param event null → state-only update (no push rules fire)
     * @param data  template variables for push rules ({player}, …)
     * @param state the full game_server state (replaces the widget)
     */
    static IngestPayload of(String event, Map<String, String> data, JsonObject state) {
        IngestPayload p = new IngestPayload();
        if (event != null && !event.isEmpty()) {
            p.json.addProperty("event", event);
            // Idempotency key: a retried delivery of the same event is deduplicated server-side.
            p.json.addProperty("event_id", event + "-" + UUID.randomUUID());
        }
        if (data != null && !data.isEmpty()) {
            JsonObject d = new JsonObject();
            data.forEach(d::addProperty);
            p.json.add("data", d);
        }
        if (state != null) p.json.add("state", state);
        return p;
    }

    String body() {
        return json.toString();
    }

    String describe() {
        return json.has("event") ? json.get("event").getAsString() : "state";
    }
}
