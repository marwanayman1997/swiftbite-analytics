package com.swiftbite.analytics.app.branchday.controller;

import com.swiftbite.analytics.app.branchday.dto.BranchDayResponseDTO;
import com.swiftbite.analytics.app.branchday.service.BranchDayService;
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

@RestController
@RequestMapping("/api/v1/analytics/branches")
public class BranchDayController {

    private final BranchDayService service;

    public BranchDayController(BranchDayService service) {
        this.service = service;
    }

    @GetMapping("/{branchId}/daily")
    public ApiResponse<List<BranchDayResponseDTO>> daily(
            @RequestAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE) AuthenticatedUser actor,
            @PathVariable long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        PageResult<BranchDayResponseDTO> page = service.listDaily(actor, branchId, from, to, cursor, limit);
        return ApiResponse.of(page.data(), page.meta());
    }
}
