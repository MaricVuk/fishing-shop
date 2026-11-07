package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class OrderJobEnqueueTests extends CommittedOrderTestSupport {

    @Autowired
    MockMvc mvc;

    @Test
    void anAcceptedOrderGetsExactlyOnePendingConfirmationJob() throws Exception {
        long variant = variantWithStock("JOB-A", 5);

        long orderId = orderIdOf(placeOrder(item(variant, 1)).andExpect(status().isCreated()));

        List<Map<String, Object>> jobs = jdbc.queryForList(
                "SELECT type, status, attempts FROM background_jobs WHERE order_id = ?", orderId);
        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.get("type")).isEqualTo("ORDER_CONFIRMATION_EMAIL");
            assertThat(job.get("status")).isEqualTo("PENDING");
            assertThat(job.get("attempts")).isEqualTo(0);
        });
    }

    @Test
    void aRejectedOrderLeavesNoJob() throws Exception {
        long outOfStock = variantWithStock("JOB-B", 0);
        int jobsBefore = jobCount();

        placeOrder(item(outOfStock, 1)).andExpect(status().isUnprocessableContent());

        assertThat(jobCount()).isEqualTo(jobsBefore);
    }

    @Test
    void theSameOrderCannotGetTwoConfirmationJobs() throws Exception {
        long variant = variantWithStock("JOB-C", 5);
        long orderId = orderIdOf(placeOrder(item(variant, 1)).andExpect(status().isCreated()));

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO background_jobs (type, order_id) VALUES ('ORDER_CONFIRMATION_EMAIL', ?)", orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int jobCount() {
        return jdbc.queryForObject("SELECT count(*) FROM background_jobs", Integer.class);
    }

    private long orderIdOf(ResultActions result) throws Exception {
        return OrderNumbers.idOf(jdbc, result.andReturn().getResponse().getContentAsString());
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
