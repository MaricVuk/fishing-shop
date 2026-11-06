package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import rs.fishingshop.stock.StockMovementEntry;
import rs.fishingshop.stock.StockMovementType;
import rs.fishingshop.stock.StockService;
import rs.fishingshop.stock.StockVariantNotFoundException;

@AutoConfigureMockMvc
class StockMovementTests extends CommittedOrderTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    StockService stock;

    @Test
    void anOrderLeavesAnOutMovementPointingAtTheOrder() throws Exception {
        long variant = variantWithStock("MOVE-OUT", 5);

        placeOrder(variant, 2).andExpect(status().isCreated());

        List<StockMovementEntry> history = stock.history(variant);
        assertThat(history).hasSize(1);
        StockMovementEntry out = history.getFirst();
        assertThat(out.type()).isEqualTo(StockMovementType.OUT);
        assertThat(out.quantityChange()).isEqualTo(-2);
        assertThat(out.quantityAfter()).isEqualTo(3);
        assertThat(out.orderId()).isEqualTo(lastOrderIdFor(variant));
    }

    @Test
    void aRejectedOrderLeavesNoMovement() throws Exception {
        long variant = variantWithStock("MOVE-REJECT", 1);

        placeOrder(variant, 2).andExpect(status().isUnprocessableContent());

        assertThat(stock.history(variant)).isEmpty();
    }

    @Test
    void receivingStockAddsAnInMovement() {
        long variant = variantWithStock("MOVE-IN", 2);

        stock.receive(variant, 10, "Delivery 42");

        assertThat(stockOf(variant)).isEqualTo(12);
        assertThat(stock.history(variant)).singleElement().satisfies(entry -> {
            assertThat(entry.type()).isEqualTo(StockMovementType.IN);
            assertThat(entry.quantityChange()).isEqualTo(10);
            assertThat(entry.quantityAfter()).isEqualTo(12);
            assertThat(entry.reason()).isEqualTo("Delivery 42");
            assertThat(entry.orderId()).isNull();
        });
    }

    @Test
    void receivingStockForAVariantWithoutAStockRowCreatesTheRow() {
        long variant = variantWithoutStockRow("MOVE-NEW");

        stock.receive(variant, 4, null);

        assertThat(stockOf(variant)).isEqualTo(4);
    }

    @Test
    void anAdjustmentRecordsTheSignedDifferenceToTheCount() {
        long variant = variantWithStock("MOVE-ADJ", 10);

        stock.adjust(variant, 7, "Inventory count");

        assertThat(stockOf(variant)).isEqualTo(7);
        assertThat(stock.history(variant)).singleElement().satisfies(entry -> {
            assertThat(entry.type()).isEqualTo(StockMovementType.ADJUSTMENT);
            assertThat(entry.quantityChange()).isEqualTo(-3);
            assertThat(entry.quantityAfter()).isEqualTo(7);
        });
    }

    @Test
    void anAdjustmentThatMatchesTheStockIsNotAMovement() {
        long variant = variantWithStock("MOVE-SAME", 6);

        stock.adjust(variant, 6, "Inventory count");

        assertThat(stock.history(variant)).isEmpty();
    }

    @Test
    void theHistoryExplainsTheCurrentStock() throws Exception {
        long variant = variantWithoutStockRow("MOVE-SUM");

        stock.receive(variant, 20, "First delivery");
        placeOrder(variant, 3).andExpect(status().isCreated());
        stock.adjust(variant, 15, "Two broken in the warehouse");
        placeOrder(variant, 5).andExpect(status().isCreated());
        stock.receive(variant, 4, null);

        // The shelf held 15, and 3 of them belong to the open first order, so 12 are left to sell.

        List<StockMovementEntry> history = stock.history(variant);
        assertThat(history).extracting(StockMovementEntry::type).containsExactly(
                StockMovementType.IN, StockMovementType.OUT, StockMovementType.ADJUSTMENT,
                StockMovementType.OUT, StockMovementType.IN);
        assertThat(history.stream().mapToInt(StockMovementEntry::quantityChange).sum()).isEqualTo(stockOf(variant));
        assertThat(history.getLast().quantityAfter()).isEqualTo(stockOf(variant)).isEqualTo(11);
    }

    @Test
    void twoDeliveriesForAVariantWithoutAStockRowAreBothCounted() throws Exception {
        long variant = variantWithoutStockRow("MOVE-RACE");
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> {
            await(start);
            stock.receive(variant, 3, null);
        });
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> {
            await(start);
            stock.receive(variant, 5, null);
        });
        start.countDown();
        CompletableFuture.allOf(first, second).get(30, TimeUnit.SECONDS);

        assertThat(stockOf(variant)).isEqualTo(8);
        assertThat(stock.history(variant)).hasSize(2);
    }

    @Test
    void rejectsInvalidStockOperations() {
        long variant = variantWithStock("MOVE-INVALID", 5);

        assertThatThrownBy(() -> stock.receive(variant, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stock.receive(variant, -1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stock.adjust(variant, -1, "Count")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stock.adjust(variant, 3, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stock.receive(Long.MAX_VALUE, 1, null))
                .isInstanceOf(StockVariantNotFoundException.class);
        assertThatThrownBy(() -> stock.receive(variant, Integer.MAX_VALUE, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(stockOf(variant)).isEqualTo(5);
        assertThat(stock.history(variant)).isEmpty();
    }

    @Test
    void theDatabaseRejectsMovementsThatContradictTheirType() {
        long variant = variantWithStock("MOVE-CHECK", 5);
        long order = jdbc.queryForObject("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total)
                VALUES ('Test', 'test@example.com', 'Ulica 1', 0) RETURNING id
                """, Long.class);
        try {
            List<Map.Entry<String, Object[]>> invalid = List.of(
                    Map.entry("OUT with a positive change", new Object[] {"OUT", 1, 5, order, null}),
                    Map.entry("OUT without an order", new Object[] {"OUT", -1, 4, null, null}),
                    Map.entry("IN with a negative change", new Object[] {"IN", -1, 4, null, null}),
                    Map.entry("IN with an order", new Object[] {"IN", 1, 6, order, null}),
                    Map.entry("ADJUSTMENT of zero", new Object[] {"ADJUSTMENT", 0, 5, null, "Count"}),
                    Map.entry("ADJUSTMENT without a reason", new Object[] {"ADJUSTMENT", 1, 6, null, null}),
                    Map.entry("negative stock after the movement", new Object[] {"OUT", -9, -4, order, null}),
                    Map.entry("unknown type", new Object[] {"LOST", -1, 4, null, null}));

            for (Map.Entry<String, Object[]> row : invalid) {
                Object[] values = row.getValue();
                assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO stock_movements (variant_id, type, quantity_change, quantity_after, order_id, reason)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, variant, values[0], values[1], values[2], values[3], values[4]))
                        .as(row.getKey())
                        .isInstanceOf(DataIntegrityViolationException.class);
            }
        } finally {
            jdbc.update("DELETE FROM orders WHERE id = ?", order);
        }
    }

    private ResultActions placeOrder(long variantId, int quantity) throws Exception {
        String body = """
                {"customer":{"name":"Marko Marković","email":"marko@example.com",
                 "shippingAddress":"Knez Mihailova 1, Beograd"},
                 "items":[{"variantId":%d,"quantity":%d}]}""".formatted(variantId, quantity);
        return mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Long lastOrderIdFor(long variantId) {
        return jdbc.queryForObject(
                "SELECT max(order_id) FROM order_items WHERE variant_id = ?", Long.class, variantId);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
