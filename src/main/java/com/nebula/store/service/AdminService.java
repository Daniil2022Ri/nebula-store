package com.nebula.store.service;

import com.nebula.store.domain.Order;
import com.nebula.store.domain.OrderItem;
import com.nebula.store.domain.OrderStatus;
import com.nebula.store.error.NotFoundException;
import com.nebula.store.repository.OrderRepository;
import com.nebula.store.repository.ProductRepository;
import com.nebula.store.web.dto.AdminOrderPage;
import com.nebula.store.web.dto.AdminStats;
import com.nebula.store.web.dto.OrderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Back-office operations: order listing, status transitions and dashboard numbers.
 */
@Service
@Transactional(readOnly = true)
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final OrderRepository orders;
    private final ProductRepository products;

    public AdminService(OrderRepository orders, ProductRepository products) {
        this.orders = orders;
        this.products = products;
    }

    public AdminOrderPage list(OrderStatus status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<Order> result = status == null
                ? orders.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : orders.findByStatusOrderByCreatedAtDescIdDesc(status, pageable);

        List<OrderDto> items = result.getContent().stream().map(OrderDto::of).toList();
        return new AdminOrderPage(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public OrderDto get(String code) {
        return OrderDto.of(requireOrder(code));
    }

    /**
     * Moves an order forward through its lifecycle. Cancelling an order returns the
     * purchased units to the catalog.
     */
    @Transactional
    public OrderDto updateStatus(String code, OrderStatus target) {
        Order order = requireOrder(code);
        if (order.getStatus() == target) {
            return OrderDto.of(order);
        }
        if (!order.getStatus().canTransitionTo(target)) {
            throw new IllegalStateException("Из статуса «" + order.getStatus().getTitle()
                    + "» нельзя перейти в «" + target.getTitle() + "»");
        }
        if (target.isCancelled()) {
            restoreStock(order);
        }
        OrderStatus previous = order.getStatus();
        order.setStatus(target);
        log.info("Order {} moved from {} to {}", order.getCode(), previous, target);
        return OrderDto.of(order);
    }

    public AdminStats stats() {
        long total = orders.count();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            byStatus.put(status.name(), orders.countByStatus(status));
        }

        BigDecimal revenue = Optional.ofNullable(orders.sumTotalExcluding(OrderStatus.CANCELLED))
                .orElse(BigDecimal.ZERO);
        long unitsSold = Optional.ofNullable(orders.sumUnitsExcluding(OrderStatus.CANCELLED))
                .orElse(0L);
        long billableOrders = total - byStatus.getOrDefault(OrderStatus.CANCELLED.name(), 0L);
        BigDecimal average = billableOrders == 0
                ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(billableOrders), 2, RoundingMode.HALF_UP);

        return new AdminStats(total, revenue, unitsSold, average, byStatus);
    }

    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            products.findById(item.getProductId()).ifPresentOrElse(
                    product -> product.restoreStock(item.getQuantity()),
                    () -> log.warn("Cannot restore stock for product {} of order {}",
                            item.getProductId(), order.getCode()));
        }
    }

    private Order requireOrder(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return orders.findByCode(normalized)
                .orElseThrow(() -> new NotFoundException("Заказ с номером " + normalized + " не найден"));
    }
}
