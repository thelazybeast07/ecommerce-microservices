package com.ecommerce.user.dto;

import com.ecommerce.user.entity.AddressType;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        UUID customerId,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        AddressType addressType
) {
}
