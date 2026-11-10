package com.nebula.store.web.dto;

import com.nebula.store.domain.Order;
import com.nebula.store.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Public representation of a placed order.
 */
public record OrderDto(
        Long id,
        String code,
        OrderStatus status,
        String statusTitle,
        BigDecimal total,
        Instant createdAt,
        String customerName,
        String email,
        String address,
        String comment,
        int itemCount,
        List<OrderItemDto> items
) {

    public static OrderDto of(Order order) {
        return new OrderDto(
                order.getId(),
                order.getCode(),
                order.getStatus(),
                order.getStatus().getTitle(),
                order.getTotal(),
                order.getCreatedAt(),
                order.getCustomerName(),
                order.getEmail(),
                order.getAddress(),
                order.getComment(),
                order.itemCount(),
                order.getItems().stream().map(OrderItemDto::of).toList());
    }
}
