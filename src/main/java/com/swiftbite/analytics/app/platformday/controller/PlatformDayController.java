package com.swiftbite.analytics.app.platformday.controller;

import com.swiftbite.analytics.app.platformday.dto.PlatformDayResponseDTO;
import com.swiftbite.analytics.app.platformday.service.PlatformDayService;
import com.swiftbite.analytics.lib.auth.AuthenticatedUser;
import com.swiftbite.analytics.lib.http.ApiResponse;
import com.swiftbite.analytics.lib.http.PageResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics/platform")
public class PlatformDayController {

    private final PlatformDayService service;

    public PlatformDayController(PlatformDayService service) {
        this.service = service;
    }

    @GetMapping("/daily")
    public ApiResponse<List<PlatformDayResponseDTO>> daily(
            @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser actor,
            @RequestParam String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        PageResult<PlatformDayResponseDTO> page = service.listDaily(actor, currency, from, to, cursor, limit);
        return ApiResponse.of(page.data(), page.meta());
    }
}
