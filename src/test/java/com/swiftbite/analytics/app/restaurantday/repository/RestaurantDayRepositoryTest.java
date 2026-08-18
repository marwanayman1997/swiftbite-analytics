package com.swiftbite.analytics.app.restaurantday.repository;

import com.swiftbite.analytics.app.restaurantday.document.RestaurantDayDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The crux of the whole aggregate-repository design: a second call for the
 * same (restaurantId, date) must accumulate onto the existing document via
 * $inc, not overwrite it — against a real Mongo, not a mock, since this is
 * exactly the kind of thing an in-memory fake would get right for the wrong
 * reasons.
 */
@SpringBootTest
class RestaurantDayRepositoryTest {

    private static final long RESTAURANT_ID = 900_101;
    private static final LocalDate DATE = LocalDate.now();

    @Autowired
    private RestaurantDayRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanUp() {
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(RestaurantDayDocument.key(RESTAURANT_ID, DATE))),
                RestaurantDayDocument.class);
    }

    @Test
    void secondCallAccumulatesOntoTheFirst() {
        repository.applyOrderPlaced(RESTAURANT_ID, DATE, "eg", "EGP", 100, 20);
        repository.applyOrderPlaced(RESTAURANT_ID, DATE, "eg", "EGP", 150, 20);

        RestaurantDayDocument doc = mongoTemplate.findById(
                RestaurantDayDocument.key(RESTAURANT_ID, DATE), RestaurantDayDocument.class);

        assertThat(doc).isNotNull();
        assertThat(doc.getOrdersCount()).isEqualTo(2);
        assertThat(doc.getGrossRevenue()).isEqualTo(250);
        assertThat(doc.getDeliveryFeeRevenue()).isEqualTo(40);
        assertThat(doc.getRestaurantId()).isEqualTo(RESTAURANT_ID);
        assertThat(doc.getCurrency()).isEqualTo("EGP");
    }

    @Test
    void cancelledAndDeliveredCountersAreIndependentOfPlacedCount() {
        repository.applyOrderPlaced(RESTAURANT_ID, DATE, "eg", "EGP", 100, 20);
        repository.applyOrderCancelled(RESTAURANT_ID, DATE, "eg");
        repository.applyOrderDelivered(RESTAURANT_ID, DATE, "eg");
        repository.applyOrderDelivered(RESTAURANT_ID, DATE, "eg");

        RestaurantDayDocument doc = mongoTemplate.findById(
                RestaurantDayDocument.key(RESTAURANT_ID, DATE), RestaurantDayDocument.class);

        assertThat(doc).isNotNull();
        assertThat(doc.getOrdersCount()).isEqualTo(1);
        assertThat(doc.getOrdersCancelled()).isEqualTo(1);
        assertThat(doc.getOrdersDelivered()).isEqualTo(2);
        // The currency established by the first (order.placed) call must
        // survive later setOnInsert-only calls that pass "" — this is the
        // exact scenario the identity()-only-on-insert split protects.
        assertThat(doc.getCurrency()).isEqualTo("EGP");
    }
}
