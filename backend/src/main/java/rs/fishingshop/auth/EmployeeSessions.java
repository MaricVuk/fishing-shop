package rs.fishingshop.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EmployeeSessions {

    private final JdbcTemplate jdbc;

    EmployeeSessions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Plain SQL on purpose: the Spring Session repository deletes in a transaction of its own, so a
    // change that is rolled back afterwards would still have logged the employee out.
    @Transactional(propagation = Propagation.MANDATORY)
    public void endAll(String email) {
        jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", email);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void endAllExcept(String email, String keptSessionId) {
        jdbc.update("DELETE FROM spring_session WHERE principal_name = ? AND session_id <> ?", email, keptSessionId);
    }
}
