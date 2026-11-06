package rs.fishingshop.stock;

public record StockChange(Long variantId, int before, int after) {

    public boolean changed() {
        return before != after;
    }
}
