package com.swiftbite.analytics.app.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftbite.analytics.app.branchday.repository.BranchDayRepository;
import com.swiftbite.analytics.app.platformday.repository.PlatformDayRepository;
import com.swiftbite.analytics.app.restaurantday.repository.RestaurantDayRepository;
import com.swiftbite.analytics.lib.events.OrderEventHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Only the two terminal statuses that feed a day-grain counter
 * (cancelled/delivered) do anything here — every other transition
 * (accepted, preparing, ready, ...) is order-service's operational
 * lifecycle and has no analytics counter, so this handler just returns
 * (the message is still acked — this is a registered handler ignoring an
 * uninteresting payload, not the listener's "no handler" path).
 * <p>
 * Attributes the counter to the order's placement day
 * ({@code orderCreatedAt}), not the day the status changed, so it lands in
 * the same document as the placement count it's a fraction of — a same-day
 * transition in practice anyway, since order-service's own cancellation
 * window is 60 seconds and deliveries typically complete within hours.
 */
@Component
public class OrderStatusChangedIngestionHandler implements OrderEventHandler {

    private static final String CANCELLED = "cancelled";
    private static final String DELIVERED = "delivered";

    private final RestaurantDayRepository restaurantDayRepository;
    private final BranchDayRepository branchDayRepository;
    private final PlatformDayRepository platformDayRepository;

    public OrderStatusChangedIngestionHandler(
            RestaurantDayRepository restaurantDayRepository,
            BranchDayRepository branchDayRepository,
            PlatformDayRepository platformDayRepository) {
        this.restaurantDayRepository = restaurantDayRepository;
        this.branchDayRepository = branchDayRepository;
        this.platformDayRepository = platformDayRepository;
    }

    @Override
    public String eventType() {
        return "order.status_changed";
    }

    @Override
    public void handle(JsonNode payload) {
        String status = payload.get("status").asText();
        if (!status.equals(CANCELLED) && !status.equals(DELIVERED)) {
            return;
        }

        long restaurantId = payload.get("restaurantId").asLong();
        long branchId = payload.get("branchId").asLong();
        String region = payload.get("region").asText();
        String currency = payload.get("currency").asText();
        LocalDate date = dateOf(payload.get("orderCreatedAt").asText());

        if (status.equals(CANCELLED)) {
            restaurantDayRepository.applyOrderCancelled(restaurantId, date, region);
            branchDayRepository.applyOrderCancelled(branchId, restaurantId, date, region);
            platformDayRepository.applyOrderCancelled(date, currency);
        } else {
            restaurantDayRepository.applyOrderDelivered(restaurantId, date, region);
            branchDayRepository.applyOrderDelivered(branchId, restaurantId, date, region);
            platformDayRepository.applyOrderDelivered(date, currency);
        }
    }

    private LocalDate dateOf(String isoInstant) {
        return Instant.parse(isoInstant).atZone(ZoneOffset.UTC).toLocalDate();
    }
}
