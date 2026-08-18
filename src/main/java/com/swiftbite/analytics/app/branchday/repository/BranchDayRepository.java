package com.swiftbite.analytics.app.branchday.repository;

import com.swiftbite.analytics.app.branchday.document.BranchDayDocument;
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
 * split.
 */
@Repository
public class BranchDayRepository {

    private final MongoTemplate mongoTemplate;

    public BranchDayRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void applyOrderPlaced(
            long branchId, long restaurantId, LocalDate date, String region, String currency,
            long grossRevenue, long deliveryFeeRevenue) {
        Query query = queryFor(branchId, date);
        Update update = identity(branchId, restaurantId, date, region, currency)
                .inc("ordersCount", 1)
                .inc("grossRevenue", grossRevenue)
                .inc("deliveryFeeRevenue", deliveryFeeRevenue)
                .currentDate("updatedAt");
        mongoTemplate.upsert(query, update, BranchDayDocument.class);
    }

    public void applyOrderCancelled(long branchId, long restaurantId, LocalDate date, String region) {
        Query query = queryFor(branchId, date);
        Update update = identity(branchId, restaurantId, date, region, "")
                .inc("ordersCancelled", 1)
                .currentDate("updatedAt");
        mongoTemplate.upsert(query, update, BranchDayDocument.class);
    }

    public void applyOrderDelivered(long branchId, long restaurantId, LocalDate date, String region) {
        Query query = queryFor(branchId, date);
        Update update = identity(branchId, restaurantId, date, region, "")
                .inc("ordersDelivered", 1)
                .currentDate("updatedAt");
        mongoTemplate.upsert(query, update, BranchDayDocument.class);
    }

    public PageResult<BranchDayDocument> findByBranchId(
            long branchId, LocalDate from, LocalDate to, String cursor, Integer limit) {
        Criteria scope = Criteria.where("branchId").is(branchId);
        return CursorPagination.queryByDate(
                mongoTemplate, scope, BranchDayDocument.class, from, to, cursor, limit,
                BranchDayDocument::getDate);
    }

    private Query queryFor(long branchId, LocalDate date) {
        return Query.query(Criteria.where("_id").is(BranchDayDocument.key(branchId, date)));
    }

    private Update identity(long branchId, long restaurantId, LocalDate date, String region, String currencyIfNew) {
        return new Update()
                .setOnInsert("branchId", branchId)
                .setOnInsert("restaurantId", restaurantId)
                .setOnInsert("date", date.toString())
                .setOnInsert("region", region)
                .setOnInsert("currency", currencyIfNew);
    }
}
