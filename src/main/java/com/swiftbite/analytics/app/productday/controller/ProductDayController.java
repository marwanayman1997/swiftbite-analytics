package com.swiftbite.analytics.app.productday.controller;

import com.swiftbite.analytics.app.productday.dto.ProductDayResponseDTO;
import com.swiftbite.analytics.app.productday.service.ProductDayService;
import com.swiftbite.analytics.lib.auth.AuthenticatedUser;
import com.swiftbite.analytics.lib.http.ApiResponse;
import com.swiftbite.analytics.lib.http.PageResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Nested under /branches/{branchId} — product-day is scoped per branch (§7's document javadoc). */
@RestController
@RequestMapping("/api/v1/analytics/branches/{branchId}/products")
public class ProductDayController {

    private final ProductDayService service;

    public ProductDayController(ProductDayService service) {
        this.service = service;
    }

    @GetMapping("/{productId}/daily")
    public ApiResponse<List<ProductDayResponseDTO>> daily(
            @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser actor,
            @PathVariable long branchId,
            @PathVariable long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        PageResult<ProductDayResponseDTO> page =
                service.listDaily(actor, branchId, productId, from, to, cursor, limit);
        return ApiResponse.of(page.data(), page.meta());
    }
}
