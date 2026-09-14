package com.ecommerce.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Our view of user-service's customer response.
 *
 * <p>Only the fields order-service actually needs are declared, and
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} makes unknown fields harmless. That is
 * what lets user-service add fields to its response without breaking this service - a
 * deliberately tolerant reader.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerDto(UUID id, String status) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
