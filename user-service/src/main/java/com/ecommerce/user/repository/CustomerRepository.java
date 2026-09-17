package com.ecommerce.user.repository;

import com.ecommerce.user.entity.Customer;
import com.ecommerce.user.entity.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByEmail(String email);

    /** Used by login. Email is stored lower-case, so callers must normalise before calling. */
    Optional<Customer> findByEmail(String email);

    Page<Customer> findAllByStatus(CustomerStatus status, Pageable pageable);
}
