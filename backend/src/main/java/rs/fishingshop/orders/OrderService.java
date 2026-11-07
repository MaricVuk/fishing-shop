package rs.fishingshop.orders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.fishingshop.catalog.ProductVariant;
import rs.fishingshop.catalog.ProductVariantRepository;
import rs.fishingshop.events.OrderPlaced;
import rs.fishingshop.events.VariantSoldOut;
import rs.fishingshop.jobs.JobQueue;
import rs.fishingshop.jobs.JobType;
import rs.fishingshop.stock.StockService;

@Service
public class OrderService {

    static final int MAX_LINE_QUANTITY = 99;

    private static final BigDecimal MAX_TOTAL = new BigDecimal("9999999999.99");

    private final CustomerOrderRepository orders;
    private final ProductVariantRepository variants;
    private final StockService stock;
    private final JobQueue jobs;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;

    public OrderService(
            CustomerOrderRepository orders, ProductVariantRepository variants, StockService stock, JobQueue jobs,
            ApplicationEventPublisher events, JdbcTemplate jdbc) {
        this.orders = orders;
        this.variants = variants;
        this.stock = stock;
        this.jobs = jobs;
        this.events = events;
        this.jdbc = jdbc;
    }

    record Placement(OrderResponse order, boolean replayed) {
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        return place(request, null).order();
    }

    @Transactional
    Placement place(CreateOrderRequest request, String idempotencyKey) {
        String fingerprint = null;
        if (idempotencyKey != null) {
            fingerprint = fingerprint(request);
            Optional<Placement> earlier = earlierOrder(idempotencyKey, fingerprint);
            if (earlier.isPresent()) {
                return earlier.get();
            }
        }

        Map<Long, Integer> quantities = mergeQuantities(request.items());
        Map<Long, ProductVariant> found = variants.findActiveWithProduct(quantities.keySet()).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<Long> unavailable = quantities.keySet().stream().filter(id -> !found.containsKey(id)).toList();
        if (!unavailable.isEmpty()) {
            throw OrderRejectedException.unavailable(unavailable);
        }

        CustomerRequest customer = request.customer();
        CustomerOrder order = new CustomerOrder(
                customer.name().trim(),
                customer.email().trim(),
                blankToNull(customer.phone()),
                customer.shippingAddress().trim());

        quantities.forEach((variantId, quantity) -> {
            ProductVariant variant = found.get(variantId);
            order.addItem(new OrderItem(
                    variant.getId(),
                    variant.getProduct().getName(),
                    variant.getSku(),
                    variant.getPrice(),
                    quantity));
        });

        if (order.getTotal().compareTo(MAX_TOTAL) > 0) {
            throw OrderRejectedException.totalTooLarge();
        }
        if (idempotencyKey != null) {
            order.placedWith(idempotencyKey, fingerprint);
        }

        CustomerOrder saved = orders.save(order);
        List<Long> soldOutMovements = stock.takeForOrder(saved.getId(), quantities);
        jobs.enqueueForOrder(JobType.ORDER_CONFIRMATION_EMAIL, saved.getId());
        events.publishEvent(new OrderPlaced(saved.getId()));
        soldOutMovements.forEach(movement -> events.publishEvent(new VariantSoldOut(movement)));
        return new Placement(OrderResponse.from(saved), false);
    }

    private Optional<Placement> earlierOrder(String idempotencyKey, String fingerprint) {
        // Requests with the same key wait here for each other, so the second finds the first committed order
        // instead of failing on the unique index.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, idempotencyKey);
        return orders.findWithItemsByIdempotencyKey(idempotencyKey).map(order -> {
            if (!fingerprint.equals(order.getIdempotencyFingerprint())) {
                throw OrderRejectedException.idempotencyKeyReused();
            }
            return new Placement(OrderResponse.from(order), true);
        });
    }

    static String fingerprint(CreateOrderRequest request) {
        CustomerRequest customer = request.customer();
        StringBuilder text = new StringBuilder();
        for (String field : new String[] {
                customer.name().trim(), customer.email().trim(), String.valueOf(blankToNull(customer.phone())),
                customer.shippingAddress().trim()}) {
            text.append(field.length()).append(':').append(field).append(';');
        }
        new TreeMap<>(mergeQuantities(request.items()))
                .forEach((variantId, quantity) -> text.append(variantId).append('x').append(quantity).append(';'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<Long, Integer> mergeQuantities(List<OrderItemRequest> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            int merged = quantities.merge(item.variantId(), item.quantity(), Integer::sum);
            if (merged > MAX_LINE_QUANTITY) {
                throw OrderRejectedException.quantityTooLarge(item.variantId(), MAX_LINE_QUANTITY);
            }
        }
        return quantities;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
