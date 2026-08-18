package com.swiftbite.analytics.lib.auth;

import java.util.List;

/**
 * Same JWT contract as core-service/order-service: {@code userId}, {@code role},
 * {@code restaurantId?}, {@code restaurantRole?}, {@code branchIds?} (order-service's
 * {@code JwtPayload} in lib/auth/jwt.ts). Populated once per request by
 * {@link JwtAuthFilter} and handed to controllers via {@code @RequestAttribute}.
 */
public record AuthenticatedUser(
        long userId,
        String role,
        Long restaurantId,
        String restaurantRole,
        List<Long> branchIds) {

    public static final String REQUEST_ATTRIBUTE = "authenticatedUser";
}
