package com.nebula.store.web.dto;

import java.util.List;

/**
 * One page of orders for the admin dashboard.
 */
public record AdminOrderPage(
        List<OrderDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
