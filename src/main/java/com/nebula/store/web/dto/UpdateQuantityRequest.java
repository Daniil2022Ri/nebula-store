package com.nebula.store.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request body for {@code PUT /api/cart/items/{productId}}.
 * A quantity of {@code 0} removes the line.
 */
public record UpdateQuantityRequest(
        @Min(value = 0, message = "Количество не может быть отрицательным")
        @Max(value = 99, message = "Максимум 99 штук одного товара")
        int quantity
) {
}
