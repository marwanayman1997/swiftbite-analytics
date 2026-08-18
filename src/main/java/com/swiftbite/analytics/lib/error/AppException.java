package com.swiftbite.analytics.lib.error;

// Mirrors order-service's lib/error/AppError.ts. Services throw this
// (never a plain exception); module-level, stable-wording instances belong
// in each module's own errors.java once app/ modules exist (CLAUDE.md §10).
public class AppException extends RuntimeException {

    private final int statusCode;
    private final boolean operational;
    private final transient Object details;

    public AppException(String message, int statusCode) {
        this(message, statusCode, true, null);
    }

    public AppException(String message, int statusCode, boolean operational, Object details) {
        super(message);
        this.statusCode = statusCode;
        this.operational = operational;
        this.details = details;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isOperational() {
        return operational;
    }

    public Object getDetails() {
        return details;
    }
}
