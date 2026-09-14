package com.ecommerce.order.entity;

import java.util.Set;

/**
 * Order lifecycle, with the legal transitions encoded as a state machine.
 *
 * <p>Putting the rules here rather than in a chain of if-statements in the service means there
 * is exactly one place to read, test and change them.
 *
 * <pre>
 * PENDING -> CONFIRMED -> PAYMENT_PENDING -> PAID -> PROCESSING -> SHIPPED -> DELIVERED
 *   |            |              |
 *   +------------+--------------+--> CANCELLED
 * </pre>
 *
 * <p>Cancelling after PAID is deliberately not allowed: money has changed hands, so it needs a
 * refund flow, which belongs to a later payments phase rather than a silent status flip.
 */
public enum OrderStatus {

    PENDING,
    CONFIRMED,
    PAYMENT_PENDING,
    PAID,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private static final Set<OrderStatus> CANCELLABLE =
            Set.of(PENDING, CONFIRMED, PAYMENT_PENDING);

    /** Statuses this one may move to directly. Empty for terminal states. */
    public Set<OrderStatus> allowedNextStatuses() {
        return switch (this) {
            case PENDING         -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED       -> Set.of(PAYMENT_PENDING, CANCELLED);
            case PAYMENT_PENDING -> Set.of(PAID, CANCELLED);
            case PAID            -> Set.of(PROCESSING);
            case PROCESSING      -> Set.of(SHIPPED);
            case SHIPPED         -> Set.of(DELIVERED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return allowedNextStatuses().contains(target);
    }

    public boolean isCancellable() {
        return CANCELLABLE.contains(this);
    }

    public boolean isTerminal() {
        return allowedNextStatuses().isEmpty();
    }
}
