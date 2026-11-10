package com.nebula.store.domain;

/**
 * Lifecycle of an order. New orders start as {@link #NEW}; payment and delivery
 * transitions are handled by the admin side (stage 4).
 */
public enum OrderStatus {

    NEW("Новый"),
    PAID("Оплачен"),
    SHIPPED("Отправлен"),
    CANCELLED("Отменён");

    private final String title;

    OrderStatus(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
