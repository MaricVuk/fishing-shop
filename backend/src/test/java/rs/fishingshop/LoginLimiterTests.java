package rs.fishingshop;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "shop.auth.max-failures-per-pair=3",
        "shop.auth.max-failures-per-email=6",
        "shop.auth.max-failures-per-address=1000"})
class LoginLimiterTests extends EmployeeTestSupport {

    private static final String OWNER = "vlasnik@limit.test";

    @Autowired
    MockMvc mvc;

    @Test
    void failuresFromOneAddressDoNotLockTheOwnerOutFromAnotherAddress() throws Exception {
        employee(OWNER, "OWNER", true);

        for (int i = 0; i < 3; i++) {
            login(OWNER, "wrong-" + i, "10.0.0.1").andExpect(status().isUnauthorized());
        }

        login(OWNER, PASSWORD, "10.0.0.1").andExpect(status().isTooManyRequests());
        login(OWNER, PASSWORD, "10.0.0.2").andExpect(status().isOk());
    }

    @Test
    void manyAddressesTogetherStillTriggerTheWiderLimitOnTheEmail() throws Exception {
        employee("meta@limit.test", "OWNER", true);

        for (int i = 1; i <= 6; i++) {
            login("meta@limit.test", "wrong-" + i, "10.1.0." + i).andExpect(status().isUnauthorized());
        }

        login("meta@limit.test", PASSWORD, "10.1.0.99").andExpect(status().isTooManyRequests());
    }

    @Test
    void aDottedCapitalIFindsNoAccountAndSharesOneBucket() throws Exception {
        employee("vlasinik@limit.test", "OWNER", true);

        login("vlasİnik@limit.test", PASSWORD, "10.2.0.1").andExpect(status().isUnauthorized());
        login("vlasinik@limit.test", PASSWORD, "10.2.0.1").andExpect(status().isOk());

        for (int i = 0; i < 3; i++) {
            login("vlas" + "İ".repeat(i + 1) + "nik@limit.test", "wrong", "10.2.0.2")
                    .andExpect(status().isUnauthorized());
        }
        login("vlasİİİİnik@limit.test", "wrong", "10.2.0.2").andExpect(status().isTooManyRequests());
        login("vlasinik@limit.test", PASSWORD, "10.2.0.2").andExpect(status().isOk());
    }

    @Test
    void aHugeEmailIsRejectedWithoutTrackingItAsAKey() throws Exception {
        login("a".repeat(5000) + "@limit.test", "wrong", "10.3.0.1").andExpect(status().isUnauthorized());
    }

    private ResultActions login(String email, String password, String address) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email)
                .param("password", password));
    }
}
