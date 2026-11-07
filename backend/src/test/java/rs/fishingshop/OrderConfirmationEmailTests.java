package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import rs.fishingshop.jobs.JobWorker;

@AutoConfigureMockMvc
class OrderConfirmationEmailTests extends CommittedOrderTestSupport {

    @DynamicPropertySource
    static void mailProps(DynamicPropertyRegistry registry) {
        Mailpit.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JobWorker worker;

    @BeforeEach
    void emptyMailbox() {
        Mailpit.clear();
    }

    @Test
    void theWorkerSendsOneConfirmationToTheCustomer() throws Exception {
        long variant = variantWithStock("MAIL-A", 5);
        long orderId = placeOrder(variant, 2);

        assertThat(worker.runOnce()).isEqualTo(1);

        String messages = Mailpit.messages();
        assertThat((Integer) JsonPath.read(messages, "$.total")).isEqualTo(1);
        assertThat((List<String>) JsonPath.read(messages, "$.messages[0].To[*].Address"))
                .containsExactly("marko@example.com");
        assertThat((String) JsonPath.read(messages, "$.messages[0].Subject")).contains(OrderNumbers.numberOf(jdbc, orderId));
        assertThat((String) JsonPath.read(messages, "$.messages[0].MessageID"))
                .isEqualTo("order-confirmation-" + OrderNumbers.tokenOf(jdbc, orderId) + "@fishing-shop.test");

        String id = JsonPath.read(messages, "$.messages[0].ID");
        String text = JsonPath.read(Mailpit.message(id), "$.Text");
        assertThat(text)
                .contains("Marko Marković")
                .contains("Committed Rod")
                .contains("COMMIT-MAIL-A")
                .contains("9.800,00 RSD")
                .contains("Knez Mihailova 1, Beograd");

        assertThat(jdbc.queryForObject(
                "SELECT status FROM background_jobs WHERE order_id = ?", String.class, orderId)).isEqualTo("DONE");
    }

    @Test
    void runningTheWorkerAgainDoesNotSendASecondEmail() throws Exception {
        long variant = variantWithStock("MAIL-B", 5);
        placeOrder(variant, 1);

        worker.runOnce();
        worker.runOnce();

        String messages = Mailpit.messages();
        assertThat((Integer) JsonPath.read(messages, "$.total")).isEqualTo(1);
    }

    @Test
    void anAddressTheMailServerCannotUseIsNotRetried() {
        long orderId = jdbc.queryForObject("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total)
                VALUES ('Los Mejl', 'a@b@c.rs', 'Ulica 1', 0) RETURNING id
                """, Long.class);
        try {
            jdbc.update("INSERT INTO background_jobs (type, order_id) VALUES ('ORDER_CONFIRMATION_EMAIL', ?)", orderId);

            worker.runOnce();

            assertThat(jdbc.queryForObject("SELECT status FROM background_jobs WHERE order_id = ?", String.class,
                    orderId)).isEqualTo("DEAD");
        } finally {
            jdbc.update("DELETE FROM background_jobs WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM orders WHERE id = ?", orderId);
        }
    }

    private long placeOrder(long variantId, int quantity) throws Exception {
        String body = """
                {"customer":{"name":"Marko Marković","email":"marko@example.com",
                 "shippingAddress":"Knez Mihailova 1, Beograd"},
                 "items":[{"variantId":%d,"quantity":%d}]}""".formatted(variantId, quantity);
        String response = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return OrderNumbers.idOf(jdbc, response);
    }
}
