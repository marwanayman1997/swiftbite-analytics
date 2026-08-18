package com.swiftbite.analytics.app.productday.repository;

import com.swiftbite.analytics.app.productday.document.ProductDayDocument;
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
 * split. Only one write path: product-day has no cancelled/delivered
 * counters (see {@link ProductDayDocument}'s javadoc).
 */
@Repository
public class ProductDayRepository {

    private final MongoTemplate mongoTemplate;

    public ProductDayRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void applyOrderPlaced(
            long productId, long branchId, long restaurantId, LocalDate date, String region, String currency,
            long unitsSold, long revenue) {
        Query query = Query.query(Criteria.where("_id").is(ProductDayDocument.key(productId, branchId, date)));
        Update update = new Update()
                .setOnInsert("productId", productId)
                .setOnInsert("branchId", branchId)
                .setOnInsert("restaurantId", restaurantId)
                .setOnInsert("date", date.toString())
                .setOnInsert("region", region)
                .setOnInsert("currency", currency)
                .inc("unitsSold", unitsSold)
                .inc("revenue", revenue)
                .currentDate("updatedAt");
        mongoTemplate.upsert(query, update, ProductDayDocument.class);
    }

    public PageResult<ProductDayDocument> findByProductAndBranch(
            long productId, long branchId, LocalDate from, LocalDate to, String cursor, Integer limit) {
        Criteria scope = Criteria.where("productId").is(productId).and("branchId").is(branchId);
        return CursorPagination.queryByDate(
                mongoTemplate, scope, ProductDayDocument.class, from, to, cursor, limit,
                ProductDayDocument::getDate);
    }
}
