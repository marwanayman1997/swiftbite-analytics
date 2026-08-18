package com.swiftbite.analytics.app.productday.service;

import com.swiftbite.analytics.app.productday.document.ProductDayDocument;
import com.swiftbite.analytics.app.productday.dto.ProductDayResponseDTO;
import com.swiftbite.analytics.app.productday.repository.ProductDayRepository;
import com.swiftbite.analytics.lib.auth.AuthenticatedUser;
import com.swiftbite.analytics.lib.error.AppException;
import com.swiftbite.analytics.lib.http.PageResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Product-day is scoped under a branch (route is {@code /branches/{branchId}/products/{productId}/daily}),
 * so it uses the same fetch-then-verify ownership pattern as
 * {@link com.swiftbite.analytics.app.branchday.service.BranchDayService} —
 * see that class's javadoc for the reasoning.
 */
@Service
public class ProductDayService {

    private final ProductDayRepository repository;

    public ProductDayService(ProductDayRepository repository) {
        this.repository = repository;
    }

    public PageResult<ProductDayResponseDTO> listDaily(
            AuthenticatedUser actor, long branchId, long productId,
            LocalDate from, LocalDate to, String cursor, Integer limit) {
        PageResult<ProductDayDocument> page =
                repository.findByProductAndBranch(productId, branchId, from, to, cursor, limit);
        assertAccess(actor, branchId, page.data());

        return new PageResult<>(
                page.data().stream().map(ProductDayResponseDTO::from).toList(),
                page.meta());
    }

    private void assertAccess(AuthenticatedUser actor, long branchId, List<ProductDayDocument> fetched) {
        if ("system_admin".equals(actor.role())) {
            return;
        }
        if ("restaurant_user".equals(actor.role())) {
            if (actor.branchIds() != null && actor.branchIds().contains(branchId)) {
                return;
            }
            if ("owner".equals(actor.restaurantRole())) {
                boolean provenForeign = fetched.stream()
                        .anyMatch(doc -> actor.restaurantId() == null || doc.getRestaurantId() != actor.restaurantId());
                if (!provenForeign) {
                    return;
                }
            }
        }
        throw new AppException("Unauthorized", 403);
    }
}
