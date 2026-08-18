package com.swiftbite.analytics.app.platformday.repository;

import com.swiftbite.analytics.app.platformday.document.PlatformDayDocument;
import com.swiftbite.analytics.lib.http.CursorPagination;
import com.swiftbite.analytics.lib.http.PageResult;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

/**
 * Same shape as {@link com.swiftbite.analytics.app.restaurantday.repository.RestaurantDayRepository}
 * — see that class's javadoc for the reasoning behind the $inc/$setOnInsert
 * split. Keyed by {@code (date, currency)} — see
 * {@link PlatformDayDocument}'s javadoc for why currency is part of the key.
 */
@Repository
public class PlatformDayRepository {

    private final MongoTemplate mongoTemplate;

    public PlatformDayRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void applyOrderPlaced(LocalDate date, String currency, long grossRevenue, long deliveryFeeRevenue) {
        Query query = queryFor(date, currency);
        Update update = identity(date, currency)
                .inc("ordersCount", 1)
                .inc("grossRevenue", grossRevenue)
                .inc("deliveryFeeRevenue", deliveryFeeRevenue)
                .currentDate("updatedAt");
        mongoTemplate.upsert(query, update, PlatformDayDocument.class);
    }

    public void applyOrderCancelled(LocalDate date, String currency) {
        Query query = queryFor(date, currency);
        Update update = identity(date, currency)
                .inc("ordersCancelled", 1)
                .currentDate("updatedAt");
        mongoTemplate.upsert(query, update, PlatformDayDocument.class);
    }

    public void applyOrderDelivered(LocalDate date, String currency) {
        Query query = queryFor(date, currency);
        Update update = identity(date, currency)
                .inc("ordersDelivered", 1)
                .currentDate("updatedAt");
        mongoTemplate.upsert(query, update, PlatformDayDocument.class);
    }

    public PageResult<PlatformDayDocument> findByCurrency(
            String currency, LocalDate from, LocalDate to, String cursor, Integer limit) {
        Criteria scope = Criteria.where("currency").is(currency);
        return CursorPagination.queryByDate(
                mongoTemplate, scope, PlatformDayDocument.class, from, to, cursor, limit,
                PlatformDayDocument::getDate);
    }

    private Query queryFor(LocalDate date, String currency) {
        return Query.query(Criteria.where("_id").is(PlatformDayDocument.key(date, currency)));
    }

    private Update identity(LocalDate date, String currency) {
        return new Update()
                .setOnInsert("date", date.toString())
                .setOnInsert("currency", currency);
    }
}
