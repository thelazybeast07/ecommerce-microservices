package com.ecommerce.product.dto;

import com.ecommerce.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Includes the category id AND name. The id is what other services and clients filter on; the
 * name saves every caller a second request just to render the product.
 */
public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        String currency,
        UUID categoryId,
        String categoryName,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
