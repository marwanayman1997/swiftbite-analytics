package com.swiftbite.analytics.app.platformday.service;

import com.swiftbite.analytics.app.platformday.document.PlatformDayDocument;
import com.swiftbite.analytics.app.platformday.dto.PlatformDayResponseDTO;
import com.swiftbite.analytics.app.platformday.repository.PlatformDayRepository;
import com.swiftbite.analytics.lib.auth.AuthenticatedUser;
import com.swiftbite.analytics.lib.error.AppException;
import com.swiftbite.analytics.lib.http.PageResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** Platform-wide totals are {@code system_admin}-only — no restaurant owns the whole platform. */
@Service
public class PlatformDayService {

    private final PlatformDayRepository repository;

    public PlatformDayService(PlatformDayRepository repository) {
        this.repository = repository;
    }

    public PageResult<PlatformDayResponseDTO> listDaily(
            AuthenticatedUser actor, String currency, LocalDate from, LocalDate to, String cursor, Integer limit) {
        if (!"system_admin".equals(actor.role())) {
            throw new AppException("Unauthorized", 403);
        }

        PageResult<PlatformDayDocument> page = repository.findByCurrency(currency, from, to, cursor, limit);
        return new PageResult<>(
                page.data().stream().map(PlatformDayResponseDTO::from).toList(),
                page.meta());
    }
}
