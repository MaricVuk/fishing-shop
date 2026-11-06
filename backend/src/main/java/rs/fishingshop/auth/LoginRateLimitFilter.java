package rs.fishingshop.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginAttemptLimiter limiter;
    private final ProblemWriter problems;

    LoginRateLimitFilter(LoginAttemptLimiter limiter, ProblemWriter problems) {
        this.limiter = limiter;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && (request.getContextPath() + SecurityConfig.LOGIN).equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Duration blocked = limiter.blockedFor(request.getParameter("email"), request.getRemoteAddr());
        if (blocked.isPositive()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, blocked.toSeconds())));
            problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "Too many failed logins, try again later");
            return;
        }
        chain.doFilter(request, response);
    }
}
