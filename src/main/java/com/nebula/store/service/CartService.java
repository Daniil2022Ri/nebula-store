package com.nebula.store.service;

import com.nebula.store.domain.Cart;
import com.nebula.store.domain.Product;
import com.nebula.store.web.dto.CartDto;
import com.nebula.store.web.dto.CartLineDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * In-memory shopping cart storage.
 *
 * <p>A cart is identified by an opaque id that the client generates once and sends in the
 * {@code X-Cart-Id} header. Carts are kept in a concurrent map, mutated under the cart's own
 * monitor and swept by a scheduled job once they have been idle for longer than
 * {@code app.cart-ttl-minutes}.</p>
 *
 * <p>Prices and stock are always read from the catalog at render time, so a cart cannot
 * display a price that is no longer valid.</p>
 */
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    /** Accepts UUIDs and other reasonably shaped ids, rejects garbage early. */
    private static final Pattern CART_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final int MAX_QUANTITY = 99;

    private final ProductService productService;
    private final Duration ttl;
    private final Map<String, Cart> carts = new ConcurrentHashMap<>();

    public CartService(ProductService productService,
                       @Value("${app.cart-ttl-minutes:720}") long ttlMinutes) {
        this.productService = productService;
        this.ttl = Duration.ofMinutes(Math.max(1, ttlMinutes));
    }

    /** Renders the cart, creating an empty one when the id is unknown. */
    public CartDto view(String cartId) {
        return render(cart(cartId));
    }

    public CartDto addItem(String cartId, long productId, int quantity) {
        requireQuantity(quantity, 1);
        Cart cart = cart(cartId);
        Product product = productService.requireActive(productId);

        synchronized (cart) {
            int target = cart.quantityOf(productId) + quantity;
            if (target > MAX_QUANTITY) {
                throw new IllegalArgumentException("В одной позиции не может быть больше " + MAX_QUANTITY + " штук");
            }
            requireStock(product, target);
            cart.put(productId, target);
        }
        return render(cart);
    }

    /** Sets an absolute quantity; {@code 0} removes the product from the cart. */
    public CartDto setQuantity(String cartId, long productId, int quantity) {
        requireQuantity(quantity, 0);
        Cart cart = cart(cartId);
        Product product = productService.requireActive(productId);

        synchronized (cart) {
            if (quantity == 0) {
                cart.remove(productId);
            } else {
                requireStock(product, quantity);
                cart.put(productId, quantity);
            }
        }
        return render(cart);
    }

    public CartDto removeItem(String cartId, long productId) {
        Cart cart = cart(cartId);
        synchronized (cart) {
            cart.remove(productId);
        }
        return render(cart);
    }

    public CartDto clear(String cartId) {
        Cart cart = cart(cartId);
        synchronized (cart) {
            cart.clear();
        }
        return render(cart);
    }

    /** Drops carts that have been untouched for longer than the configured TTL. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void evictExpiredCarts() {
        Instant now = Instant.now();
        int before = carts.size();
        carts.entrySet().removeIf(entry -> entry.getValue().isExpired(now, ttl));
        int evicted = before - carts.size();
        if (evicted > 0) {
            log.debug("Evicted {} idle carts, {} still active", evicted, carts.size());
        }
    }

    private CartDto render(Cart cart) {
        Map<Long, Integer> lines;
        synchronized (cart) {
            lines = cart.lines();
        }
        if (lines.isEmpty()) {
            return new CartDto(cart.getId(), List.of(), 0, BigDecimal.ZERO);
        }

        Map<Long, Product> catalog = productService.findActiveByIds(lines.keySet());
        List<CartLineDto> items = new ArrayList<>(lines.size());
        BigDecimal subtotal = BigDecimal.ZERO;
        int units = 0;

        for (Map.Entry<Long, Integer> line : lines.entrySet()) {
            Product product = catalog.get(line.getKey());
            if (product == null) {
                continue; // product disappeared from the catalog, drop the line from the view
            }
            int quantity = line.getValue();
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            items.add(new CartLineDto(
                    product.getId(),
                    product.getSlug(),
                    product.getName(),
                    product.getCategory(),
                    product.getIcon(),
                    product.getPrice(),
                    quantity,
                    lineTotal,
                    product.getStock(),
                    product.isAvailable(quantity)));
            subtotal = subtotal.add(lineTotal);
            units += quantity;
        }
        return new CartDto(cart.getId(), List.copyOf(items), units, subtotal);
    }

    private Cart cart(String cartId) {
        return carts.computeIfAbsent(normalize(cartId), Cart::new);
    }

    private String normalize(String cartId) {
        if (cartId == null) {
            return newId();
        }
        String trimmed = cartId.trim();
        return CART_ID_PATTERN.matcher(trimmed).matches() ? trimmed : newId();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static void requireQuantity(int quantity, int minimum) {
        if (quantity < minimum || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Количество должно быть от " + minimum + " до " + MAX_QUANTITY);
        }
    }

    private static void requireStock(Product product, int wanted) {
        if (wanted > product.getStock()) {
            throw new IllegalStateException(
                    "«" + product.getName() + "» в наличии только " + product.getStock() + " шт.");
        }
    }
}
