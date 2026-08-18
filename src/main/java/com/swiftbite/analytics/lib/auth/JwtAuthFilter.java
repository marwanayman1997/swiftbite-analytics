package com.swiftbite.analytics.lib.auth;

import com.swiftbite.analytics.lib.config.AnalyticsProperties;
import com.swiftbite.analytics.lib.error.AppException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Mirrors order-service's lib/auth/guard.ts: read the access token from the
 * {@code access_token} cookie, verify it, populate the equivalent of
 * {@code req.user}. Same JWT contract, so a token minted by core-service or
 * order-service verifies here unchanged.
 * <p>
 * Auth failures are routed through Spring MVC's {@link HandlerExceptionResolver}
 * rather than thrown directly — a plain {@code throw} here would propagate
 * as an uncaught servlet-filter exception and never reach
 * {@code GlobalExceptionHandler} (filters run before {@code DispatcherServlet},
 * outside the {@code @RestControllerAdvice} machinery). Routing through the
 * resolver reuses the same {@code {error, details?}} response shape instead
 * of duplicating it here.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME = "access_token";

    private final SecretKey key;
    private final HandlerExceptionResolver exceptionResolver;

    public JwtAuthFilter(
            AnalyticsProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.key = Keys.hmacShaKeyFor(properties.getJwt().getAccessSecret().getBytes(StandardCharsets.UTF_8));
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = readCookie(request);
        if (token == null) {
            exceptionResolver.resolveException(request, response, null, new AppException("Not authenticated", 401));
            return;
        }

        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            request.setAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE, toUser(claims));
        } catch (JwtException | IllegalArgumentException ex) {
            exceptionResolver.resolveException(request, response, null, new AppException("Not authenticated", 401));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private AuthenticatedUser toUser(Claims claims) {
        Number restaurantIdClaim = claims.get("restaurantId", Number.class);
        List<?> rawBranchIds = claims.get("branchIds", List.class);
        List<Long> branchIds = rawBranchIds == null
                ? List.of()
                : rawBranchIds.stream().map(value -> ((Number) value).longValue()).toList();

        return new AuthenticatedUser(
                claims.get("userId", Number.class).longValue(),
                claims.get("role", String.class),
                restaurantIdClaim == null ? null : restaurantIdClaim.longValue(),
                claims.get("restaurantRole", String.class),
                branchIds);
    }
}
