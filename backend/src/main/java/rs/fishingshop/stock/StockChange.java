package rs.fishingshop.stock;

public record StockChange(Long variantId, int before, int after, int reserved) {

    public boolean changed() {
        return before != after;
    }
}
