package rs.fishingshop.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "variant_id")
    private Long variantId;

    private int quantity;

    @Version
    private Long version;

    protected Inventory() {
    }

    public Long getVariantId() {
        return variantId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void increase(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Cannot add " + amount);
        }
        quantity = Math.addExact(quantity, amount);
    }

    public void setCounted(int counted) {
        if (counted < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        quantity = counted;
    }

    public void decrease(int amount) {
        if (amount <= 0 || amount > quantity) {
            throw new IllegalArgumentException("Cannot take " + amount + " of " + quantity);
        }
        quantity -= amount;
    }
}
