package com.swiftbite.analytics.lib.http;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Mirrors order-service's lib/http/response.ts ({@code sendSuccess} /
 * {@code sendPaginated}). Express calls a function to write the response
 * body imperatively; Spring controllers just return this and Jackson
 * serializes it — so where the TS side has two functions, this is one
 * envelope type with two factories. Controllers must always return data
 * wrapped in this (or {@code ApiResponse<List<T>>} with pagination meta),
 * never a raw entity/document (CLAUDE.md §6/§10).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiResponse<T> {

    private final boolean success = true;
    private final T data;
    private final Object meta;

    private ApiResponse(T data, Object meta) {
        this.data = data;
        this.meta = meta;
    }

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> of(T data, Object meta) {
        return new ApiResponse<>(data, meta);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public Object getMeta() {
        return meta;
    }
}
