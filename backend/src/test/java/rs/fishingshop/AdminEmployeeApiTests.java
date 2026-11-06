package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class AdminEmployeeApiTests extends EmployeeTestSupport {

    private static final String OWNER = "vlasnik@team.test";
    private static final String SECOND_OWNER = "suvlasnik@team.test";
    private static final String SELLER = "prodavac@team.test";
    private static final String WAREHOUSE = "magacin@team.test";
    private static final String NEW_PASSWORD = "a-brand-new-password";

    @Autowired
    MockMvc mvc;

    long ownerId;
    long sellerId;

    @BeforeEach
    void setUp() {
        ownerId = employee(OWNER, "OWNER", true);
        sellerId = employee(SELLER, "SELLER", true);
        employee(WAREHOUSE, "WAREHOUSE", true);
    }

    @Test
    void onlyTheOwnerManagesEmployees() throws Exception {
        Cookie seller = logIn(mvc, SELLER);
        Cookie warehouse = logIn(mvc, WAREHOUSE);

        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/admin/employees"),
                get("/api/admin/audit"),
                post("/api/admin/employees").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(newEmployee("x@team.test", "SELLER")),
                post("/api/admin/employees/{id}/role", sellerId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OWNER\"}"),
                post("/api/admin/employees/{id}/deactivate", sellerId).with(csrf()),
                post("/api/admin/employees/{id}/password", sellerId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + NEW_PASSWORD + "\"}"))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(request.cookie(seller)).andExpect(status().isForbidden());
            mvc.perform(request.cookie(warehouse)).andExpect(status().isForbidden());
        }

        assertThat(roleOf(sellerId)).isEqualTo("SELLER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class)).isEqualTo(3);
    }

    @Test
    void theOwnerSeesEveryEmployeeWithoutPasswordHashes() throws Exception {
        mvc.perform(get("/api/admin/employees").cookie(logIn(mvc, OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].email").exists())
                .andExpect(jsonPath("$[0].role").exists())
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(content().string(not(containsString("$2"))))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void anOwnerAddsAnEmployeeWhoCanThenLogIn() throws Exception {
        mvc.perform(post("/api/admin/employees").with(csrf()).cookie(logIn(mvc, OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(newEmployee("Nova@Team.test", "WAREHOUSE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nova@team.test"))
                .andExpect(jsonPath("$.role").value("WAREHOUSE"))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(jdbc.queryForObject(
                "SELECT password_hash FROM employees WHERE email = 'nova@team.test'", String.class)).startsWith("$2");
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "nova@team.test").param("password", NEW_PASSWORD))
                .andExpect(status().isOk());
        String audit = jdbc.queryForObject("""
                SELECT new_value::text FROM audit_log WHERE action = 'EMPLOYEE_CREATED'
                """, String.class);
        assertThat(audit).contains("nova@team.test").contains("WAREHOUSE").doesNotContain(NEW_PASSWORD).doesNotContain("$2");
    }

    @Test
    void anEmailCanBeUsedOnlyOnceWhateverTheLetterCase() throws Exception {
        mvc.perform(post("/api/admin/employees").with(csrf()).cookie(logIn(mvc, OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(newEmployee("PRODAVAC@team.test", "SELLER")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsWeakOrBrokenInput() throws Exception {
        Cookie owner = logIn(mvc, OWNER);

        for (String body : List.of(
                "{\"email\":\"a@team.test\",\"fullName\":\"A\",\"role\":\"SELLER\",\"password\":\"short\"}",
                "{\"email\":\"a@team.test\",\"fullName\":\"A\",\"role\":\"SELLER\",\"password\":\"" + "x".repeat(65) + "\"}",
                "{\"email\":\"a@team.test\",\"fullName\":\"A\",\"role\":\"SELLER\",\"password\":\"" + "š".repeat(40) + "\"}",
                "{\"email\":\"not-an-email\",\"fullName\":\"A\",\"role\":\"SELLER\",\"password\":\"" + NEW_PASSWORD + "\"}",
                "{\"email\":\"a@team.test\",\"fullName\":\" \",\"role\":\"SELLER\",\"password\":\"" + NEW_PASSWORD + "\"}",
                "{\"email\":\"a@team.test\",\"fullName\":\"A\",\"role\":\"BOSS\",\"password\":\"" + NEW_PASSWORD + "\"}",
                "{\"email\":\"" + "a".repeat(95) + "@t.test\",\"fullName\":\"A\",\"role\":\"SELLER\",\"password\":\""
                        + NEW_PASSWORD + "\"}")) {
            mvc.perform(post("/api/admin/employees").with(csrf()).cookie(owner)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class)).isEqualTo(3);
    }

    @Test
    void aRoleChangeTakesEffectAtOnceBecauseTheSessionsEnd() throws Exception {
        Cookie seller = logIn(mvc, SELLER);
        mvc.perform(get("/api/admin/orders").cookie(seller)).andExpect(status().isOk());

        mvc.perform(post("/api/admin/employees/{id}/role", sellerId).with(csrf()).cookie(logIn(mvc, OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"WAREHOUSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("WAREHOUSE"));

        mvc.perform(get("/api/admin/orders").cookie(seller)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/stock").cookie(logIn(mvc, SELLER))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT old_value ->> 'role' || '>' || (new_value ->> 'role') FROM audit_log
                WHERE action = 'EMPLOYEE_ROLE_CHANGED'
                """, String.class)).isEqualTo("SELLER>WAREHOUSE");
    }

    @Test
    void aDeactivatedEmployeeIsLoggedOutAndCannotLogInUntilActivatedAgain() throws Exception {
        Cookie seller = logIn(mvc, SELLER);
        Cookie owner = logIn(mvc, OWNER);

        mvc.perform(post("/api/admin/employees/{id}/deactivate", sellerId).with(csrf()).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/auth/me").cookie(seller)).andExpect(status().isUnauthorized());
        login(SELLER, PASSWORD).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/admin/employees/{id}/activate", sellerId).with(csrf()).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        login(SELLER, PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT action FROM audit_log ORDER BY id", String.class))
                .containsExactly("EMPLOYEE_DEACTIVATED", "EMPLOYEE_ACTIVATED");
    }

    @Test
    void anOwnerCannotChangeTheirOwnAccount() throws Exception {
        Cookie owner = logIn(mvc, OWNER);

        mvc.perform(post("/api/admin/employees/{id}/role", ownerId).with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"SELLER\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/employees/{id}/deactivate", ownerId).with(csrf()).cookie(owner))
                .andExpect(status().isConflict());

        assertThat(roleOf(ownerId)).isEqualTo("OWNER");
        mvc.perform(get("/api/auth/me").cookie(owner)).andExpect(status().isOk());
    }

    @Test
    void twoOwnersDemotingEachOtherAtOnceLeaveOneOwner() throws Exception {
        long secondOwner = employee(SECOND_OWNER, "OWNER", true);
        Cookie first = logIn(mvc, OWNER);
        Cookie second = logIn(mvc, SECOND_OWNER);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            results.add(pool.submit(() -> {
                start.await();
                return changeRole(first, secondOwner, "SELLER").andReturn().getResponse().getStatus();
            }));
            results.add(pool.submit(() -> {
                start.await();
                return changeRole(second, ownerId, "SELLER").andReturn().getResponse().getStatus();
            }));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsOnlyOnce(200);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM employees WHERE role = 'OWNER' AND active", Integer.class)).isEqualTo(1);
    }

    @Test
    void aPasswordResetEndsTheOldSessionsAndReplacesThePassword() throws Exception {
        Cookie seller = logIn(mvc, SELLER);

        mvc.perform(post("/api/admin/employees/{id}/password", sellerId).with(csrf()).cookie(logIn(mvc, OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(seller)).andExpect(status().isUnauthorized());
        login(SELLER, PASSWORD).andExpect(status().isUnauthorized());
        login(SELLER, NEW_PASSWORD).andExpect(status().isOk());
        String audit = jdbc.queryForObject(
                "SELECT coalesce(old_value::text, '') || coalesce(new_value::text, '') FROM audit_log", String.class);
        assertThat(audit).doesNotContain(NEW_PASSWORD).doesNotContain(PASSWORD).doesNotContain("$2");
    }

    @Test
    void everyEmployeeChangesTheirOwnPasswordWithTheCurrentOne() throws Exception {
        Cookie current = logIn(mvc, WAREHOUSE);
        Cookie other = logIn(mvc, WAREHOUSE);

        mvc.perform(post("/api/auth/password").with(csrf()).cookie(current).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password-here\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));

        mvc.perform(post("/api/auth/password").with(csrf()).cookie(current).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(current)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(other)).andExpect(status().isUnauthorized());
        login(WAREHOUSE, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void theOwnerReadsTheAuditLogNewestFirstAndCanFilterIt() throws Exception {
        Cookie owner = logIn(mvc, OWNER);
        mvc.perform(post("/api/admin/employees/{id}/deactivate", sellerId).with(csrf()).cookie(owner));
        mvc.perform(post("/api/admin/employees/{id}/activate", sellerId).with(csrf()).cookie(owner));

        mvc.perform(get("/api/admin/audit").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].action").value("EMPLOYEE_ACTIVATED"))
                .andExpect(jsonPath("$.content[0].actorEmail").value(OWNER))
                .andExpect(jsonPath("$.content[1].action").value("EMPLOYEE_DEACTIVATED"));
        mvc.perform(get("/api/admin/audit").param("entityType", "EMPLOYEE").param("entityId", String.valueOf(sellerId))
                        .cookie(owner))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/admin/audit").param("entityType", "ORDER").cookie(owner))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/audit").param("entityType", "NOPE").cookie(owner))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenTheAuditRowCannotBeWrittenTheDeactivationIsRolledBack() throws Exception {
        Cookie seller = logIn(mvc, SELLER);
        Cookie owner = logIn(mvc, OWNER);
        jdbc.execute("ALTER TABLE audit_log ADD CONSTRAINT test_reject_all CHECK (false) NOT VALID");
        try {
            mvc.perform(post("/api/admin/employees/{id}/deactivate", sellerId).with(csrf()).cookie(owner))
                    .andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("ALTER TABLE audit_log DROP CONSTRAINT test_reject_all");
        }

        assertThat(jdbc.queryForObject("SELECT active FROM employees WHERE id = ?", Boolean.class, sellerId)).isTrue();
        mvc.perform(get("/api/auth/me").cookie(seller)).andExpect(status().isOk());
    }

    private ResultActions changeRole(Cookie session, long employeeId, String role) throws Exception {
        return mvc.perform(post("/api/admin/employees/{id}/role", employeeId).with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + role + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email).param("password", password));
    }

    private static String newEmployee(String email, String role) {
        return """
                {"email":"%s","fullName":"Nova Osoba","role":"%s","password":"%s"}""".formatted(email, role, NEW_PASSWORD);
    }

    private String roleOf(long id) {
        return jdbc.queryForObject("SELECT role FROM employees WHERE id = ?", String.class, id);
    }
}
