package com.ecommerce.order.dto;

import java.util.UUID;

/** The address snapshot stored on the order, not a live read from user-service. */
public record ShippingAddressResponse(
        UUID sourceAddressId,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country
) {
}
