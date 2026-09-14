package com.ecommerce.order.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The state machine gets its own test class because it is pure logic with no dependencies -
 * exactly the kind of rule that is cheap to test exhaustively and expensive to get wrong.
 */
class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @CsvSource({
            "PENDING, CONFIRMED",
            "PENDING, CANCELLED",
            "CONFIRMED, PAYMENT_PENDING",
            "CONFIRMED, CANCELLED",
            "PAYMENT_PENDING, PAID",
            "PAYMENT_PENDING, CANCELLED",
            "PAID, PROCESSING",
            "PROCESSING, SHIPPED",
            "SHIPPED, DELIVERED"
    })
    void legalTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "PENDING, SHIPPED",          // no skipping ahead
            "PENDING, DELIVERED",
            "PAID, CANCELLED",           // needs a refund flow, not a status flip
            "SHIPPED, CANCELLED",
            "DELIVERED, PROCESSING",     // no going backwards
            "CANCELLED, PENDING",        // terminal
            "DELIVERED, CANCELLED"
    })
    void illegalTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    @DisplayName("only pre-payment statuses are cancellable")
    void cancellableStatuses() {
        assertThat(OrderStatus.PENDING.isCancellable()).isTrue();
        assertThat(OrderStatus.CONFIRMED.isCancellable()).isTrue();
        assertThat(OrderStatus.PAYMENT_PENDING.isCancellable()).isTrue();

        assertThat(OrderStatus.PAID.isCancellable()).isFalse();
        assertThat(OrderStatus.SHIPPED.isCancellable()).isFalse();
        assertThat(OrderStatus.DELIVERED.isCancellable()).isFalse();
        assertThat(OrderStatus.CANCELLED.isCancellable()).isFalse();
    }

    @Test
    void terminalStatusesHaveNoOnwardTransitions() {
        assertThat(OrderStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("no status can transition to itself")
    void noSelfTransitions(OrderStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }
}
