package com.nebula.store.web.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Aggregated numbers for the admin dashboard.
 *
 * @param totalOrders       every order ever placed, including cancelled ones
 * @param revenue           sum of all non-cancelled orders
 * @param unitsSold         sum of quantities over non-cancelled orders
 * @param averageOrderValue {@code revenue} divided by the number of non-cancelled orders
 * @param byStatus          order count per status name, in enum declaration order
 */
public record AdminStats(
        long totalOrders,
        BigDecimal revenue,
        long unitsSold,
        BigDecimal averageOrderValue,
        Map<String, Long> byStatus
) {
}
