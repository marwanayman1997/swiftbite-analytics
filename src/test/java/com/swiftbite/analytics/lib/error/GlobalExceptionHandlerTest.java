package com.swiftbite.analytics.lib.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void operationalExceptionReturnsItsOwnStatusAndMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleAppException(new AppException("Order not found", 404));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsExactly(Map.entry("error", "Order not found"));
    }

    @Test
    void operationalExceptionIncludesDetailsWhenPresent() {
        Map<String, Object> details = Map.of("field", "branchId");
        ResponseEntity<Map<String, Object>> response =
                handler.handleAppException(new AppException("Invalid request", 400, true, details));

        assertThat(response.getBody()).containsEntry("error", "Invalid request");
        assertThat(response.getBody()).containsEntry("details", details);
    }

    @Test
    void nonOperationalExceptionIsMaskedAs500() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleAppException(new AppException("DB connection string leaked", 500, false, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsExactly(Map.entry("error", "Something went wrong"));
    }

    @Test
    void unexpectedExceptionIsMaskedAs500() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleUnexpected(new RuntimeException("npe somewhere"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsExactly(Map.entry("error", "Something went wrong"));
    }
}
