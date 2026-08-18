package com.swiftbite.analytics.lib.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Mirrors order-service's {@code lib/config/env.ts}: one place app-specific,
 * env-backed settings live, validated at boot rather than failing deep in a
 * request handler. Grows one field at a time as each feature that needs it
 * lands — see CLAUDE.md's "never add an unused env var" rule.
 */
@ConfigurationProperties(prefix = "analytics")
@Validated
public class AnalyticsProperties {

    @NotEmpty
    private List<String> corsOrigins;

    @Valid
    @NestedConfigurationProperty
    private Rabbitmq rabbitmq = new Rabbitmq();

    @Valid
    @NestedConfigurationProperty
    private Jwt jwt = new Jwt();

    public List<String> getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(List<String> corsOrigins) {
        this.corsOrigins = corsOrigins;
    }

    public Rabbitmq getRabbitmq() {
        return rabbitmq;
    }

    public void setRabbitmq(Rabbitmq rabbitmq) {
        this.rabbitmq = rabbitmq;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    // Same JWT contract as core-service/order-service: this must be the
    // identical ACCESS_SECRET value those services sign with (HS256), no
    // default — fails fast at boot rather than silently accepting every
    // token as invalid.
    public static class Jwt {

        @NotEmpty
        private String accessSecret;

        public String getAccessSecret() {
            return accessSecret;
        }

        public void setAccessSecret(String accessSecret) {
            this.accessSecret = accessSecret;
        }
    }

    public static class Rabbitmq {

        @Valid
        @NestedConfigurationProperty
        private OrderEvents orderEvents = new OrderEvents();

        public OrderEvents getOrderEvents() {
            return orderEvents;
        }

        public void setOrderEvents(OrderEvents orderEvents) {
            this.orderEvents = orderEvents;
        }
    }

    // Mirrors order-service's RABBITMQ_ORDER_EVENTS_* / RABBITMQ_CORE_EVENTS_*
    // env vars (lib/config/env.ts) — same topology shape, pointed at the
    // order.events exchange order-service's outbox publishes to (Phase 0).
    public static class OrderEvents {

        @NotEmpty
        private String exchange;

        @NotEmpty
        private String queue;

        @NotEmpty
        private String dlx;

        @NotEmpty
        private String dlq;

        @NotEmpty
        private List<String> bindings;

        @Positive
        private int dedupeTtlHours = 24;

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getQueue() {
            return queue;
        }

        public void setQueue(String queue) {
            this.queue = queue;
        }

        public String getDlx() {
            return dlx;
        }

        public void setDlx(String dlx) {
            this.dlx = dlx;
        }

        public String getDlq() {
            return dlq;
        }

        public void setDlq(String dlq) {
            this.dlq = dlq;
        }

        public List<String> getBindings() {
            return bindings;
        }

        public void setBindings(List<String> bindings) {
            this.bindings = bindings;
        }

        public int getDedupeTtlHours() {
            return dedupeTtlHours;
        }

        public void setDedupeTtlHours(int dedupeTtlHours) {
            this.dedupeTtlHours = dedupeTtlHours;
        }
    }
}
