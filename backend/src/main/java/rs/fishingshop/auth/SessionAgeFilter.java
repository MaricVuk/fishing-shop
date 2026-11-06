package rs.fishingshop.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import org.springframework.web.filter.OncePerRequestFilter;

class SessionAgeFilter extends OncePerRequestFilter {

    private final Duration maxAge;

    SessionAgeFilter(Duration maxAge) {
        this.maxAge = maxAge;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && System.currentTimeMillis() - session.getCreationTime() > maxAge.toMillis()) {
            session.invalidate();
        }
        chain.doFilter(request, response);
    }
}
