package com.swiftbite.analytics.app.platformday.dto;

import com.swiftbite.analytics.app.platformday.document.PlatformDayDocument;

public class PlatformDayResponseDTO {

    private String date;
    private String currency;
    private long ordersCount;
    private long ordersCancelled;
    private long ordersDelivered;
    private long grossRevenue;
    private long deliveryFeeRevenue;

    public static PlatformDayResponseDTO from(PlatformDayDocument doc) {
        PlatformDayResponseDTO dto = new PlatformDayResponseDTO();
        dto.date = doc.getDate();
        dto.currency = doc.getCurrency();
        dto.ordersCount = doc.getOrdersCount();
        dto.ordersCancelled = doc.getOrdersCancelled();
        dto.ordersDelivered = doc.getOrdersDelivered();
        dto.grossRevenue = doc.getGrossRevenue();
        dto.deliveryFeeRevenue = doc.getDeliveryFeeRevenue();
        return dto;
    }

    public String getDate() {
        return date;
    }

    public String getCurrency() {
        return currency;
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
}
