package com.swiftbite.analytics.app.restaurantday.controller;

import com.swiftbite.analytics.app.restaurantday.dto.RestaurantDayResponseDTO;
import com.swiftbite.analytics.app.restaurantday.service.RestaurantDayService;
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

/** Validates (via Spring's own param binding) → calls service → wraps in ApiResponse. No business logic here. */
@RestController
@RequestMapping("/api/v1/analytics/restaurants")
public class RestaurantDayController {

    private final RestaurantDayService service;

    public RestaurantDayController(RestaurantDayService service) {
        this.service = service;
    }

    @GetMapping("/{restaurantId}/daily")
    public ApiResponse<List<RestaurantDayResponseDTO>> daily(
            @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser actor,
            @PathVariable long restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        PageResult<RestaurantDayResponseDTO> page = service.listDaily(actor, restaurantId, from, to, cursor, limit);
        return ApiResponse.of(page.data(), page.meta());
    }
}
