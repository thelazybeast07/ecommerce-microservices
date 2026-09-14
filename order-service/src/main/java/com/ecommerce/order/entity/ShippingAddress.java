package com.ecommerce.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A copy of the customer's address as it was when the order was placed.
 *
 * <p>{@code @Embeddable} means these fields live as columns on the {@code orders} table rather
 * than in a table of their own - they have no identity or lifecycle apart from the order.
 *
 * <p>{@code sourceAddressId} records WHICH address in user-service this came from, for tracing.
 * It is a plain UUID with no foreign key, and the address it points at may since have been
 * edited or deleted. That is exactly why the values themselves are copied here.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingAddress {

    @Column(name = "shipping_address_id")
    private UUID sourceAddressId;

    @Column(name = "shipping_line1", nullable = false, length = 255)
    private String line1;

    @Column(name = "shipping_line2", length = 255)
    private String line2;

    @Column(name = "shipping_city", nullable = false, length = 100)
    private String city;

    @Column(name = "shipping_state", length = 100)
    private String state;

    @Column(name = "shipping_postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "shipping_country", nullable = false, length = 2)
    private String country;

    public ShippingAddress(UUID sourceAddressId, String line1, String line2, String city,
                           String state, String postalCode, String country) {
        this.sourceAddressId = sourceAddressId;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
    }
}
