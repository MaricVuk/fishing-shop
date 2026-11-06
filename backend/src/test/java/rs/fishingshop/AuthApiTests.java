package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@TestPropertySource(properties = "shop.auth.session-max-age=PT4S")
class AuthApiTests extends EmployeeTestSupport {

    private static final String OWNER = "owner@fishing-shop.test";

    @Autowired
    MockMvc mvc;

    @Test
    void theSessionCookieIsHttpOnlySecureAndSameSite() throws Exception {
        employee(OWNER, "OWNER", true);

        String setCookie = login(OWNER, PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeaders("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("SESSION="))
                .findFirst()
                .orElseThrow();

        assertThat(setCookie).startsWith("SESSION=").contains("HttpOnly").contains("Secure").contains("SameSite=Lax");
    }

    @Test
    void wrongPasswordUnknownEmailAndInactiveEmployeeGetTheSameAnswer() throws Exception {
        employee(OWNER, "OWNER", true);
        employee("gone@fishing-shop.test", "SELLER", false);

        String wrongPassword = login(OWNER, "not-the-password").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@fishing-shop.test", PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String inactive = login("gone@fishing-shop.test", PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
        assertThat(inactive).isEqualTo(wrongPassword);
        assertThat(wrongPassword).contains("Invalid email or password");
    }

    @Test
    void theEmailIsMatchedRegardlessOfCase() throws Exception {
        employee(OWNER, "OWNER", true);

        login("Owner@Fishing-Shop.TEST", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void loginWithoutACsrfTokenIsRejected() throws Exception {
        employee(OWNER, "OWNER", true);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", OWNER)
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void meNeedsASession() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void meReturnsTheLoggedInEmployee() throws Exception {
        employee(OWNER, "OWNER", true);
        Cookie session = sessionOf(login(OWNER, PASSWORD));

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(OWNER))
                .andExpect(jsonPath("$.fullName").value("Test OWNER"))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void aNewLoginGetsANewSessionId() throws Exception {
        employee(OWNER, "OWNER", true);
        Cookie first = sessionOf(login(OWNER, PASSWORD));

        Cookie second = sessionOf(mvc.perform(post("/api/auth/login")
                .with(csrf())
                .cookie(first)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", OWNER)
                .param("password", PASSWORD)));

        assertThat(second.getValue()).isNotEqualTo(first.getValue());
    }

    @Test
    void logoutEndsTheSessionOnTheServer() throws Exception {
        employee(OWNER, "OWNER", true);
        Cookie session = sessionOf(login(OWNER, PASSWORD));
        assertThat(sessionCount()).isEqualTo(1);

        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(session))
                .andExpect(status().isNoContent());

        assertThat(sessionCount()).isZero();
        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutACsrfTokenIsRejected() throws Exception {
        employee(OWNER, "OWNER", true);
        Cookie session = sessionOf(login(OWNER, PASSWORD));

        mvc.perform(post("/api/auth/logout").cookie(session)).andExpect(status().isForbidden());

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void aSessionEndsAfterItsMaximumAgeEvenWhenItIsUsed() throws Exception {
        employee(OWNER, "OWNER", true);
        Cookie session = sessionOf(login(OWNER, PASSWORD));

        for (int i = 0; i < 5; i++) {
            Thread.sleep(1_000);
            mvc.perform(get("/api/auth/me").cookie(session));
        }

        mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedFailuresBlockFurtherAttemptsForThatEmail() throws Exception {
        String target = "limited@fishing-shop.test";
        employee(target, "OWNER", true);

        for (int i = 0; i < 5; i++) {
            login(target, "guess-" + i).andExpect(status().isUnauthorized());
        }

        login(target, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void passwordsAreStoredAsBcryptHashes() {
        employee(OWNER, "OWNER", true);

        String hash = jdbc.queryForObject("SELECT password_hash FROM employees WHERE email = ?", String.class, OWNER);

        assertThat(hash).startsWith("$2").doesNotContain(PASSWORD);
    }

    @Test
    void adminRoutesNeedALogin() throws Exception {
        mvc.perform(get("/api/admin/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void theShopStaysPublic() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("errors")));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email)
                .param("password", password));
    }

    private static Cookie sessionOf(ResultActions result) throws Exception {
        Cookie session = result.andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    private int sessionCount() {
        return jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class);
    }
}
