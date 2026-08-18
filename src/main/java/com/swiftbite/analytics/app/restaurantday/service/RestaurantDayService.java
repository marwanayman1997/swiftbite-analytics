package com.swiftbite.analytics.app.restaurantday.service;

import com.swiftbite.analytics.app.restaurantday.document.RestaurantDayDocument;
import com.swiftbite.analytics.app.restaurantday.dto.RestaurantDayResponseDTO;
import com.swiftbite.analytics.app.restaurantday.repository.RestaurantDayRepository;
import com.swiftbite.analytics.lib.auth.AuthenticatedUser;
import com.swiftbite.analytics.lib.error.AppException;
import com.swiftbite.analytics.lib.http.PageResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Ownership check mirrors order-service's OrderService#assertReadAccess:
 * {@code system_admin} reads anything, a {@code restaurant_user} may only
 * read their own {@code restaurantId} (from the JWT, not a query param).
 * No permission-cache/core-service round trip — unlike order-service's
 * resource:action RBAC (which gates actions like "accept an order"), this
 * is a pure ownership check the JWT claims already settle, so keeping this
 * service's only synchronous dependency being "nothing external" was worth
 * the simplification (see CLAUDE.md §8's Phase 4 note).
 */
@Service
public class RestaurantDayService {

    private final RestaurantDayRepository repository;

    public RestaurantDayService(RestaurantDayRepository repository) {
        this.repository = repository;
    }

    public PageResult<RestaurantDayResponseDTO> listDaily(
            AuthenticatedUser actor, long restaurantId, LocalDate from, LocalDate to, String cursor, Integer limit) {
        assertAccess(actor, restaurantId);

        PageResult<RestaurantDayDocument> page = repository.findByRestaurantId(restaurantId, from, to, cursor, limit);
        return new PageResult<>(
                page.data().stream().map(RestaurantDayResponseDTO::from).toList(),
                page.meta());
    }

    private void assertAccess(AuthenticatedUser actor, long restaurantId) {
        if ("system_admin".equals(actor.role())) {
            return;
        }
        if ("restaurant_user".equals(actor.role())
                && actor.restaurantId() != null
                && actor.restaurantId() == restaurantId) {
            return;
        }
        throw new AppException("Unauthorized", 403);
    }
}
