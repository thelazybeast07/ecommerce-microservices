package com.ecommerce.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/** Mirrors product-service's trimmed lookup projection. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(
        UUID id,
        String sku,
        String name,
        BigDecimal price,
        String currency,
        String status
) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
