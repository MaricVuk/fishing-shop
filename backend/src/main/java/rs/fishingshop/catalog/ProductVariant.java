package rs.fishingshop.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product_variants")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    private String sku;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> attributes;

    private BigDecimal price;

    private boolean active;

    protected ProductVariant() {
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getSku() {
        return sku;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }
}
