package com.swiftbite.analytics.lib.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Mirrors app.ts's cors({ origin: env.cors.origins, credentials: true }).
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AnalyticsProperties properties;

    public WebConfig(AnalyticsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(properties.getCorsOrigins().toArray(new String[0]))
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowCredentials(true);
    }
}
