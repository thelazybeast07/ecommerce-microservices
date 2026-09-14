package com.ecommerce.product.dto;

import com.ecommerce.product.entity.CategoryStatus;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description,
        CategoryStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
