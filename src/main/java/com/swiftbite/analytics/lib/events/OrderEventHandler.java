package com.swiftbite.analytics.lib.events;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Extension point implemented by {@code app/} modules (Phase 3 onward) to
 * react to one order-event type. {@code lib/} only knows this interface —
 * never a concrete app/ class — so the DI container wires implementations in
 * at boot (Spring auto-collects every bean of this type into the listener's
 * {@code List<OrderEventHandler>}). This is this codebase's equivalent of
 * the "DI tokens at boot" exception CLAUDE.md §3 carves out of the
 * lib/-must-not-import-app/ rule, and it's what {@code LayeringTest} enforces.
 */
public interface OrderEventHandler {

    String eventType();

    void handle(JsonNode payload);
}
