package com.swiftbite.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Boots the full context — catches config binding failures (AnalyticsProperties),
// and Mongo/Redis/RabbitMQ autoconfiguration errors, not just compile errors.
@SpringBootTest
class AnalyticsServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
