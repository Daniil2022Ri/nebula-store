package com.nebula.store.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Snapshot of a shopping cart.
 *
 * @param id        cart identifier the client must send back in the {@code X-Cart-Id} header
 * @param items     cart lines in the order they were added
 * @param itemCount total number of units (not distinct products)
 * @param subtotal  sum of all line totals
 */
public record CartDto(String id, List<CartLineDto> items, int itemCount, BigDecimal subtotal) {
}
