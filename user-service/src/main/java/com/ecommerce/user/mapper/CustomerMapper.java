package com.ecommerce.user.mapper;

import com.ecommerce.user.dto.AddressResponse;
import com.ecommerce.user.dto.CustomerProfileResponse;
import com.ecommerce.user.dto.CustomerResponse;
import com.ecommerce.user.dto.RegisterCustomerRequest;
import com.ecommerce.user.entity.Customer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hand-written on purpose so every mapping is visible while learning. A MapStruct interface
 * could replace this class later without changing any caller.
 */
@Component
public class CustomerMapper {

    /**
     * The email and hash are passed in already processed: normalising and hashing are business
     * decisions made by the service, not mechanical field copying.
     */
    public Customer toEntity(RegisterCustomerRequest request, String normalizedEmail, String passwordHash) {
        return new Customer(
                request.firstName(),
                request.lastName(),
                normalizedEmail,
                request.phone(),
                passwordHash);
    }

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }

    public CustomerProfileResponse toProfileResponse(Customer customer, List<AddressResponse> addresses) {
        return new CustomerProfileResponse(toResponse(customer), addresses);
    }
}
