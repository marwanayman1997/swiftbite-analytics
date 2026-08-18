package com.swiftbite.analytics.lib.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void omitsMetaWhenAbsent() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.of("hello"));

        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"data\":\"hello\"");
        assertThat(json).doesNotContain("meta");
    }

    @Test
    void includesMetaWhenPresent() throws Exception {
        String json = mapper.writeValueAsString(
                ApiResponse.of(java.util.List.of(1, 2), java.util.Map.of("count", 2)));

        assertThat(json).contains("\"meta\":{\"count\":2}");
    }
}
