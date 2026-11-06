package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.ErrorResponseException;
import rs.fishingshop.orders.CreateOrderRequest;
import rs.fishingshop.orders.CustomerRequest;
import rs.fishingshop.orders.OrderItemRequest;
import rs.fishingshop.orders.OrderService;

class ConcurrentOrderTests extends CommittedOrderTestSupport {

    private static final int BUYERS = 50;
    private static final int DEADLOCK_ROUNDS = 20;

    private enum Outcome { CREATED, REJECTED, FAILED }

    @Autowired
    OrderService orders;

    @Test
    void fiftyBuyersOfTheLastItemLeaveExactlyOneOrder() throws Exception {
        long variant = variantWithStock("LAST", 1);

        List<Outcome> outcomes = buyConcurrently(BUYERS, () -> buy(List.of(variant), 1));

        assertThat(outcomes).filteredOn(Outcome.CREATED::equals).hasSize(1);
        assertThat(outcomes).filteredOn(Outcome.REJECTED::equals).hasSize(BUYERS - 1);
        assertThat(stockOf(variant)).isZero();
        assertThat(unitsOrdered(variant)).isEqualTo(1);
    }

    @Test
    void asManyBuyersSucceedAsThereAreUnits() throws Exception {
        long variant = variantWithStock("THREE", 3);

        List<Outcome> outcomes = buyConcurrently(BUYERS, () -> buy(List.of(variant), 1));

        assertThat(outcomes).filteredOn(Outcome.CREATED::equals).hasSize(3);
        assertThat(outcomes).filteredOn(Outcome.REJECTED::equals).hasSize(BUYERS - 3);
        assertThat(stockOf(variant)).isZero();
        assertThat(unitsOrdered(variant)).isEqualTo(3);
    }

    @Test
    void ordersWithTheSameVariantsInOppositeOrderDoNotDeadlock() throws Exception {
        for (int round = 0; round < DEADLOCK_ROUNDS; round++) {
            long first = variantWithStock("D" + round + "-A", 2);
            long second = variantWithStock("D" + round + "-B", 2);

            List<Outcome> outcomes = buyConcurrently(List.of(
                    () -> buy(List.of(first, second), 1),
                    () -> buy(List.of(second, first), 1)));

            assertThat(outcomes).containsOnly(Outcome.CREATED);
            assertThat(stockOf(first)).isZero();
            assertThat(stockOf(second)).isZero();
        }
    }

    private List<Outcome> buyConcurrently(int buyers, Callable<Outcome> purchase) throws Exception {
        List<Callable<Outcome>> purchases = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            purchases.add(purchase);
        }
        return buyConcurrently(purchases);
    }

    private List<Outcome> buyConcurrently(List<Callable<Outcome>> purchases) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(purchases.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<Outcome> purchase : purchases) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return purchase.call();
                }));
            }
            start.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private Outcome buy(List<Long> variantIds, int quantity) {
        List<OrderItemRequest> items = variantIds.stream()
                .map(id -> new OrderItemRequest(id, quantity))
                .toList();
        CreateOrderRequest request = new CreateOrderRequest(
                new CustomerRequest("Marko Marković", "marko@example.com", null, "Knez Mihailova 1, Beograd"),
                items);
        try {
            orders.create(request);
            return Outcome.CREATED;
        } catch (ErrorResponseException rejected) {
            return rejected.getStatusCode().value() == 422 ? Outcome.REJECTED : Outcome.FAILED;
        } catch (RuntimeException failed) {
            return Outcome.FAILED;
        }
    }
}
