package com.ecommerce.product.dto;

import com.ecommerce.product.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Trimmed projection for order-service: exactly what is needed to validate and price a cart
 * line, and nothing else. A separate, smaller contract means the catalogue's presentation
 * fields can change without affecting order-service.
 */
public record ProductLookupResponse(
        UUID id,
        String sku,
        String name,
        BigDecimal price,
        String currency,
        ProductStatus status
) {
}
