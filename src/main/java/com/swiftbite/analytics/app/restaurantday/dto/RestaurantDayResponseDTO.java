package com.swiftbite.analytics.app.restaurantday.dto;

import com.swiftbite.analytics.app.restaurantday.document.RestaurantDayDocument;

/**
 * Mirrors order-service CLAUDE.md §6's response-DTO rule: never return a raw
 * document from a controller. Never exposes Mongo's {@code _id} — the
 * public contract is {@code restaurantId} + {@code date} instead. Money is
 * integer minor units with {@code currency} alongside, dates are ISO 8601.
 */
public class RestaurantDayResponseDTO {

    private String restaurantId;
    private String date;
    private String region;
    private long ordersCount;
    private long ordersCancelled;
    private long ordersDelivered;
    private long grossRevenue;
    private long deliveryFeeRevenue;
    private String currency;

    public static RestaurantDayResponseDTO from(RestaurantDayDocument doc) {
        RestaurantDayResponseDTO dto = new RestaurantDayResponseDTO();
        dto.restaurantId = String.valueOf(doc.getRestaurantId());
        dto.date = doc.getDate();
        dto.region = doc.getRegion();
        dto.ordersCount = doc.getOrdersCount();
        dto.ordersCancelled = doc.getOrdersCancelled();
        dto.ordersDelivered = doc.getOrdersDelivered();
        dto.grossRevenue = doc.getGrossRevenue();
        dto.deliveryFeeRevenue = doc.getDeliveryFeeRevenue();
        dto.currency = doc.getCurrency();
        return dto;
    }

    public String getRestaurantId() {
        return restaurantId;
    }

    public String getDate() {
        return date;
    }

    public String getRegion() {
        return region;
    }

    public long getOrdersCount() {
        return ordersCount;
    }

    public long getOrdersCancelled() {
        return ordersCancelled;
    }

    public long getOrdersDelivered() {
        return ordersDelivered;
    }

    public long getGrossRevenue() {
        return grossRevenue;
    }

    public long getDeliveryFeeRevenue() {
        return deliveryFeeRevenue;
    }

    public String getCurrency() {
        return currency;
    }
}
