package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

abstract class EmployeeTestSupport extends PostgresIntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    @AfterEach
    void removeEmployeesAndSessions() {
        clearAuditLog();
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM employees");
    }

    protected void clearAuditLog() {
        jdbc.execute("ALTER TABLE audit_log DISABLE TRIGGER USER");
        try {
            jdbc.update("DELETE FROM audit_log");
        } finally {
            jdbc.execute("ALTER TABLE audit_log ENABLE TRIGGER USER");
        }
    }

    protected Cookie logIn(MockMvc mvc, String email) throws Exception {
        Cookie session = mvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    protected long employee(String email, String role, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO employees (email, full_name, password_hash, role, active)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, email, "Test " + role, passwordEncoder.encode(PASSWORD), role, active);
    }
}
