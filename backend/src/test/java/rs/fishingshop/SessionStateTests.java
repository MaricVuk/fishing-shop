package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SessionStateTests extends EmployeeTestSupport {

    private static final String OWNER = "vlasnik@state.test";
    private static final String SELLER = "prodavac@state.test";

    @Autowired
    MockMvc mvc;

    @MockitoSpyBean
    PasswordEncoder encoder;

    @Test
    void aSessionEndsWhenTheEmployeeIsDeactivatedBehindItsBack() throws Exception {
        long seller = employee(SELLER, "SELLER", true);
        Cookie session = logIn(mvc, SELLER);
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk());

        jdbc.update("UPDATE employees SET active = FALSE WHERE id = ?", seller);

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void aSessionEndsWhenTheRoleDiffersFromTheOneItWasIssuedFor() throws Exception {
        long seller = employee(SELLER, "SELLER", true);
        Cookie session = logIn(mvc, SELLER);

        jdbc.update("UPDATE employees SET role = 'WAREHOUSE' WHERE id = ?", seller);

        mvc.perform(get("/api/admin/orders").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void aSessionEndsWhenTheSessionVersionMovesOn() throws Exception {
        long seller = employee(SELLER, "SELLER", true);
        Cookie session = logIn(mvc, SELLER);

        jdbc.update("UPDATE employees SET session_version = session_version + 1 WHERE id = ?", seller);

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void aLoginThatIsInFlightWhileTheOwnerDeactivatesTheEmployeeGetsNoUsableSession() throws Exception {
        employee(OWNER, "OWNER", true);
        long seller = employee(SELLER, "SELLER", true);
        Cookie owner = logIn(mvc, OWNER);

        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean armed = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (armed.compareAndSet(true, false)) {
                inside.countDown();
                release.await(30, TimeUnit.SECONDS);
            }
            return invocation.callRealMethod();
        }).when(encoder).matches(any(), any());

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Cookie> slowLogin = pool.submit(() -> logIn(mvc, SELLER));
            assertThat(inside.await(30, TimeUnit.SECONDS)).isTrue();

            mvc.perform(post("/api/admin/employees/{id}/deactivate", seller).with(csrf()).cookie(owner))
                    .andExpect(status().isOk());
            release.countDown();
            Cookie late = slowLogin.get(30, TimeUnit.SECONDS);

            mvc.perform(get("/api/auth/me").cookie(late)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/admin/orders").cookie(late)).andExpect(status().isUnauthorized());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void aPasswordResetDuringALoginLeavesNoSessionOnTheOldPassword() throws Exception {
        employee(OWNER, "OWNER", true);
        long seller = employee(SELLER, "SELLER", true);
        Cookie owner = logIn(mvc, OWNER);

        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean armed = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (armed.compareAndSet(true, false)) {
                inside.countDown();
                release.await(30, TimeUnit.SECONDS);
            }
            return invocation.callRealMethod();
        }).when(encoder).matches(any(), any());

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Cookie> slowLogin = pool.submit(() -> logIn(mvc, SELLER));
            assertThat(inside.await(30, TimeUnit.SECONDS)).isTrue();

            mvc.perform(post("/api/admin/employees/{id}/password", seller).with(csrf()).cookie(owner)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"a-brand-new-password\"}"))
                    .andExpect(status().isNoContent());
            release.countDown();
            Cookie late = slowLogin.get(30, TimeUnit.SECONDS);

            mvc.perform(get("/api/auth/me").cookie(late)).andExpect(status().isUnauthorized());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
