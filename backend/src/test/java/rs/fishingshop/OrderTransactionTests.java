package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class OrderTransactionTests extends CommittedOrderTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    TransactionTemplate transactions;

    @Test
    void aRejectedOrderLeavesNoTraceInTheDatabase() throws Exception {
        long inStock = variantWithStock("ROLLBACK-A", 5);
        long outOfStock = variantWithStock("ROLLBACK-B", 0);
        int ordersBefore = orderCount();

        placeOrder(item(inStock, 2) + "," + item(outOfStock, 1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.insufficientStockVariantIds", contains((int) outOfStock)));

        assertThat(stockOf(inStock)).isEqualTo(5);
        assertThat(unitsOrdered(inStock)).isZero();
        assertThat(orderCount()).isEqualTo(ordersBefore);
    }

    @Test
    void anAcceptedOrderIsCommittedTogetherWithTheStockChange() throws Exception {
        long variant = variantWithStock("COMMIT-A", 5);

        placeOrder(item(variant, 2)).andExpect(status().isCreated());

        assertThat(stockOf(variant)).isEqualTo(3);
        assertThat(unitsOrdered(variant)).isEqualTo(2);
    }

    @Test
    void answers503WhenTheStockRowStaysLockedTooLong() throws Exception {
        long variant = variantWithStock("LOCKED", 5);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(tx -> {
            jdbc.queryForObject("SELECT quantity FROM inventory WHERE variant_id = ? FOR UPDATE", Integer.class, variant);
            locked.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }));

        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Instant started = Instant.now();

            placeOrder(item(variant, 1))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "1"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

            assertThat(Duration.between(started, Instant.now())).isGreaterThanOrEqualTo(Duration.ofSeconds(2));
        } finally {
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
        }

        assertThat(stockOf(variant)).isEqualTo(5);
        assertThat(unitsOrdered(variant)).isZero();
    }

    private ResultActions placeOrder(String itemsJson) throws Exception {
        String body = """
                {"customer":{"name":"Marko Marković","email":"marko@example.com",
                 "shippingAddress":"Knez Mihailova 1, Beograd"},"items":[%s]}""".formatted(itemsJson);
        return mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String item(long variantId, int quantity) {
        return "{\"variantId\":%d,\"quantity\":%d}".formatted(variantId, quantity);
    }
}
