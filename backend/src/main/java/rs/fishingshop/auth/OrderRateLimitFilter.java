package rs.fishingshop.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

class OrderRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private record Window(Instant startedAt, int requests) {
    }

    private final PathPatternRequestMatcher orders =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/orders");
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final OrderRateLimitProperties properties;
    private final ProblemWriter problems;

    OrderRateLimitFilter(OrderRateLimitProperties properties, ProblemWriter problems) {
        this.properties = properties;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !orders.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = Instant.now();
        Duration window = properties.rateWindow();
        if (windows.size() > MAX_TRACKED_ADDRESSES) {
            windows.values().removeIf(counted -> !counted.startedAt().plus(window).isAfter(now));
        }
        Window counted = windows.compute(request.getRemoteAddr(), (address, existing) ->
                existing == null || !existing.startedAt().plus(window).isAfter(now)
                        ? new Window(now, 1)
                        : new Window(existing.startedAt(), existing.requests() + 1));

        if (counted.requests() > properties.maxPerAddress()) {
            long wait = Math.max(1, Duration.between(now, counted.startedAt().plus(window)).toSeconds());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(wait));
            problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "Too many orders, try again later");
            return;
        }
        chain.doFilter(request, response);
    }
}
