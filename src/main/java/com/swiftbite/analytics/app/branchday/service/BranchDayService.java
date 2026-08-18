package com.swiftbite.analytics.app.branchday.service;

import com.swiftbite.analytics.app.branchday.document.BranchDayDocument;
import com.swiftbite.analytics.app.branchday.dto.BranchDayResponseDTO;
import com.swiftbite.analytics.app.branchday.repository.BranchDayRepository;
import com.swiftbite.analytics.lib.auth.AuthenticatedUser;
import com.swiftbite.analytics.lib.error.AppException;
import com.swiftbite.analytics.lib.http.PageResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * A non-owner {@code restaurant_user} is checked strictly against the JWT's
 * own {@code branchIds} claim — no gap possible there. A
 * {@code restaurantRole=="owner"} actor is trickier: order-service's own
 * {@code requireBranchAccess} middleware trusts an owner for any branchId
 * with no further check, because it has no branch-ownership data on hand at
 * that point without a core-service call. This service is in a better
 * position — {@code BranchDayDocument} already carries {@code restaurantId}
 * — so it fetches first and then verifies the owner's claimed
 * {@code restaurantId} against whatever data actually came back, denying
 * only when the fetched data *proves* the branch belongs to someone else. A
 * branch with no order history yet has nothing to prove that with, so an
 * owner is still trusted for it — but that's an empty result either way, so
 * there's nothing to disclose.
 */
@Service
public class BranchDayService {

    private final BranchDayRepository repository;

    public BranchDayService(BranchDayRepository repository) {
        this.repository = repository;
    }

    public PageResult<BranchDayResponseDTO> listDaily(
            AuthenticatedUser actor, long branchId, LocalDate from, LocalDate to, String cursor, Integer limit) {
        PageResult<BranchDayDocument> page = repository.findByBranchId(branchId, from, to, cursor, limit);
        assertAccess(actor, branchId, page.data());

        return new PageResult<>(
                page.data().stream().map(BranchDayResponseDTO::from).toList(),
                page.meta());
    }

    private void assertAccess(AuthenticatedUser actor, long branchId, List<BranchDayDocument> fetched) {
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
