package com.ecommerce.user.repository;

import com.ecommerce.user.entity.Customer;
import com.ecommerce.user.entity.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByEmail(String email);

    Page<Customer> findAllByStatus(CustomerStatus status, Pageable pageable);
}
