package com.nebula.store.web.dto;

import java.math.BigDecimal;

/**
 * One line of the shopping cart, enriched with live catalog data.
 */
public record CartLineDto(
        Long productId,
        String slug,
        String name,
        String category,
        String icon,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        int stock,
        boolean inStock
) {
}
