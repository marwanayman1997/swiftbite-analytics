package com.swiftbite.analytics.app.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftbite.analytics.app.branchday.document.BranchDayDocument;
import com.swiftbite.analytics.app.platformday.document.PlatformDayDocument;
import com.swiftbite.analytics.app.productday.document.ProductDayDocument;
import com.swiftbite.analytics.app.restaurantday.document.RestaurantDayDocument;
import com.swiftbite.analytics.lib.config.AnalyticsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Exercises the real {@code OrderPlacedIngestionHandler} /
 * {@code OrderStatusChangedIngestionHandler} beans end-to-end: publish a
 * real envelope onto RabbitMQ, let {@code OrderEventsListener} dispatch it,
 * assert the resulting document in real Mongo — same "test the real thing"
 * approach as Phase 0/2, not mocked repositories.
 * <p>
 * Uses its own exchange/queue/DLQ (see
 * {@code OrderEventsListenerIntegrationTest}'s javadoc for why — the same
 * cross-context fan-out/dedupe-race applies here).
 */
@SpringBootTest(properties = {
        "analytics.rabbitmq.order-events.exchange=order.events.ingestion-itest",
        "analytics.rabbitmq.order-events.queue=analytics-service.order-events.ingestion-itest",
        "analytics.rabbitmq.order-events.dlx=order.events.ingestion-itest.dlx",
        "analytics.rabbitmq.order-events.dlq=analytics-service.order-events.dlq.ingestion-itest"
})
class OrderIngestionIntegrationTest {

    private static final long RESTAURANT_ID = 900_001;
    private static final long BRANCH_ID = 900_002;
    private static final long PRODUCT_ID = 900_003;
    private static final String REGION = "eg";
    private static final String CURRENCY = "EGP";
    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private AnalyticsProperties properties;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mongoTemplate.remove(idQuery(RestaurantDayDocument.key(RESTAURANT_ID, TODAY)), RestaurantDayDocument.class);
        mongoTemplate.remove(idQuery(BranchDayDocument.key(BRANCH_ID, TODAY)), BranchDayDocument.class);
        mongoTemplate.remove(idQuery(ProductDayDocument.key(PRODUCT_ID, BRANCH_ID, TODAY)), ProductDayDocument.class);
        mongoTemplate.remove(idQuery(PlatformDayDocument.key(TODAY, CURRENCY)), PlatformDayDocument.class);

