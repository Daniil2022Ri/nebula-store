package com.nebula.store.service;

import com.nebula.store.domain.Order;
import com.nebula.store.domain.OrderItem;
import com.nebula.store.domain.Product;
import com.nebula.store.error.NotFoundException;
import com.nebula.store.repository.OrderRepository;
import com.nebula.store.web.dto.CartDto;
import com.nebula.store.web.dto.CartLineDto;
import com.nebula.store.web.dto.CheckoutRequest;
import com.nebula.store.web.dto.OrderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a cart into an order.
 *
 * <p>Checkout is transactional: stock is validated and decremented together with saving the
 * order, so a failure leaves both the catalog and the cart untouched. Prices are copied into
 * {@link OrderItem}s to freeze the purchase history.</p>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final DateTimeFormatter CODE_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orders;
    private final ProductService productService;
    private final CartService cartService;

    public OrderService(OrderRepository orders, ProductService productService, CartService cartService) {
        this.orders = orders;
        this.productService = productService;
        this.cartService = cartService;
    }

    @Transactional
    public OrderDto checkout(String cartId, CheckoutRequest request) {
        CartDto cart = cartService.view(cartId);
        if (cart.items().isEmpty()) {
            throw new IllegalStateException("Корзина пуста — добавьте товары перед оформлением");
        }

        Order order = new Order(
                nextCode(),
                request.name().trim(),
                request.email().trim().toLowerCase(),
                request.address().trim(),
                normalizeComment(request.comment()));

        Map<Long, Product> catalog = productService.findActiveByIds(
                cart.items().stream().map(CartLineDto::productId).toList());

        for (CartLineDto line : cart.items()) {
            Product product = catalog.get(line.productId());
            if (product == null) {
                throw new IllegalStateException("Товар «" + line.name() + "» больше недоступен");
            }
            product.decreaseStock(line.quantity());
            order.addItem(new OrderItem(
                    product.getId(),
                    product.getSlug(),
                    product.getName(),
                    product.getIcon(),
                    product.getPrice(),
                    line.quantity()));
        }

        order.recalculateTotal();
        Order saved = orders.save(order);
        cartService.clear(cartId);

        log.info("Order {} created: {} units, total {}", saved.getCode(), saved.itemCount(), saved.getTotal());
        return OrderDto.of(saved);
    }

    @Transactional(readOnly = true)
    public OrderDto get(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return orders.findByCode(normalized)
                .map(OrderDto::of)
                .orElseThrow(() -> new NotFoundException("Заказ с номером " + normalized + " не найден"));
    }

    private String nextCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = "NB-" + CODE_DATE.format(Instant.now()) + "-" + suffix(4);
            if (!orders.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Не удалось подобрать номер заказа, попробуйте ещё раз");
    }

    private static String suffix(int length) {
        StringBuilder suffix = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            suffix.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return suffix.toString();
    }

    private static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
