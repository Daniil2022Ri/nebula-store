package com.nebula.store.web.dto;

import com.nebula.store.domain.OrderItem;

import java.math.BigDecimal;

/**
 * A line of a placed order, with the price captured at checkout time.
 */
public record OrderItemDto(
        Long productId,
        String slug,
        String name,
        String icon,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {

    public static OrderItemDto of(OrderItem item) {
        return new OrderItemDto(
                item.getProductId(),
                item.getProductSlug(),
                item.getProductName(),
                item.getIcon(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal());
    }
}
