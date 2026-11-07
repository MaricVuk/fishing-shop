package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import rs.fishingshop.jobs.JobWorker;

@AutoConfigureMockMvc
@TestPropertySource(properties = "shop.orders.max-per-address=1000000")
class OrderPublicNumberTests extends CommittedOrderTestSupport {

    private static final String NUMBER_FORMAT = "[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}";

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

    @AfterEach
    void removeRawOrders() {
        jdbc.update("DELETE FROM orders WHERE customer_name IN ('Raw', 'Dup', 'Bad')");
    }

    @Test
    void theCustomerGetsARandomNumberAndNeverTheInternalId() throws Exception {
        long variant = variantWithStock("NUM-A", 100);

        String response = place(variant, "ana@example.com", null);

        assertThat((String) JsonPath.read(response, "$.number")).matches(NUMBER_FORMAT);
        assertThat(JsonPath.<java.util.Map<String, Object>>read(response, "$")).doesNotContainKey("id");
    }

    @Test
    void numbersDoNotFollowTheOrderOfTheOrders() throws Exception {
        long variant = variantWithStock("NUM-B", 100);
        Set<String> numbers = new HashSet<>();

        for (int i = 0; i < 30; i++) {
            numbers.add(JsonPath.read(place(variant, "ana@example.com", null), "$.number"));
        }

        assertThat(numbers).hasSize(30);
        List<String> byId = jdbc.queryForList(
                "SELECT public_token FROM orders WHERE customer_email = 'ana@example.com' ORDER BY id", String.class);
        assertThat(byId).isNotEqualTo(byId.stream().sorted().toList());
        assertThat(byId).isNotEqualTo(byId.stream().sorted().toList().reversed());
    }

    @Test
    void theNumberIsStoredWithoutDashesAndTheDatabaseGivesOneToEveryRow() throws Exception {
        long variant = variantWithStock("NUM-C", 100);
        String number = JsonPath.read(place(variant, "ana@example.com", null), "$.number");

        assertThat(jdbc.queryForObject(
                "SELECT public_token FROM orders WHERE customer_email = 'ana@example.com'", String.class))
                .isEqualTo(number.replace("-", ""))
                .matches("[0-9A-F]{12}");

        long rawInsert = jdbc.queryForObject("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total)
                VALUES ('Raw', 'ana@example.com', 'Ulica 1', 100.00) RETURNING id
                """, Long.class);
        assertThat(jdbc.queryForObject("SELECT public_token FROM orders WHERE id = ?", String.class, rawInsert))
                .matches("[0-9A-F]{12}");
    }

    @Test
    void theDatabaseRefusesADuplicateOrABadNumber() throws Exception {
        long variant = variantWithStock("NUM-D", 100);
        String number = JsonPath.<String>read(place(variant, "ana@example.com", null), "$.number")
                .replace("-", "");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total, public_token)
                VALUES ('Dup', 'ana@example.com', 'Ulica 1', 100.00, ?)
                """, number)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total, public_token)
                VALUES ('Bad', 'ana@example.com', 'Ulica 1', 100.00, 'lowercase123')
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aReplayedOrderKeepsItsNumber() throws Exception {
        long variant = variantWithStock("NUM-E", 100);
        String key = "0123456789abcdef0123456789abcdef";

        String first = place(variant, "ana@example.com", key);
        String second = place(variant, "ana@example.com", key);

        assertThat((String) JsonPath.read(second, "$.number")).isEqualTo(JsonPath.read(first, "$.number"));
    }

    @Test
    void theConfirmationEmailUsesTheNumberAndNotTheInternalId() throws Exception {
        long variant = variantWithStock("NUM-F", 100);
        String response = place(variant, "ana@example.com", null);
        String number = JsonPath.read(response, "$.number");
        long id = OrderNumbers.idOf(jdbc, response);

        worker.runOnce();

        String messages = Mailpit.messages();
        assertThat((String) JsonPath.read(messages, "$.messages[0].Subject")).contains(number).doesNotContain("#");
        assertThat((String) JsonPath.read(messages, "$.messages[0].MessageID"))
                .isEqualTo("order-confirmation-" + number.replace("-", "") + "@fishing-shop.test");
        String text = JsonPath.read(Mailpit.message(JsonPath.read(messages, "$.messages[0].ID")), "$.Text");
        assertThat(text).contains("Porudžbina " + number).doesNotContain("#" + id);
    }

    private String place(long variant, String email, String idempotencyKey) throws Exception {
        var request = post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"customer":{"name":"Ana Anić","email":"%s","shippingAddress":"Ulica 1, Beograd"},
                 "items":[{"variantId":%d,"quantity":1}]}
                """.formatted(email, variant));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").exists())
                .andReturn().getResponse().getContentAsString();
    }
}
