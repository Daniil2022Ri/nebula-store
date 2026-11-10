package com.nebula.store.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A placed order.
 *
 * <p>Order lines store a <em>snapshot</em> of the product: name, slug, icon and unit price
 * are copied at checkout time, so later catalog changes never rewrite order history.</p>
 */
@Entity
@Table(name = "orders", indexes = @Index(name = "idx_orders_code", columnList = "code", unique = true))
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human friendly identifier shown to the customer, e.g. {@code NB-20260915-7KQ2}. */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false, length = 400)
    private String address;

    @Column(length = 600)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.NEW;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // for JPA
    }

    public Order(String code, String customerName, String email, String address, String comment) {
        this.code = code;
        this.customerName = customerName;
        this.email = email;
        this.address = address;
        this.comment = comment;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /** Recomputes {@link #total} from the current lines. */
    public void recalculateTotal() {
        total = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int itemCount() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public String getComment() {
        return comment;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Order other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
