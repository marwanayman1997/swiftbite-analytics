package com.swiftbite.analytics.app.branchday.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Day-grained rollup of one branch's order activity. {@code _id} is
 * {@code "{branchId}:{date}"}. Carries {@code restaurantId} alongside
 * {@code branchId} so a restaurant-level rollup view can group by it
 * without a lookup back to core-service.
 *
 * @see com.swiftbite.analytics.app.restaurantday.document.RestaurantDayDocument
 *      for why grossRevenue and deliveryFeeRevenue are separate fields, and
 *      why {@code date} is a plain ISO string rather than a BSON Date.
 */
@Document(collection = "agg_branch_day")
public class BranchDayDocument {

    @Id
    private String id;

    private long branchId;
    private long restaurantId;
    private String date;
    private String region;
    private String currency;

    private long ordersCount;
    private long ordersCancelled;
    private long ordersDelivered;
    private long grossRevenue;
    private long deliveryFeeRevenue;

    private Instant updatedAt;

    public static String key(long branchId, LocalDate date) {
        return branchId + ":" + date;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public long getOrdersCount() {
        return ordersCount;
    }

    public void setOrdersCount(long ordersCount) {
        this.ordersCount = ordersCount;
    }

    public long getOrdersCancelled() {
        return ordersCancelled;
    }

    public void setOrdersCancelled(long ordersCancelled) {
        this.ordersCancelled = ordersCancelled;
    }

    public long getOrdersDelivered() {
        return ordersDelivered;
    }

    public void setOrdersDelivered(long ordersDelivered) {
        this.ordersDelivered = ordersDelivered;
    }

    public long getGrossRevenue() {
        return grossRevenue;
    }

    public void setGrossRevenue(long grossRevenue) {
        this.grossRevenue = grossRevenue;
    }

    public long getDeliveryFeeRevenue() {
        return deliveryFeeRevenue;
    }

    public void setDeliveryFeeRevenue(long deliveryFeeRevenue) {
        this.deliveryFeeRevenue = deliveryFeeRevenue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
