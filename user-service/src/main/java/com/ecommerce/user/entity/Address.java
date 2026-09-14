package com.ecommerce.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A customer's postal address.
 *
 * <p>Unlike {@link Customer}, an address has no lifecycle rules beyond field validation, so
 * plain setters on the detail fields are used. The owning customer and the id are fixed at
 * creation and have no setters.
 *
 * <p>The relationship is unidirectional (Address -> Customer). Customer has no
 * {@code List<Address>}, which avoids accidentally loading every address whenever a
 * customer is read.
 */
@Entity
@Table(name = "addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Setter
    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    @Setter
    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Setter
    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Setter
    @Column(name = "state", length = 100)
    private String state;

    @Setter
    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    /** ISO 3166-1 alpha-2, e.g. "CA", "IN", "GB". */
    @Setter
    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 20)
    private AddressType addressType;

    public Address(Customer customer) {
        this.customer = customer;
    }
}
