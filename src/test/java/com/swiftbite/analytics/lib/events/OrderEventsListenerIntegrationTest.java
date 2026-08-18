package com.swiftbite.analytics.lib.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftbite.analytics.lib.config.AnalyticsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;

/**
 * Exercises {@link OrderEventsListener} against a real local RabbitMQ + Redis
 * (same infra Phase 0/1 already depend on) — not mocked — because the thing
 * actually worth verifying here is wiring: topology declared correctly,
 * dedupe backed by real Redis TTLs, and DLQ routing backed by a real
 * dead-letter exchange, not just that Java method calls happen in the right
 * order.
 * <p>
 * Uses its own exchange/queue/DLX/DLQ names — not just the queue — rather
 * than the configured production ones. {@link AnalyticsServiceApplicationTests}'
 * plain {@code @SpringBootTest} context also boots a real
 * {@code OrderEventsListener} bound to the production exchange; a queue-only
 * override still leaves both contexts bound to the *same* exchange, so
 * publishing one message fans out a copy to both queues, and both listeners
 * race on the *same* Redis dedupe key (Redis isn't test-isolated either) —
 * whichever consumer wins marks the eventId "seen" and the other silently
 * skips it, which made this flaky in a way that pointed at a timing issue
 * but was actually cross-context interference. Isolating the exchange
 * removes that fan-out; isolating the DLX too (not just the DLQ name)
 * matters for the same reason — a shared DLX still fans a nacked message
 * out to every queue bound to it, production DLQ included.
 */
@SpringBootTest(properties = {
        "analytics.rabbitmq.order-events.exchange=order.events.itest",
        "analytics.rabbitmq.order-events.queue=analytics-service.order-events.itest",
        "analytics.rabbitmq.order-events.dlx=order.events.itest.dlx",
        "analytics.rabbitmq.order-events.dlq=analytics-service.order-events.dlq.itest"
})
class OrderEventsListenerIntegrationTest {

    // A stub handler, registered only for this test — this class tests the
    // listener's own dispatch *mechanics* (dedupe, ack/nack, DLQ routing),
    // not any real business event, so it deliberately uses a synthetic
    // eventType rather than "order.placed": Phase 3 added a real handler for
    // that eventType (OrderPlacedIngestionHandler), and @TestConfiguration
    // beans are additive to the main context, not a replacement — reusing
    // "order.placed" here would register two handlers for the same
    // eventType and crash the listener's handler-map construction.
    // OrderIngestionIntegrationTest is what exercises the real handlers.
    @TestConfiguration
    static class StubHandlerConfig {
        @Bean
        OrderEventHandler stubOrderPlacedHandler() {
            return new OrderEventHandler() {
                @Override
                public String eventType() {
                    return "test.dispatch";
                }

                @Override
                public void handle(JsonNode payload) {
                    if (payload.path("shouldFail").asBoolean(false)) {
                        throw new RuntimeException("simulated handler failure");
                    }
                    RECEIVED.add(payload);
                    RECEIVED_COUNT.incrementAndGet();
                }
            };
        }
    }

    private static final List<JsonNode> RECEIVED = new CopyOnWriteArrayList<>();
    private static final AtomicInteger RECEIVED_COUNT = new AtomicInteger();

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AnalyticsProperties properties;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        RECEIVED.clear();
        RECEIVED_COUNT.set(0);
        // Guards against a startup race on the very first test to run against
        // this class's dedicated (brand new) queue/bindings: RabbitAdmin
        // declares the topology asynchronously off ContextRefreshedEvent, and
        // publishing before that lands means the message has nowhere to route
        // on the topic exchange yet and is silently dropped. initialize() is
        // idempotent, so calling it again here is a no-op once things have
        // settled.
        rabbitAdmin.initialize();

        // Container.isRunning() (SmartLifecycle) flips true before the
        // background consumer thread has actually completed its basic.consume
        // handshake with the broker — waiting for a real active consumer here
        // (rather than a fixed sleep) is what actually eliminates the "message
        // published before anything was listening, silently dropped" race,
        // regardless of which test method happens to run first.
        MessageListenerContainer container = listenerRegistry.getListenerContainer("orderEventsListener");
        await().atMost(ofSeconds(10)).until(
                () -> ((SimpleMessageListenerContainer) container).getActiveConsumerCount() >= 1);
    }

    @Test
    void dispatchesToRegisteredHandlerAndAcks() throws Exception {
        String eventId = UUID.randomUUID().toString();
        publish("order.placed", envelope(eventId, "test.dispatch", false));

        await().atMost(ofSeconds(8)).untilAsserted(() -> assertThat(RECEIVED).hasSize(1));
        assertThat(RECEIVED.get(0).get("orderPublicId").asText()).isEqualTo("test-order");

        String dedupeKey = "order-events:dedupe:" + eventId;
        assertThat(redisTemplate.hasKey(dedupeKey)).isTrue();
        Long ttl = redisTemplate.getExpire(dedupeKey);
        assertThat(ttl).isGreaterThan(0);
    }

    @Test
    void deduplicatesRepeatedEventId() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String body = envelope(eventId, "test.dispatch", false);

        publish("order.placed", body);
        await().atMost(ofSeconds(8)).untilAsserted(() -> assertThat(RECEIVED_COUNT.get()).isEqualTo(1));

        publish("order.placed", body);
        // Give the redelivery a moment to be (not) processed, then assert it
        // never bumps the count — a fixed sleep here would be flaky in the
        // other direction, so pollDelay + during() confirms it *stays* at 1.
        await().pollDelay(ofSeconds(2)).atMost(ofSeconds(6))
                .untilAsserted(() -> assertThat(RECEIVED_COUNT.get()).isEqualTo(1));
    }

    @Test
    void routesToDlqWhenHandlerThrows() throws Exception {
        String eventId = UUID.randomUUID().toString();
        publish("order.placed", envelope(eventId, "test.dispatch", true));

        String dlq = properties.getRabbitmq().getOrderEvents().getDlq();
        await().atMost(ofSeconds(8)).untilAsserted(() -> {
            Message dead = rabbitTemplate.receive(dlq, 200);
            assertThat(dead).isNotNull();
            OrderEventEnvelope envelope = objectMapper.readValue(dead.getBody(), OrderEventEnvelope.class);
            assertThat(envelope.eventId()).isEqualTo(eventId);
        });
    }

    private void publish(String routingKey, String jsonBody) {
        String exchange = properties.getRabbitmq().getOrderEvents().getExchange();
        Message message = new Message(jsonBody.getBytes(StandardCharsets.UTF_8), new MessageProperties());
        rabbitTemplate.send(exchange, routingKey, message);
    }

    private String envelope(String eventId, String eventType, boolean shouldFail) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderPublicId", "test-order");
        payload.put("shouldFail", shouldFail);

        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", java.time.Instant.now().toString());
        envelope.set("payload", payload);
        return objectMapper.writeValueAsString(envelope);
    }
}
