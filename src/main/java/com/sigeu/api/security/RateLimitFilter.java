package com.sigeu.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends HttpFilter {
    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 5_000;

    private final Map<String, RequestWindow> clients = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int authLimit;
    private final int reportLimit;
    private final int apiLimit;
    private final boolean enabled;

    public RateLimitFilter(
            Clock clock,
            @Value("${sigeu.rate-limit.enabled:true}") boolean enabled,
            @Value("${sigeu.rate-limit.auth-per-minute:10}") int authLimit,
            @Value("${sigeu.rate-limit.reports-per-minute:20}") int reportLimit,
            @Value("${sigeu.rate-limit.api-per-minute:120}") int apiLimit
    ) {
        this.clock = clock;
        this.enabled = enabled;
        this.authLimit = authLimit;
        this.reportLimit = reportLimit;
        this.apiLimit = apiLimit;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        RateRule rule = ruleFor(request);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        if (!allow(request, rule)) {
            response.setStatus(429);
            response.setContentType("text/plain;charset=UTF-8");
            response.setHeader("Retry-After", "60");
            response.getWriter().write("Demasiadas peticiones. Intenta de nuevo en un minuto.");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean allow(HttpServletRequest request, RateRule rule) {
        long now = clock.millis();
        cleanupIfNeeded(now);

        String key = clientIp(request) + ":" + rule.name();
        RequestWindow window = clients.compute(key, (ignored, current) -> {
            if (current == null || now >= current.windowStartedAt + WINDOW_MILLIS) {
                return new RequestWindow(now, 1);
            }
            current.count++;
            return current;
        });

        return window.count <= rule.limit();
    }

    private RateRule ruleFor(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return null;

        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) return null;

        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) {
            return new RateRule("auth", authLimit);
        }

        if (path.equals("/api/emergencies") && "POST".equalsIgnoreCase(request.getMethod())) {
            return new RateRule("reports", reportLimit);
        }

        return new RateRule("api", apiLimit);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void cleanupIfNeeded(long now) {
        if (clients.size() < MAX_TRACKED_CLIENTS) return;

        clients.entrySet().removeIf(entry -> now >= entry.getValue().windowStartedAt + WINDOW_MILLIS);
    }

    private record RateRule(String name, int limit) {}

    private static class RequestWindow {
        private final long windowStartedAt;
        private int count;

        private RequestWindow(long windowStartedAt, int count) {
            this.windowStartedAt = windowStartedAt;
            this.count = count;
        }
    }
}
