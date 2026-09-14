package com.ecommerce.order.dto;

import com.ecommerce.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * List-view projection: no items.
 *
 * <p>This is not just tidiness. The repository's paged query deliberately does not fetch items
 * (fetch-joining a collection with pagination makes Hibernate paginate in memory), so a summary
 * DTO that never touches {@code order.getItems()} keeps that contract honest.
 */
public record OrderSummaryResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {
}
