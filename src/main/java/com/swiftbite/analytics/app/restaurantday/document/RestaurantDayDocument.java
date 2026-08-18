package com.swiftbite.analytics.app.restaurantday.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Day-grained rollup of one restaurant's order activity. {@code _id} is
 * {@code "{restaurantId}:{date}"} — unique-by-construction, so no separate
 * uniqueness index is needed (Mongo already indexes {@code _id}).
 * <p>
 * {@code grossRevenue} is the subtotal (product revenue only — what the
 * restaurant is owed before commission), kept separate from
 * {@code deliveryFeeRevenue} because that's a different revenue stream
 * (split with the delivery agent) — mirrors how order-service's own
 * settlement logic (delivery.service.ts's settleDelivery) treats them as
 * two distinct pools, never summed together.
 * <p>
 * {@code date} is stored as an ISO-8601 string ({@code "yyyy-MM-dd"}), not
 * a BSON Date — Spring Data's default {@code LocalDate}-to-{@code Date}
 * conversion uses the JVM's local default timezone, not UTC, so the same
 * calendar date would serialize to a different instant (and a different
 * {@code $gte}/{@code $lte} query match) depending on which timezone the
 * writing/reading process happens to run in. A plain date-only string has
 * no timezone to get wrong, and lexicographic ordering on
 * {@code "yyyy-MM-dd"} strings already matches chronological order.
 */
@Document(collection = "agg_restaurant_day")
public class RestaurantDayDocument {

    @Id
    private String id;

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

    public static String key(long restaurantId, LocalDate date) {
        return restaurantId + ":" + date;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
