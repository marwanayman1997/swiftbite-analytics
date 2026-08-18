package com.swiftbite.analytics.app.productday.dto;

import com.swiftbite.analytics.app.productday.document.ProductDayDocument;

public class ProductDayResponseDTO {

    private String productId;
    private String branchId;
    private String date;
    private String region;
    private long unitsSold;
    private long revenue;
    private String currency;

    public static ProductDayResponseDTO from(ProductDayDocument doc) {
        ProductDayResponseDTO dto = new ProductDayResponseDTO();
        dto.productId = String.valueOf(doc.getProductId());
        dto.branchId = String.valueOf(doc.getBranchId());
        dto.date = doc.getDate();
        dto.region = doc.getRegion();
        dto.unitsSold = doc.getUnitsSold();
        dto.revenue = doc.getRevenue();
        dto.currency = doc.getCurrency();
        return dto;
    }

    public String getProductId() {
        return productId;
    }

    public String getBranchId() {
        return branchId;
    }

    public String getDate() {
        return date;
    }

    public String getRegion() {
        return region;
    }

    public long getUnitsSold() {
        return unitsSold;
    }

    public long getRevenue() {
        return revenue;
    }

    public String getCurrency() {
        return currency;
    }
}