        // Same two guards as OrderEventsListenerIntegrationTest — see that
        // class's @BeforeEach for why both are needed.
        rabbitAdmin.initialize();
        MessageListenerContainer container = listenerRegistry.getListenerContainer("orderEventsListener");
        await().atMost(ofSeconds(10)).until(
                () -> ((SimpleMessageListenerContainer) container).getActiveConsumerCount() >= 1);
    }

    @Test
    void orderPlacedUpsertsAllFourCollections() throws Exception {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("productId", PRODUCT_ID);
        item.put("quantity", 2);
        item.put("lineTotal", 300);
        item.put("unitPriceSnapshot", 150);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("restaurantId", RESTAURANT_ID);
        payload.put("branchId", BRANCH_ID);
        payload.put("region", REGION);
        payload.put("currency", CURRENCY);
        payload.put("subtotal", 300);
        payload.put("deliveryFee", 50);
        payload.put("createdAt", Instant.now().toString());
        payload.putArray("items").add(item);

        publish("order.placed", envelope("order.placed", payload));

        await().atMost(ofSeconds(8)).untilAsserted(() -> {
            RestaurantDayDocument restaurantDay = mongoTemplate.findById(
                    RestaurantDayDocument.key(RESTAURANT_ID, TODAY), RestaurantDayDocument.class);
            assertThat(restaurantDay).isNotNull();
            assertThat(restaurantDay.getOrdersCount()).isEqualTo(1);
            assertThat(restaurantDay.getGrossRevenue()).isEqualTo(300);
            assertThat(restaurantDay.getDeliveryFeeRevenue()).isEqualTo(50);
            assertThat(restaurantDay.getCurrency()).isEqualTo(CURRENCY);

            BranchDayDocument branchDay = mongoTemplate.findById(
                    BranchDayDocument.key(BRANCH_ID, TODAY), BranchDayDocument.class);
            assertThat(branchDay).isNotNull();
            assertThat(branchDay.getOrdersCount()).isEqualTo(1);
            assertThat(branchDay.getRestaurantId()).isEqualTo(RESTAURANT_ID);

            ProductDayDocument productDay = mongoTemplate.findById(
                    ProductDayDocument.key(PRODUCT_ID, BRANCH_ID, TODAY), ProductDayDocument.class);
            assertThat(productDay).isNotNull();
            assertThat(productDay.getUnitsSold()).isEqualTo(2);
            assertThat(productDay.getRevenue()).isEqualTo(300);

            PlatformDayDocument platformDay = mongoTemplate.findById(
                    PlatformDayDocument.key(TODAY, CURRENCY), PlatformDayDocument.class);
            assertThat(platformDay).isNotNull();
            assertThat(platformDay.getOrdersCount()).isEqualTo(1);
            assertThat(platformDay.getGrossRevenue()).isEqualTo(300);
        });
    }

    @Test
    void orderStatusChangedIncrementsCancelledCounter() throws Exception {
        // Seed the placement day's document first, same as production order
        // — order.placed always precedes order.status_changed for one order.
        ObjectNode item = objectMapper.createObjectNode();
        item.put("productId", PRODUCT_ID);
        item.put("quantity", 1);
        item.put("lineTotal", 150);
        item.put("unitPriceSnapshot", 150);

        ObjectNode placedPayload = objectMapper.createObjectNode();
        placedPayload.put("restaurantId", RESTAURANT_ID);
        placedPayload.put("branchId", BRANCH_ID);
        placedPayload.put("region", REGION);
        placedPayload.put("currency", CURRENCY);
        placedPayload.put("subtotal", 150);
        placedPayload.put("deliveryFee", 50);
        String orderCreatedAt = Instant.now().toString();
        placedPayload.put("createdAt", orderCreatedAt);
        placedPayload.putArray("items").add(item);

        publish("order.placed", envelope("order.placed", placedPayload));
        await().atMost(ofSeconds(8)).untilAsserted(() -> assertThat(mongoTemplate.findById(
                RestaurantDayDocument.key(RESTAURANT_ID, TODAY), RestaurantDayDocument.class)).isNotNull());

        ObjectNode statusPayload = objectMapper.createObjectNode();
        statusPayload.put("orderPublicId", "test-order");
        statusPayload.put("region", REGION);
        statusPayload.put("restaurantId", RESTAURANT_ID);
        statusPayload.put("branchId", BRANCH_ID);
        statusPayload.put("status", "cancelled");
        statusPayload.put("updatedAt", Instant.now().toString());
        statusPayload.put("orderCreatedAt", orderCreatedAt);
        statusPayload.put("currency", CURRENCY);

        publish("order.status_changed", envelope("order.status_changed", statusPayload));

        await().atMost(ofSeconds(8)).untilAsserted(() -> {
            RestaurantDayDocument restaurantDay = mongoTemplate.findById(
                    RestaurantDayDocument.key(RESTAURANT_ID, TODAY), RestaurantDayDocument.class);
            assertThat(restaurantDay).isNotNull();
            assertThat(restaurantDay.getOrdersCancelled()).isEqualTo(1);
            // Attributed to the placement day's document, not left dangling
            // on a would-be "status change day" document.
            assertThat(restaurantDay.getOrdersCount()).isEqualTo(1);

            BranchDayDocument branchDay = mongoTemplate.findById(
                    BranchDayDocument.key(BRANCH_ID, TODAY), BranchDayDocument.class);
            assertThat(branchDay).isNotNull();
            assertThat(branchDay.getOrdersCancelled()).isEqualTo(1);

            PlatformDayDocument platformDay = mongoTemplate.findById(
                    PlatformDayDocument.key(TODAY, CURRENCY), PlatformDayDocument.class);
            assertThat(platformDay).isNotNull();
            assertThat(platformDay.getOrdersCancelled()).isEqualTo(1);
        });
    }

    private Query idQuery(String id) {
        return Query.query(Criteria.where("_id").is(id));
    }

    private void publish(String routingKey, String jsonBody) {
        String exchange = properties.getRabbitmq().getOrderEvents().getExchange();
        Message message = new Message(jsonBody.getBytes(StandardCharsets.UTF_8), new MessageProperties());
        rabbitTemplate.send(exchange, routingKey, message);
    }

    private String envelope(String eventType, ObjectNode payload) throws Exception {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.set("payload", payload);
        return objectMapper.writeValueAsString(envelope);
    }
}
