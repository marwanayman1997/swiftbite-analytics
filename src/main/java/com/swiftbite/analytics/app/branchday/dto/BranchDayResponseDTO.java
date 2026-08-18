package com.swiftbite.analytics.app.branchday.dto;

import com.swiftbite.analytics.app.branchday.document.BranchDayDocument;

public class BranchDayResponseDTO {

    private String branchId;
    private String restaurantId;
    private String date;
    private String region;
    private long ordersCount;
    private long ordersCancelled;
    private long ordersDelivered;
    private long grossRevenue;
    private long deliveryFeeRevenue;
    private String currency;

    public static BranchDayResponseDTO from(BranchDayDocument doc) {
        BranchDayResponseDTO dto = new BranchDayResponseDTO();
        dto.branchId = String.valueOf(doc.getBranchId());
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

    public String getBranchId() {
        return branchId;
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
