package com.nebula.store.web.dto;

import com.nebula.store.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/admin/orders/{code}/status}.
 */
public record StatusUpdateRequest(
        @NotNull(message = "Укажите новый статус")
        OrderStatus status
) {
}
