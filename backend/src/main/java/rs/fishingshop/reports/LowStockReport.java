package rs.fishingshop.reports;

import java.util.List;
import java.util.Map;

public record LowStockReport(int threshold, long total, List<Item> items) {

    public record Item(
            long variantId,
            long productId,
            String productName,
            String sku,
            Map<String, String> attributes,
            int quantity) {
    }
}
