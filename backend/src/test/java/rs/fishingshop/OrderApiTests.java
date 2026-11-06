package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class OrderApiTests extends CatalogTestSupport {

    private static final String CUSTOMER = """
            {"name":"Marko Marković","email":"marko@example.com","phone":"+381 60 123 4567",
             "shippingAddress":"Knez Mihailova 1, Beograd"}""";

    @Autowired
    MockMvc mvc;

    long shortRod;
    long longRod;

    @BeforeEach
    void catalog() {
        long product = product("stapovi", "Order Rod", "Acme", true);
        variantWithAttributes(product, "OR-240", "{\"length\":\"2.4m\"}", "4500.00", true, 5);
        variantWithAttributes(product, "OR-270", "{\"length\":\"2.7m\"}", "4900.00", true, 0);
        shortRod = variantId("OR-240");
        longRod = variantId("OR-270");
    }

    @Test
    void createsOrderWithComputedTotalAndNewStatus() throws Exception {
        placeOrder(CUSTOMER, item(shortRod, 2) + "," + item(longRod, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.total").value(13900.0))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].productName").value("Order Rod"))
                .andExpect(jsonPath("$.items[0].sku").value("OR-240"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(4500.0))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(9000.0));
    }

    @Test
    void doesNotExposeCustomerDataInTheResponse() throws Exception {
        placeOrder(CUSTOMER, item(shortRod, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andExpect(jsonPath("$.customerEmail").doesNotExist())
                .andExpect(jsonPath("$.shippingAddress").doesNotExist());
    }

    @Test
    void copiesNameSkuAndPriceSoLaterChangesDoNotAffectTheOrder() throws Exception {
        placeOrder(CUSTOMER, item(shortRod, 2)).andExpect(status().isCreated());

        jdbc.update("UPDATE product_variants SET price = 9999.00 WHERE id = ?", shortRod);
        jdbc.update("UPDATE products SET name = 'Renamed Rod' WHERE name = 'Order Rod'");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT product_name, sku, unit_price, quantity, variant_id FROM order_items");
        assertThat(row.get("product_name")).isEqualTo("Order Rod");
        assertThat(row.get("sku")).isEqualTo("OR-240");
        assertThat((BigDecimal) row.get("unit_price")).isEqualByComparingTo("4500.00");
        assertThat(row.get("quantity")).isEqualTo(2);
        assertThat(row.get("variant_id")).isEqualTo(shortRod);
    }

    @Test
    void storesTrimmedCustomerDataAndNullPhoneWhenBlank() throws Exception {
        String customer = """
                {"name":"  Ana Anić  ","email":"ana@example.com","phone":"",
                 "shippingAddress":"  Bulevar 5, Novi Sad "}""";

        placeOrder(customer, item(shortRod, 1)).andExpect(status().isCreated());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT customer_name, customer_email, customer_phone, shipping_address, status FROM orders");
        assertThat(row.get("customer_name")).isEqualTo("Ana Anić");
        assertThat(row.get("customer_email")).isEqualTo("ana@example.com");
        assertThat(row.get("customer_phone")).isNull();
        assertThat(row.get("shipping_address")).isEqualTo("Bulevar 5, Novi Sad");
        assertThat(row.get("status")).isEqualTo("NEW");
    }

    @Test
    void rejectsEmailWithSurroundingWhitespaceInsteadOfSilentlyFixingIt() throws Exception {
        String customer = """
                {"name":"Ana Anić","email":" ana@example.com ","shippingAddress":"Bulevar 5, Novi Sad"}""";

        placeOrder(customer, item(shortRod, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'customer.email')].code").value("Email"));
    }

    @Test
    void mergesDuplicateVariantLines() throws Exception {
        placeOrder(CUSTOMER, item(shortRod, 1) + "," + item(shortRod, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.total").value(13500.0));
    }

    @Test
    void rejectsMergedQuantityAboveTheLimit() throws Exception {
        placeOrder(CUSTOMER, item(shortRod, 60) + "," + item(shortRod, 60))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.variantId").value(shortRod));
        assertThat(orderCount()).isZero();
    }

    @Test
    void rejectsUnknownAndInactiveVariantsAndStoresNothing() throws Exception {
        long inactiveVariantProduct = product("stapovi", "Inactive Variant Rod", "Acme", true);
        variant(inactiveVariantProduct, "OR-INACTIVE", "100.00", false, 1);
        long inactiveProduct = product("stapovi", "Inactive Product Rod", "Acme", false);
        variant(inactiveProduct, "OR-HIDDEN", "100.00", true, 1);
        long inactiveVariant = variantId("OR-INACTIVE");
        long hidden = variantId("OR-HIDDEN");

        placeOrder(CUSTOMER, item(shortRod, 1) + "," + item(inactiveVariant, 1) + "," + item(hidden, 1) + "," + item(987654321L, 1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.unavailableVariantIds", contains(
                        (int) inactiveVariant, (int) hidden, 987654321)));

        assertThat(orderCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items", Integer.class)).isZero();
    }

    @Test
    void reportsEveryInvalidCustomerFieldWithItsCode() throws Exception {
        String customer = """
                {"name":" ","email":"not-an-email","phone":"abc","shippingAddress":""}""";

        placeOrder(customer, item(shortRod, 1))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'customer.name')].code").value("NotBlank"))
                .andExpect(jsonPath("$.errors[?(@.field == 'customer.email')].code").value("Email"))
                .andExpect(jsonPath("$.errors[?(@.field == 'customer.phone')].code").value("Pattern"))
                .andExpect(jsonPath("$.errors[?(@.field == 'customer.shippingAddress')].code").value("NotBlank"));
        assertThat(orderCount()).isZero();
    }

    @Test
    void reportsInvalidItemsWithIndexedFieldNames() throws Exception {
        placeOrder(CUSTOMER, item(shortRod, 0) + "," + item(longRod, 100) + ",{\"quantity\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].quantity')].code").value("Min"))
                .andExpect(jsonPath("$.errors[?(@.field == 'items[1].quantity')].code").value("Max"))
                .andExpect(jsonPath("$.errors[?(@.field == 'items[2].variantId')].code").value("NotNull"));
    }

    @Test
    void rejectsEmptyAndOversizedItemLists() throws Exception {
        placeOrder(CUSTOMER, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'items')].code").value("NotEmpty"));

        String tooMany = String.join(",", Collections.nCopies(51, item(shortRod, 1)));
        placeOrder(CUSTOMER, tooMany)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'items')].code").value("Size"));
    }

    @Test
    void rejectsMissingCustomerAndMalformedJson() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[" + item(shortRod, 1) + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'customer')].code").value("NotNull"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(orderCount()).isZero();
    }

    @Test
    void doesNotExposeOrdersForReading() throws Exception {
        placeOrder(CUSTOMER, item(shortRod, 1)).andExpect(status().isCreated());

        mvc.perform(get("/api/orders/1")).andExpect(status().is4xxClientError());
    }

    private ResultActions placeOrder(String customerJson, String itemsJson) throws Exception {
        String body = "{\"customer\":" + customerJson + ",\"items\":[" + itemsJson + "]}";
        return mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String item(long variantId, int quantity) {
        return "{\"variantId\":%d,\"quantity\":%d}".formatted(variantId, quantity);
    }

    private int orderCount() {
        return jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class);
    }
}
