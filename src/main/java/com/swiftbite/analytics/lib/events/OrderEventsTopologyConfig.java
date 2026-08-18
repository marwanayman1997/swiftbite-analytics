package com.swiftbite.analytics.lib.events;

import com.swiftbite.analytics.lib.config.AnalyticsProperties;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Declares the order.events consumer topology this service owns — mirrors
 * order-service's core-events consumer.ts declareTopology call (same shape:
 * topic exchange, durable queue with a dead-letter-exchange argument, DLQ
 * bound with "#"). Spring AMQP (re)asserts every {@link Declarable} here on
 * every broker (re)connect, so this is idempotent by construction, same as
 * the TS side.
 */
@Configuration
public class OrderEventsTopologyConfig {

    @Bean
    public Declarables orderEventsTopology(AnalyticsProperties properties) {
        AnalyticsProperties.OrderEvents cfg = properties.getRabbitmq().getOrderEvents();

        TopicExchange exchange = new TopicExchange(cfg.getExchange(), true, false);
        TopicExchange dlx = new TopicExchange(cfg.getDlx(), true, false);
        Queue dlq = QueueBuilder.durable(cfg.getDlq()).build();
        Queue queue = QueueBuilder.durable(cfg.getQueue())
                .withArgument("x-dead-letter-exchange", cfg.getDlx())
                .build();

        List<Declarable> declarables = new ArrayList<>();
        declarables.add(exchange);
        declarables.add(dlx);
        declarables.add(queue);
        declarables.add(dlq);
        declarables.add(BindingBuilder.bind(dlq).to(dlx).with("#"));
        for (String pattern : cfg.getBindings()) {
            declarables.add(BindingBuilder.bind(queue).to(exchange).with(pattern));
        }

        return new Declarables(declarables);
    }
}
