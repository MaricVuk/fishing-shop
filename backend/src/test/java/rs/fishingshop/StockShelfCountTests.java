package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.ErrorResponseException;
import rs.fishingshop.orders.CreateOrderRequest;
import rs.fishingshop.orders.CustomerRequest;
import rs.fishingshop.orders.OrderItemRequest;
import rs.fishingshop.orders.OrderService;
import rs.fishingshop.stock.StockService;

class StockShelfCountTests extends CommittedOrderTestSupport {

    @Autowired
    OrderService orders;

    @Autowired
    StockService stock;

    @Test
    void aCountOfTheShelfDoesNotResellUnitsThatOpenOrdersHold() {
        long variant = variantWithStock("SHELF-A", 5);
        long order = placeOrder(variant, 2);
        assertThat(stockOf(variant)).isEqualTo(3);

        stock.adjust(variant, 5, "Popis police");

        assertThat(stockOf(variant)).isEqualTo(3);
        assertThat(stock.history(variant)).extracting(entry -> entry.type().name()).doesNotContain("ADJUSTMENT");

        stock.adjust(variant, 4, "Popis police, jedan manjak");

        assertThat(stockOf(variant)).isEqualTo(2);
        assertThat(order).isPositive();
    }

    @Test
    void aShelfCountBelowWhatOpenOrdersHoldIsRefused() {
        long variant = variantWithStock("SHELF-B", 5);
        placeOrder(variant, 3);

        assertThatThrownBy(() -> stock.adjust(variant, 2, "Popis"))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        problem -> assertThat(problem.getStatusCode().value()).isEqualTo(422));

        assertThat(stockOf(variant)).isEqualTo(2);
    }

    @Test
    void unitsOfAShippedOrderAreNoLongerOnTheShelf() {
        long variant = variantWithStock("SHELF-C", 5);
        long order = placeOrder(variant, 2);
        jdbc.update("UPDATE orders SET status = 'SHIPPED' WHERE id = ?", order);

        stock.adjust(variant, 3, "Popis police");

        assertThat(stockOf(variant)).isEqualTo(3);
        assertThat(stock.history(variant)).extracting(entry -> entry.type().name()).doesNotContain("ADJUSTMENT");
    }

    @Test
    void aReceiptThatWouldPassTheStockLimitIsRefused() {
        long variant = variantWithStock("SHELF-D", 999_995);

        assertThatThrownBy(() -> stock.receive(variant, 10, null))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        problem -> assertThat(problem.getStatusCode().value()).isEqualTo(422));

        assertThat(stockOf(variant)).isEqualTo(999_995);
        stock.receive(variant, 5, null);
        assertThat(stockOf(variant)).isEqualTo(1_000_000);
    }

    private long placeOrder(long variant, int quantity) {
        CreateOrderRequest request = new CreateOrderRequest(
                new CustomerRequest("Marko Marković", "marko@example.com", null, "Knez Mihailova 1, Beograd"),
                List.of(new OrderItemRequest(variant, quantity)));
        String number = orders.create(request).number();
        return jdbc.queryForObject("SELECT id FROM orders WHERE public_token = ?", Long.class,
                number.replace("-", ""));
    }
}
