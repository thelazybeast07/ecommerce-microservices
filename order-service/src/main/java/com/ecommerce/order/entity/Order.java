package com.ecommerce.order.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Order aggregate root.
 *
 * <p>Unlike Customer/Product (which stand alone), an Order genuinely OWNS its items: an item has
 * no meaning without its order, they are always saved and deleted together, and the total is
 * only correct when computed across all of them. That is what an "aggregate" means in
 * domain-driven design, and it is why this is the one place in the platform with a
 * {@code @OneToMany} cascade.
 *
 * <p>The total is computed here, from the items, never taken from the client.
 */
@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Points into user-service. No foreign key. */
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Embedded
    private ShippingAddress shippingAddress;

    /**
     * cascade = ALL + orphanRemoval: items are persisted and removed with the order.
     * Safe precisely because items belong to this aggregate and nothing else references them.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Order(UUID customerId, String currency, ShippingAddress shippingAddress) {
        this.customerId = customerId;
        this.currency = currency;
        this.shippingAddress = shippingAddress;
        this.status = OrderStatus.PENDING;
        this.totalAmount = BigDecimal.ZERO;
    }

    /**
     * Adds a line and recalculates the total. Prices are supplied by the caller because they
     * come from product-service, never from the client placing the order.
     */
    public void addItem(UUID productId, String productSku, String productName,
                        int quantity, BigDecimal unitPrice) {
        items.add(new OrderItem(this, productId, productSku, productName, quantity, unitPrice));
        recalculateTotal();
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Moves the order to a new status if the state machine allows it.
     *
     * @throws IllegalStateException if the transition is illegal; the service layer translates
     *                               this into an HTTP 409
     */
    public void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Cannot move order from %s to %s".formatted(status, target));
        }
        this.status = target;
    }

    public void cancel() {
        if (!status.isCancellable()) {
            throw new IllegalStateException(
                    "An order in status %s can no longer be cancelled".formatted(status));
        }
        this.status = OrderStatus.CANCELLED;
    }

    /** Defensive copy: callers read the items, they don't mutate the aggregate's internals. */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
