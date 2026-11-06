package rs.fishingshop.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// Deleting the sessions of an employee only reaches sessions that exist at that moment. A login
// that is still running while an owner changes the account saves its session afterwards. So every
// request also checks that the account is still the one the session was issued for.
class EmployeeStateFilter extends OncePerRequestFilter {

    private final JdbcTemplate jdbc;

    EmployeeStateFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof EmployeePrincipal principal
                && !stillCurrent(principal)) {
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }

    private boolean stillCurrent(EmployeePrincipal principal) {
        List<Boolean> current = jdbc.query(
                "SELECT active, role, session_version FROM employees WHERE id = ?",
                (row, index) -> row.getBoolean("active")
                        && row.getString("role").equals(principal.getRole().name())
                        && row.getInt("session_version") == principal.getSessionVersion(),
                principal.getId());
        return !current.isEmpty() && current.getFirst();
    }
}
