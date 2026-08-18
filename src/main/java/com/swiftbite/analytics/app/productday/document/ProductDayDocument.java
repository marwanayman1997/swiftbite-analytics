package com.swiftbite.analytics.app.productday.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Day-grained rollup of one product's sales, scoped per branch — the same
 * product can be listed at multiple branches with different prices
 * (order-service's {@code product_branch_details} table), so branchId is
 * part of the identity, not just an extra field. {@code _id} is
 * {@code "{productId}:{branchId}:{date}"}.
 * <p>
 * No cancelled/delivered counters here — a cancellation voids the whole
 * order, not a single line item, and product-day revenue is recognized at
 * placement (same simplification order.service.ts's own comments describe
 * for order-level revenue).
 * <p>
 * Carries {@code restaurantId} for the same reason
 * {@code BranchDayDocument} does — {@code ProductDayService}'s ownership
 * check verifies a claimed branch-owner's {@code restaurantId} against it
 * once data exists, closing the "any owner can read any branch" gap
 * without a core-service call.
 *
 * @see com.swiftbite.analytics.app.restaurantday.document.RestaurantDayDocument
 *      for why {@code date} is a plain ISO string rather than a BSON Date.
 */
@Document(collection = "agg_product_day")
public class ProductDayDocument {

    @Id
    private String id;

    private long productId;
    private long branchId;
    private long restaurantId;
    private String date;
    private String region;
    private String currency;

    private long unitsSold;
    private long revenue;

    private Instant updatedAt;

    public static String key(long productId, long branchId, LocalDate date) {
        return productId + ":" + branchId + ":" + date;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getProductId() {
        return productId;
    }

    public void setProductId(long productId) {
        this.productId = productId;
    }

    public long getBranchId() {
        return branchId;
    }

    public void setBranchId(long branchId) {
        this.branchId = branchId;
    }

    public long getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(long restaurantId) {
        this.restaurantId = restaurantId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getUnitsSold() {
        return unitsSold;
    }

    public void setUnitsSold(long unitsSold) {
        this.unitsSold = unitsSold;
    }

    public long getRevenue() {
        return revenue;
    }

    public void setRevenue(long revenue) {
        this.revenue = revenue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
