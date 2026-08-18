package com.swiftbite.analytics.app.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftbite.analytics.app.branchday.repository.BranchDayRepository;
import com.swiftbite.analytics.app.platformday.repository.PlatformDayRepository;
import com.swiftbite.analytics.app.productday.repository.ProductDayRepository;
import com.swiftbite.analytics.app.restaurantday.repository.RestaurantDayRepository;
import com.swiftbite.analytics.lib.events.OrderEventHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Fans one {@code order.placed} event out to all four aggregate
 * collections — the one place that knows a single order event touches
 * restaurant, branch, per-line-item product, and platform granularities,
 * keeping each {@code <module>day} repository single-purpose (plan Phase 3).
 * <p>
 * {@code grossRevenue} = subtotal, {@code deliveryFeeRevenue} = deliveryFee
 * — see {@code RestaurantDayDocument}'s javadoc for why they're tracked
 * separately rather than summed.
 */
@Component
public class OrderPlacedIngestionHandler implements OrderEventHandler {

    private final RestaurantDayRepository restaurantDayRepository;
    private final BranchDayRepository branchDayRepository;
    private final ProductDayRepository productDayRepository;
    private final PlatformDayRepository platformDayRepository;

    public OrderPlacedIngestionHandler(
            RestaurantDayRepository restaurantDayRepository,
            BranchDayRepository branchDayRepository,
            ProductDayRepository productDayRepository,
            PlatformDayRepository platformDayRepository) {
        this.restaurantDayRepository = restaurantDayRepository;
        this.branchDayRepository = branchDayRepository;
        this.productDayRepository = productDayRepository;
        this.platformDayRepository = platformDayRepository;
    }

    @Override
    public String eventType() {
        return "order.placed";
    }

    @Override
    public void handle(JsonNode payload) {
        long restaurantId = payload.get("restaurantId").asLong();
        long branchId = payload.get("branchId").asLong();
        String region = payload.get("region").asText();
        String currency = payload.get("currency").asText();
        long subtotal = payload.get("subtotal").asLong();
        long deliveryFee = payload.get("deliveryFee").asLong();
        LocalDate date = dateOf(payload.get("createdAt").asText());

        restaurantDayRepository.applyOrderPlaced(restaurantId, date, region, currency, subtotal, deliveryFee);
        branchDayRepository.applyOrderPlaced(branchId, restaurantId, date, region, currency, subtotal, deliveryFee);
        platformDayRepository.applyOrderPlaced(date, currency, subtotal, deliveryFee);

        for (JsonNode item : payload.path("items")) {
            long productId = item.get("productId").asLong();
            long quantity = item.get("quantity").asLong();
            long lineTotal = item.get("lineTotal").asLong();
            productDayRepository.applyOrderPlaced(
                    productId, branchId, restaurantId, date, region, currency, quantity, lineTotal);
        }
    }

    private LocalDate dateOf(String isoInstant) {
        return Instant.parse(isoInstant).atZone(ZoneOffset.UTC).toLocalDate();
    }
}
