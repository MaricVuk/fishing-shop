package rs.fishingshop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

abstract class EmployeeTestSupport extends PostgresIntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void removeEmployeesAndSessions() {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM employees");
    }

    protected long employee(String email, String role, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO employees (email, full_name, password_hash, role, active)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, email, "Test " + role, passwordEncoder.encode(PASSWORD), role, active);
    }
}
