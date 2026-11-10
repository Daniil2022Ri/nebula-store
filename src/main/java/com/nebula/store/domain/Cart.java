package com.nebula.store.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A shopping cart.
 *
 * <p>Carts are intentionally <em>not</em> persisted: they live in
 * {@link com.nebula.store.service.CartService} and expire after a period of inactivity.
 * Only the product id and quantity are stored — names, prices and stock are always read
 * from the catalog when the cart is rendered, so a cart never shows stale prices.</p>
 */
public class Cart {

    private final String id;
    private final Map<Long, Integer> lines = new LinkedHashMap<>();
    private Instant updatedAt = Instant.now();

    public Cart(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** Immutable copy of the cart contents, in insertion order. */
    public Map<Long, Integer> lines() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(lines));
    }

    public int quantityOf(long productId) {
        return lines.getOrDefault(productId, 0);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public int totalUnits() {
        return lines.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Stores the quantity for a product, dropping the line when it becomes zero. */
    public void put(long productId, int quantity) {
        if (quantity <= 0) {
            lines.remove(productId);
        } else {
            lines.put(productId, quantity);
        }
        touch();
    }

    public void remove(long productId) {
        lines.remove(productId);
        touch();
    }

    public void clear() {
        lines.clear();
        touch();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isExpired(Instant now, Duration ttl) {
        return updatedAt.plus(ttl).isBefore(now);
    }

    private void touch() {
        updatedAt = Instant.now();
    }
}
