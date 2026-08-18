package com.swiftbite.analytics.app.platformday.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Platform-wide day-grained totals. {@code _id} is
 * {@code "{date}:{currency}"} — currency is part of the identity, not just
 * a field, because summing money across regions with different currencies
 * (EGP, SAR, ...) into one running total would be meaningless. In this
 * system currency is effectively determined by region, so this also keeps
 * one document per region-day without needing a separate region key.
 *
 * @see com.swiftbite.analytics.app.restaurantday.document.RestaurantDayDocument
 *      for why {@code date} is a plain ISO string rather than a BSON Date.
 */
@Document(collection = "agg_platform_day")
public class PlatformDayDocument {

    @Id
    private String id;

    private String date;
    private String currency;

    private long ordersCount;
    private long ordersCancelled;
    private long ordersDelivered;
    private long grossRevenue;
    private long deliveryFeeRevenue;

    private Instant updatedAt;

    public static String key(LocalDate date, String currency) {
        return date + ":" + currency;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
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
