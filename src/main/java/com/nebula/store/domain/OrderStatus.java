package com.nebula.store.domain;

/**
 * Lifecycle of an order.
 *
 * <p>Orders move forward one step at a time and may be cancelled until they are delivered:</p>
 *
 * <pre>
 * NEW ──▶ PAID ──▶ SHIPPED ──▶ DELIVERED
 *  │       │         │
 *  └───────┴─────────┴──▶ CANCELLED
 * </pre>
 */
public enum OrderStatus {

    NEW("Новый"),
    PAID("Оплачен"),
    SHIPPED("Отправлен"),
    DELIVERED("Доставлен"),
    CANCELLED("Отменён");

    private final String title;

    OrderStatus(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /** Whether an order may move from this status to {@code target}. */
    public boolean canTransitionTo(OrderStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case NEW -> target == PAID || target == CANCELLED;
            case PAID -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED || target == CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    public boolean isCancelled() {
        return this == CANCELLED;
    }
}
