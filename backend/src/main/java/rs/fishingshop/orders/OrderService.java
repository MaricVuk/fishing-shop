package rs.fishingshop.orders;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.fishingshop.catalog.ProductVariant;
import rs.fishingshop.catalog.ProductVariantRepository;
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

    public OrderService(
            CustomerOrderRepository orders, ProductVariantRepository variants, StockService stock, JobQueue jobs) {
        this.orders = orders;
        this.variants = variants;
        this.stock = stock;
        this.jobs = jobs;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
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

        CustomerOrder saved = orders.save(order);
        stock.takeForOrder(saved.getId(), quantities);
        jobs.enqueueForOrder(JobType.ORDER_CONFIRMATION_EMAIL, saved.getId());
        return OrderResponse.from(saved);
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
