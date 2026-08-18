package com.swiftbite.analytics.app.restaurantday.repository;

import com.swiftbite.analytics.app.restaurantday.document.RestaurantDayDocument;
import com.swiftbite.analytics.lib.http.CursorPagination;
import com.swiftbite.analytics.lib.http.PageResult;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

/**
 * Explicit methods (not Spring Data's auto-derived queries) so the atomic
 * {@code $inc} upsert stays reviewable — mirrors order-service's
 * {@code <module>.repo.ts} convention of exported functions over an
 * auto-magic layer. Every method upserts: a first-ever event for a given
 * day creates the document via {@code setOnInsert}, a later one just
 * increments — same shape whether it's called once or a thousand times a
 * second for the same day.
 */
@Repository
public class RestaurantDayRepository {

    private final MongoTemplate mongoTemplate;

    public RestaurantDayRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void applyOrderPlaced(
            long restaurantId, LocalDate date, String region, String currency,
            long grossRevenue, long deliveryFeeRevenue) {
        Query query = queryFor(restaurantId, date);
        Update update = identity(restaurantId, date, region, currency)
                .inc("ordersCount", 1)
                .inc("grossRevenue", grossRevenue)
                .inc("deliveryFeeRevenue", deliveryFeeRevenue)
                .currentDate("updatedAt");
        mongoTemplate.upsert(query, update, RestaurantDayDocument.class);
    }

    public void applyOrderCancelled(long restaurantId, LocalDate date, String region) {
        Query query = queryFor(restaurantId, date);
        // currency is unknown if this document doesn't exist yet (shouldn't
        // normally happen — order.placed always precedes order.status_changed
        // for the same order — but the upsert must stay well-formed either way).
        Update update = identity(restaurantId, date, region, "")
                .inc("ordersCancelled", 1)
                .currentDate("updatedAt");
        mongoTemplate.upsert(query, update, RestaurantDayDocument.class);
    }

    public void applyOrderDelivered(long restaurantId, LocalDate date, String region) {
        Query query = queryFor(restaurantId, date);
        Update update = identity(restaurantId, date, region, "")
                .inc("ordersDelivered", 1)
                .currentDate("updatedAt");
        mongoTemplate.upsert(query, update, RestaurantDayDocument.class);
    }

    public PageResult<RestaurantDayDocument> findByRestaurantId(
            long restaurantId, LocalDate from, LocalDate to, String cursor, Integer limit) {
        Criteria scope = Criteria.where("restaurantId").is(restaurantId);
        return CursorPagination.queryByDate(
                mongoTemplate, scope, RestaurantDayDocument.class, from, to, cursor, limit,
                RestaurantDayDocument::getDate);
    }

    private Query queryFor(long restaurantId, LocalDate date) {
        return Query.query(Criteria.where("_id").is(RestaurantDayDocument.key(restaurantId, date)));
    }

    // Only the non-incremented identity fields go through setOnInsert — a
    // field can't be targeted by both $inc and $setOnInsert in the same
    // update (MongoDB rejects it as an operator conflict), and $inc already
    // treats a missing counter field as starting from 0 on its own, upsert
    // or not, so the counters need no explicit zero-init here.
    private Update identity(long restaurantId, LocalDate date, String region, String currencyIfNew) {
        return new Update()
                .setOnInsert("restaurantId", restaurantId)
                .setOnInsert("date", date.toString())
                .setOnInsert("region", region)
                .setOnInsert("currency", currencyIfNew);
    }
}
