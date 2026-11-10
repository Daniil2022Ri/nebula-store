package com.nebula.store.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/cart/items}.
 */
public record AddItemRequest(
        @NotNull(message = "Укажите товар")
        Long productId,

        @Min(value = 1, message = "Количество должно быть не меньше 1")
        @Max(value = 99, message = "Максимум 99 штук одного товара")
        int quantity
) {
}
