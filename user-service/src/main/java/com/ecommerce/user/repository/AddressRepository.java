package com.ecommerce.user.repository;

import com.ecommerce.user.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    /** Derived query: "CustomerId" resolves to the nested path address.customer.id. */
    List<Address> findAllByCustomerId(UUID customerId);

    /**
     * Always look an address up together with its owner. Finding by address id alone would let
     * /customers/A/addresses/{id} return an address that belongs to customer B.
     */
    Optional<Address> findByIdAndCustomerId(UUID id, UUID customerId);
}
