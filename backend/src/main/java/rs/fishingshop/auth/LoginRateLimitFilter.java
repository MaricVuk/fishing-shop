package rs.fishingshop.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

class LoginRateLimitFilter extends OncePerRequestFilter {

    // The same kind of matcher the login filter itself uses. A comparison of the raw URI string
    // would miss a path such as /api/auth/%6Cogin, which still reaches the login filter.
    private final PathPatternRequestMatcher login =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, SecurityConfig.LOGIN);
    private final LoginAttemptLimiter limiter;
    private final ProblemWriter problems;

    LoginRateLimitFilter(LoginAttemptLimiter limiter, ProblemWriter problems) {
        this.limiter = limiter;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !login.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getQueryString() != null) {
            problems.write(request, response, HttpStatus.BAD_REQUEST, "Credentials belong in the request body");
            return;
        }
        Duration blocked = limiter.blockedFor(request.getParameter("email"), request.getRemoteAddr());
        if (blocked.isPositive()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, blocked.toSeconds())));
            problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "Too many failed logins, try again later");
            return;
        }
        chain.doFilter(request, response);
    }
}
