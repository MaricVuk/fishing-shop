package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
        variantWithAttributes(product, "OR-270", "{\"length\":\"2.7m\"}", "4900.00", true, 3);
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
    void totalIsExactAndStoredForFractionalPrices() throws Exception {
        long product = product("stapovi", "Fraction Rod", "Acme", true);
        variant(product, "FR-A", "1999.99", true, 3);
        variant(product, "FR-B", "0.10", true, 7);

        MvcResult result = placeOrder(CUSTOMER, item(variantId("FR-A"), 3) + "," + item(variantId("FR-B"), 7))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("\"total\":6000.67")))
                .andExpect(content().string(containsString("\"lineTotal\":5999.97")))
                .andExpect(content().string(containsString("\"lineTotal\":0.70")))
                .andReturn();

        long orderId = orderId(result);
        assertThat(jdbc.queryForObject("SELECT total FROM orders WHERE id = ?", BigDecimal.class, orderId))
                .isEqualByComparingTo("6000.67");
        List<BigDecimal> unitPrices = jdbc.queryForList(
                "SELECT unit_price FROM order_items WHERE order_id = ? ORDER BY id", BigDecimal.class, orderId);
        assertThat(unitPrices).hasSize(2);
        assertThat(unitPrices.get(0)).isEqualByComparingTo("1999.99");
        assertThat(unitPrices.get(1)).isEqualByComparingTo("0.10");
    }

    @Test
    void responseContainsOnlyOrderFieldsAndNoCustomerData() throws Exception {
        placeOrder(CUSTOMER, item(shortRod, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.items").exists())
                .andExpect(jsonPath("$.items[0].length()").value(5))
                .andExpect(content().string(not(containsString("marko@example.com"))))
                .andExpect(content().string(not(containsString("Marković"))))
                .andExpect(content().string(not(containsString("Knez Mihailova"))))
                .andExpect(content().string(not(containsString("+381"))));
    }

    @Test
    void ordersCannotBeReadBack() throws Exception {
        long orderId = orderId(placeOrder(CUSTOMER, item(shortRod, 1)).andExpect(status().isCreated()).andReturn());

        mvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(not(containsString("marko@example.com"))));
        mvc.perform(get("/api/orders")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void copiesNameSkuAndPriceSoLaterChangesDoNotAffectTheOrder() throws Exception {
        long orderId = orderId(placeOrder(CUSTOMER, item(shortRod, 2)).andExpect(status().isCreated()).andReturn());

        jdbc.update("UPDATE product_variants SET price = 9999.00 WHERE id = ?", shortRod);
        jdbc.update("UPDATE products SET name = 'Renamed Rod' WHERE name = 'Order Rod'");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT product_name, sku, unit_price, quantity, variant_id FROM order_items WHERE order_id = ?",
                orderId);
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

        long orderId = orderId(placeOrder(customer, item(shortRod, 1)).andExpect(status().isCreated()).andReturn());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT customer_name, customer_email, customer_phone, shipping_address, status FROM orders WHERE id = ?",
                orderId);
        assertThat(row.get("customer_name")).isEqualTo("Ana Anić");
        assertThat(row.get("customer_email")).isEqualTo("ana@example.com");
        assertThat(row.get("customer_phone")).isNull();
        assertThat(row.get("shipping_address")).isEqualTo("Bulevar 5, Novi Sad");
        assertThat(row.get("status")).isEqualTo("NEW");
    }

    @Test
    void acceptsLineBreaksInTheShippingAddress() throws Exception {
        String customer = """
                {"name":"Ana Anić","email":"ana@example.com","shippingAddress":"Bulevar 5\\nStan 12, Novi Sad"}""";

        long orderId = orderId(placeOrder(customer, item(shortRod, 1)).andExpect(status().isCreated()).andReturn());

        assertThat(jdbc.queryForObject("SELECT shipping_address FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("Bulevar 5\nStan 12, Novi Sad");
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
    void rejectsAnOrderForMoreUnitsThanInStockAndKeepsTheStock() throws Exception {
        placeOrder(CUSTOMER, item(shortRod, 6) + "," + item(longRod, 1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.insufficientStockVariantIds", contains((int) shortRod)));

        assertThat(stockOf(shortRod)).isEqualTo(5);
        assertThat(stockOf(longRod)).isEqualTo(3);
    }

    @Test
    void listsEveryVariantWithoutEnoughStock() throws Exception {
        long product = product("stapovi", "Empty Rod", "Acme", true);
        variant(product, "OR-EMPTY", "100.00", true, 0);
        long empty = variantId("OR-EMPTY");

        placeOrder(CUSTOMER, item(empty, 1) + "," + item(longRod, 4) + "," + item(shortRod, 1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.insufficientStockVariantIds", hasSize(2)))
                .andExpect(jsonPath("$.insufficientStockVariantIds", containsInAnyOrder(
                        (int) empty, (int) longRod)));
    }

    @Test
    void treatsAVariantWithoutAStockRowAsOutOfStock() throws Exception {
        long product = product("stapovi", "Rowless Rod", "Acme", true);
        long rowless = jdbc.queryForObject("""
                INSERT INTO product_variants (product_id, sku, attributes, price, active)
                VALUES (?, 'OR-ROWLESS', '{}'::jsonb, 100.00, TRUE) RETURNING id
                """, Long.class, product);

        placeOrder(CUSTOMER, item(rowless, 1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.insufficientStockVariantIds", contains((int) rowless)));
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
        int before = orderCount();

        placeOrder(CUSTOMER, item(shortRod, 60) + "," + item(shortRod, 60))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.variantId").value(shortRod));

        assertThat(orderCount()).isEqualTo(before);
    }

    @Test
    void rejectsAnOrderTotalThatDoesNotFitTheColumn() throws Exception {
        long product = product("stapovi", "Gold Rod", "Acme", true);
        variant(product, "GOLD-1", "9999999999.99", true, 1);
        int before = orderCount();

        placeOrder(CUSTOMER, item(variantId("GOLD-1"), 2))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Order total is too large"));

        assertThat(orderCount()).isEqualTo(before);
    }

    @Test
    void rejectsUnknownAndInactiveVariantsAndStoresNothing() throws Exception {
        long inactiveVariantProduct = product("stapovi", "Inactive Variant Rod", "Acme", true);
        variant(inactiveVariantProduct, "OR-INACTIVE", "100.00", false, 1);
        long inactiveProduct = product("stapovi", "Inactive Product Rod", "Acme", false);
        variant(inactiveProduct, "OR-HIDDEN", "100.00", true, 1);
        long inactiveVariant = variantId("OR-INACTIVE");
        long hidden = variantId("OR-HIDDEN");
        int ordersBefore = orderCount();
        int itemsBefore = itemCount();

        placeOrder(CUSTOMER, item(shortRod, 1) + "," + item(inactiveVariant, 1) + "," + item(hidden, 1) + "," + item(987654321L, 1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.unavailableVariantIds", contains(
                        (int) inactiveVariant, (int) hidden, 987654321)));

        assertThat(orderCount()).isEqualTo(ordersBefore);
        assertThat(itemCount()).isEqualTo(itemsBefore);
    }

    @Test
    void reportsEveryInvalidCustomerFieldWithItsCode() throws Exception {
        String customer = """
                {"name":" ","email":"not-an-email","phone":"abc","shippingAddress":""}""";
        int before = orderCount();

        placeOrder(customer, item(shortRod, 1))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'customer.name')].code").value("NotBlank"))
                .andExpect(jsonPath("$.errors[?(@.field == 'customer.email')].code").value("Email"))
                .andExpect(jsonPath("$.errors[?(@.field == 'customer.phone')].code").value("Pattern"))
                .andExpect(jsonPath("$.errors[?(@.field == 'customer.shippingAddress')].code").value("NotBlank"));

        assertThat(orderCount()).isEqualTo(before);
    }

    @Test
    void rejectsControlCharactersInFreeText() throws Exception {
        String customer = """
                {"name":"a\\u0000b","email":"x@y.rs","shippingAddress":"Ulica\\u0007 1"}""";
        int before = orderCount();

        placeOrder(customer, item(shortRod, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'customer.name')].code").value("Pattern"))
                .andExpect(jsonPath("$.errors[?(@.field == 'customer.shippingAddress')].code").value("Pattern"));

        assertThat(orderCount()).isEqualTo(before);
    }

    @Test
    void phoneMustStartAndEndWithADigitOrBracket() throws Exception {
        for (String phone : new String[] {"......", "1     ", "12345", "+381 60 123 456 ", "abc 123456"}) {
            String customer = """
                    {"name":"Ana","email":"ana@example.com","phone":"%s","shippingAddress":"Ulica 1"}"""
                    .formatted(phone);
            placeOrder(customer, item(shortRod, 1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field == 'customer.phone')].code").value("Pattern"));
        }
        for (String phone : new String[] {"060 123 4567", "+381 (60) 123-4567", "123456"}) {
            String customer = """
                    {"name":"Ana","email":"ana@example.com","phone":"%s","shippingAddress":"Ulica 1"}"""
                    .formatted(phone);
            placeOrder(customer, item(shortRod, 1)).andExpect(status().isCreated());
        }
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
    void rejectsFractionalNumbersInsteadOfTruncatingThem() throws Exception {
        placeOrder(CUSTOMER, "{\"variantId\":%d,\"quantity\":2.7}".formatted(shortRod))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        placeOrder(CUSTOMER, "{\"variantId\":%d.9,\"quantity\":1}".formatted(shortRod))
                .andExpect(status().isBadRequest());
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
        int before = orderCount();

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[" + item(shortRod, 1) + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'customer')].code").value("NotNull"));

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(orderCount()).isEqualTo(before);
    }

    @Test
    void rejectsOversizedBodiesWith413BeforeValidation() throws Exception {
        String hugeAddress = "a".repeat(40_000);
        String customer = """
                {"name":"Ana","email":"ana@example.com","shippingAddress":"%s"}""".formatted(hugeAddress);
        int before = orderCount();

        placeOrder(customer, item(shortRod, 1))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(413));

        assertThat(orderCount()).isEqualTo(before);
    }

    private ResultActions placeOrder(String customerJson, String itemsJson) throws Exception {
        String body = "{\"customer\":" + customerJson + ",\"items\":[" + itemsJson + "]}";
        return mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long orderId(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private static String item(long variantId, int quantity) {
        return "{\"variantId\":%d,\"quantity\":%d}".formatted(variantId, quantity);
    }

    private int orderCount() {
        return jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class);
    }

    private int stockOf(long variantId) {
        return jdbc.queryForObject("SELECT quantity FROM inventory WHERE variant_id = ?", Integer.class, variantId);
    }

    private int itemCount() {
        return jdbc.queryForObject("SELECT count(*) FROM order_items", Integer.class);
    }
}
