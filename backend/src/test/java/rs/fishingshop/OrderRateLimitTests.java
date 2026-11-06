package rs.fishingshop;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
@TestPropertySource(properties = "shop.orders.max-per-address=3")
class OrderRateLimitTests extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void oneAddressCanSendOnlyALimitedNumberOfOrdersInAWindow() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(order("10.0.0.1")).andExpect(status().isBadRequest());
        }

        mvc.perform(order("10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(order("10.0.0.2")).andExpect(status().isBadRequest());
    }

    private static MockHttpServletRequestBuilder order(String address) {
        return post("/api/orders")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
    }
}
