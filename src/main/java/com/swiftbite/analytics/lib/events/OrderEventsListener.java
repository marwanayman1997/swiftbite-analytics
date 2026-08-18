package com.swiftbite.analytics.lib.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.swiftbite.analytics.lib.config.AnalyticsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mirrors order-service's lib/core-events/consumer.ts: manual ack, Redis
 * SETNX dedupe before dispatch, dispatch by eventType, ack on success,
 * nack(requeue=false) on handler failure so the message routes to the DLQ.
 * <p>
 * Deserializes the raw message body itself (rather than relying on a
 * Spring AMQP {@code MessageConverter}) so this doesn't depend on the
 * producer setting a particular content-type header — order-service's
 * outbox publisher (Phase 0) doesn't set one.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);
    private static final String DEDUPE_KEY_PREFIX = "order-events:dedupe:";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, OrderEventHandler> handlersByEventType;
    private final Duration dedupeTtl;

    public OrderEventsListener(
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            List<OrderEventHandler> handlers,
            AnalyticsProperties properties) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.handlersByEventType = handlers.stream()
                .collect(Collectors.toMap(OrderEventHandler::eventType, Function.identity()));
        this.dedupeTtl = Duration.ofHours(properties.getRabbitmq().getOrderEvents().getDedupeTtlHours());
    }

    @RabbitListener(id = "orderEventsListener", queues = "${analytics.rabbitmq.order-events.queue}")
    public void onMessage(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        try {
            OrderEventEnvelope envelope = objectMapper.readValue(message.getBody(), OrderEventEnvelope.class);

            // $inc-based Mongo upserts (Phase 3) are not naturally idempotent the
            // way order-service's cache-invalidation handlers are — this dedupe
            // is load-bearing, not just de-noising redelivery.
            String dedupeKey = DEDUPE_KEY_PREFIX + envelope.eventId();
            Boolean isFresh = redisTemplate.opsForValue().setIfAbsent(dedupeKey, "1", dedupeTtl);
            if (Boolean.FALSE.equals(isFresh)) {
                log.info("order-event already processed, skipping eventId={} eventType={}",
                        envelope.eventId(), envelope.eventType());
                channel.basicAck(deliveryTag, false);
                return;
            }

            OrderEventHandler handler = handlersByEventType.get(envelope.eventType());
            if (handler == null) {
                log.info("order-event has no registered handler, acking eventId={} eventType={}",
                        envelope.eventId(), envelope.eventType());
                channel.basicAck(deliveryTag, false);
                return;
            }

            handler.handle(envelope.payload());
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("order-event handler failed, nacking to DLQ", ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
