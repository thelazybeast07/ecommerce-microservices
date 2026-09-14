package com.ecommerce.user.dto;

import java.util.List;

/** Customer plus addresses in one call, e.g. for an account page. */
public record CustomerProfileResponse(
        CustomerResponse customer,
        List<AddressResponse> addresses
) {
}
