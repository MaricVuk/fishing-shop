package rs.fishingshop.orders;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "shipping_address")
    private String shippingAddress;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private BigDecimal total;

    @Column(name = "created_at")
    private Instant createdAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "public_token", insertable = false, updatable = false)
    private String publicToken;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "idempotency_fingerprint")
    private String idempotencyFingerprint;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(String customerName, String customerEmail, String customerPhone, String shippingAddress) {
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.shippingAddress = shippingAddress;
        this.status = OrderStatus.NEW;
        this.total = BigDecimal.ZERO;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void addItem(OrderItem item) {
        item.attachTo(this);
        items.add(item);
        total = total.add(item.lineTotal());
    }

    void placedWith(String idempotencyKey, String idempotencyFingerprint) {
        this.idempotencyKey = idempotencyKey;
        this.idempotencyFingerprint = idempotencyFingerprint;
    }

    public void moveTo(OrderStatus next) {
        if (!status.canBecome(next)) {
            throw new IllegalStateException("Order " + id + " cannot go from " + status + " to " + next);
        }
        status = next;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getPublicToken() {
        return publicToken;
    }

    public String getPublicNumber() {
        return PublicOrderNumber.format(publicToken);
    }

    String getIdempotencyFingerprint() {
        return idempotencyFingerprint;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }
}
