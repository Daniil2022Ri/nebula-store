package com.nebula.store.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Customer details submitted when a cart is turned into an order.
 */
public record CheckoutRequest(

        @NotBlank(message = "Укажите имя")
        @Size(max = 120, message = "Имя слишком длинное")
        String name,

        @NotBlank(message = "Укажите email")
        @Email(message = "Некорректный email")
        @Size(max = 160, message = "Email слишком длинный")
        String email,

        @NotBlank(message = "Укажите адрес доставки")
        @Size(min = 6, max = 400, message = "Адрес должен быть от 6 до 400 символов")
        String address,

        @Size(max = 600, message = "Комментарий слишком длинный")
        String comment
) {
}
