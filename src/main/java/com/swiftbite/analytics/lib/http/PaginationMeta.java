package com.swiftbite.analytics.lib.http;

/**
 * Mirrors order-service's lib/http/pagination/cursor-pagination.ts's
 * {@code PaginationMeta} shape exactly, so response payloads look the same
 * across services regardless of which one served them.
 */
public record PaginationMeta(String nextCursor, boolean hasMore, int count) {
}
