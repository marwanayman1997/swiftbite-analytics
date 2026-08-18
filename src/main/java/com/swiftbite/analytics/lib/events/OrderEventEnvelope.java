package com.swiftbite.analytics.lib.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Mirrors order-service's {@code lib/core-events/types.ts} CoreEventEnvelope
 * — same shape order-service's own outbox publishes (Phase 0). {@code payload}
 * stays an untyped {@link JsonNode} here; its shape depends on eventType, so
 * each {@link OrderEventHandler} parses the fields it expects.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderEventEnvelope(String eventId, String eventType, String occurredAt, JsonNode payload) {
}
