package com.ecommerce.user.dto;

import com.ecommerce.user.entity.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

/** What the API returns for a customer. Note what is NOT here: passwordHash and version. */
public record CustomerResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        CustomerStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
