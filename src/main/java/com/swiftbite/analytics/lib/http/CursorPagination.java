package com.swiftbite.analytics.lib.http;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * The Java analogue of order-service's lib/http/pagination/cursor-pagination.ts,
 * adapted for this service's documents. Every aggregate collection here has
 * exactly one document per (dimension, date) — unlike order-service's orders
 * table, where multiple rows can share a timestamp and the TS implementation
 * needs an id tiebreaker, {@code date} alone is already a unique, strictly
 * ordering key once a dimension (restaurantId, branchId, ...) is fixed. So
 * this cursor is just the last-seen date, no id suffix.
 * <p>
 * Compares against the {@code date} field as a plain ISO string — see
 * {@code RestaurantDayDocument}'s javadoc for why it's stored that way
 * rather than as a BSON Date (JVM-local-timezone conversion would silently
 * shift {@code $gte}/{@code $lte} bounds by the host's UTC offset).
 * Lexicographic ordering on {@code "yyyy-MM-dd"} strings already matches
 * chronological order, so {@code $gte}/{@code $lte}/{@code $gt} all work
 * exactly as they would on a real date type.
 */
public final class CursorPagination {

    public static final int DEFAULT_LIMIT = 30;
    public static final int MAX_LIMIT = 100;

    private CursorPagination() {
    }

    /**
     * @param scope        criteria identifying the dimension (e.g. {@code Criteria.where("restaurantId").is(id)}) —
     *                     {@code date} conditions are chained onto it, never combined via {@code $and}
     * @param dateExtractor how to read the sort field back off a fetched document, to build the next cursor
     */
    public static <T> PageResult<T> queryByDate(
            MongoTemplate mongoTemplate,
            Criteria scope,
            Class<T> documentClass,
            LocalDate from,
            LocalDate to,
            String cursor,
            Integer limit,
            Function<T, String> dateExtractor) {

        int boundedLimit = boundLimit(limit);
        boolean hasCursor = cursor != null && !cursor.isBlank();

        // Only touch "date" at all when there's an actual filter — chaining
        // .and("date") with zero follow-up operator calls still adds an
        // empty {date: {}} clause to the query (Criteria.and(key) starts a
        // new criteria entry regardless of whether an operator ever gets
        // set on it), which matches nothing rather than being a no-op.
        Criteria criteria = scope;
        if (from != null || to != null || hasCursor) {
            Criteria dateCriteria = criteria.and("date");
            if (from != null) {
                dateCriteria = dateCriteria.gte(from.toString());
            }
            if (to != null) {
                dateCriteria = dateCriteria.lte(to.toString());
            }
            if (hasCursor) {
                dateCriteria = dateCriteria.gt(cursor);
            }
            criteria = dateCriteria;
        }

        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.ASC, "date"))
                // fetch one extra row — its presence is how hasMore is
                // detected without a separate count query
                .limit(boundedLimit + 1);

        List<T> rows = mongoTemplate.find(query, documentClass);
        boolean hasMore = rows.size() > boundedLimit;
        List<T> data = hasMore ? rows.subList(0, boundedLimit) : rows;
        String nextCursor = hasMore && !data.isEmpty()
                ? dateExtractor.apply(data.get(data.size() - 1))
                : null;

        return new PageResult<>(data, new PaginationMeta(nextCursor, hasMore, data.size()));
    }

    private static int boundLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
