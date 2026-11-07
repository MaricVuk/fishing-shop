package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
@TestPropertySource(properties = "shop.orders.max-per-address=1000000")
class OrderIdempotencyTests extends CommittedOrderTestSupport {

    private static final String KEY_HEADER = "Idempotency-Key";
    private static final String REPLAYED_HEADER = "Idempotency-Replayed";
    private static final int CONCURRENT_REQUESTS = 20;

    @Autowired
    MockMvc mvc;

    @Test
    void theSameKeySentTwiceCreatesOneOrderAndReturnsItAgain() throws Exception {
        long variant = variantWithStock("IDEM-TWICE", 10);
        String key = newKey();

        MockHttpServletResponse first = mvc.perform(order(key, body("ana@example.com", line(variant, 2))))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(REPLAYED_HEADER))
                .andReturn().getResponse();
        MockHttpServletResponse second = mvc.perform(order(key, body("ana@example.com", line(variant, 2))))
                .andExpect(status().isCreated())
                .andExpect(header().string(REPLAYED_HEADER, "true"))
                .andReturn().getResponse();

        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(stockOf(variant)).isEqualTo(8);
        assertThat(unitsOrdered(variant)).isEqualTo(2);
        assertThat(ordersWithKey(key)).isEqualTo(1);
        long order = OrderNumbers.idOf(jdbc, first.getContentAsString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM background_jobs WHERE order_id = ?", Integer.class, order))
                .isEqualTo(1);
    }

    @Test
    void twentySimultaneousRequestsWithOneKeyLeaveOneOrder() throws Exception {
        long variant = variantWithStock("IDEM-RACE", 100);
        String key = newKey();
        String body = body("ana@example.com", line(variant, 1));

        List<MockHttpServletResponse> responses = sendConcurrently(key, body);

        assertThat(responses).allSatisfy(response -> assertThat(response.getStatus()).isEqualTo(201));
        Set<String> ids = new TreeSet<>();
        for (MockHttpServletResponse response : responses) {
            ids.add(JsonPath.read(response.getContentAsString(), "$.number"));
        }
        assertThat(ids).hasSize(1);
        assertThat(responses).filteredOn(response -> response.getHeader(REPLAYED_HEADER) == null).hasSize(1);
        assertThat(ordersWithKey(key)).isEqualTo(1);
        assertThat(stockOf(variant)).isEqualTo(99);
        assertThat(unitsOrdered(variant)).isEqualTo(1);
    }

    @Test
    void theSameKeyWithADifferentOrderIsRejected() throws Exception {
        long variant = variantWithStock("IDEM-OTHER", 10);
        String key = newKey();
        mvc.perform(order(key, body("ana@example.com", line(variant, 2)))).andExpect(status().isCreated());

        mvc.perform(order(key, body("ana@example.com", line(variant, 3))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.idempotencyKeyReused").value(true));
        mvc.perform(order(key, body("marko@example.com", line(variant, 2))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.idempotencyKeyReused").value(true));

        assertThat(ordersWithKey(key)).isEqualTo(1);
        assertThat(stockOf(variant)).isEqualTo(8);
    }

    @Test
    void itemsInAnotherOrderOrSplitOverTwoLinesAreTheSameOrder() throws Exception {
        long first = variantWithStock("IDEM-A", 10);
        long second = variantWithStock("IDEM-B", 10);
        String key = newKey();
        mvc.perform(order(key, body("ana@example.com", line(first, 2), line(second, 1))))
                .andExpect(status().isCreated());

        mvc.perform(order(key, body("ana@example.com", line(second, 1), line(first, 1), line(first, 1))))
                .andExpect(status().isCreated())
                .andExpect(header().string(REPLAYED_HEADER, "true"));

        assertThat(ordersWithKey(key)).isEqualTo(1);
    }

    @Test
    void aKeyWhoseFirstAttemptFailedCanBeUsedAgain() throws Exception {
        long variant = variantWithStock("IDEM-RETRY", 1);
        String key = newKey();
        String body = body("ana@example.com", line(variant, 2));

        mvc.perform(order(key, body)).andExpect(status().isUnprocessableContent());
        jdbc.update("UPDATE inventory SET quantity = 5 WHERE variant_id = ?", variant);
        mvc.perform(order(key, body))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(REPLAYED_HEADER));

        assertThat(ordersWithKey(key)).isEqualTo(1);
        assertThat(stockOf(variant)).isEqualTo(3);
    }

    @Test
    void requestsWithoutAKeyStillCreateAnOrderEachTime() throws Exception {
        long variant = variantWithStock("IDEM-NONE", 10);
        String body = body("ana@example.com", line(variant, 1));

        mvc.perform(order(null, body)).andExpect(status().isCreated());
        mvc.perform(order(null, body)).andExpect(status().isCreated());

        assertThat(unitsOrdered(variant)).isEqualTo(2);
    }

    @Test
    void malformedKeysAreRejectedBeforeAnythingIsWritten() throws Exception {
        long variant = variantWithStock("IDEM-BAD", 10);
        String body = body("ana@example.com", line(variant, 1));

        for (String key : List.of("", "short", "has space-in-the-key", "a".repeat(101), "ključ-sa-dijakritikom-12")) {
            mvc.perform(order(key, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(KEY_HEADER))
                    .andExpect(jsonPath("$.errors[0].code").value("Pattern"));
        }

        assertThat(unitsOrdered(variant)).isZero();
    }

    private List<MockHttpServletResponse> sendConcurrently(String key, String body) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(order(key, body)).andReturn().getResponse();
                }));
            }
            start.countDown();
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private int ordersWithKey(String key) {
        return jdbc.queryForObject("SELECT count(*) FROM orders WHERE idempotency_key = ?", Integer.class, key);
    }

    private static MockHttpServletRequestBuilder order(String key, String body) {
        MockHttpServletRequestBuilder request = post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return key == null ? request : request.header(KEY_HEADER, key);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static String line(long variant, int quantity) {
        return "{\"variantId\":%d,\"quantity\":%d}".formatted(variant, quantity);
    }

    private static String body(String email, String... lines) {
        return """
                {"customer":{"name":"Ana Anić","email":"%s","phone":"0601234567",
                "shippingAddress":"Ulica 1, Beograd"},"items":[%s]}
                """.formatted(email, String.join(",", lines));
    }
}
