package com.ecommerce.user.service;

import com.ecommerce.user.dto.AddressResponse;
import com.ecommerce.user.dto.CustomerProfileResponse;
import com.ecommerce.user.dto.CustomerResponse;
import com.ecommerce.user.dto.PageResponse;
import com.ecommerce.user.dto.RegisterCustomerRequest;
import com.ecommerce.user.dto.UpdateCustomerRequest;
import com.ecommerce.user.entity.Customer;
import com.ecommerce.user.entity.CustomerStatus;
import com.ecommerce.user.exception.DuplicateResourceException;
import com.ecommerce.user.exception.InvalidResourceStateException;
import com.ecommerce.user.exception.ResourceNotFoundException;
import com.ecommerce.user.mapper.AddressMapper;
import com.ecommerce.user.mapper.CustomerMapper;
import com.ecommerce.user.repository.AddressRepository;
import com.ecommerce.user.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Customer use cases. Transaction boundaries live here, and this layer takes DTOs in and
 * returns DTOs out, so entities never leave the service layer.
 *
 * <p>Class-level {@code readOnly = true} is the safe default; methods that write override it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final CustomerMapper customerMapper;
    private final AddressMapper addressMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CustomerResponse register(RegisterCustomerRequest request) {
        String email = normalizeEmail(request.email());

        // Fast, friendly check. The UNIQUE constraint remains the real guarantee under concurrency.
        if (customerRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("A customer with this email is already registered");
        }

        Customer customer = customerMapper.toEntity(request, email, passwordEncoder.encode(request.password()));
        Customer saved = customerRepository.save(customer);

        log.info("Registered customer id={}", saved.getId()); // ids only; no PII in logs
        return customerMapper.toResponse(saved);
    }

    public CustomerResponse getCustomer(UUID id) {
        return customerMapper.toResponse(findCustomer(id));
    }

    public PageResponse<CustomerResponse> listCustomers(CustomerStatus status, Pageable pageable) {
        Page<Customer> page = (status == null)
                ? customerRepository.findAll(pageable)
                : customerRepository.findAllByStatus(status, pageable);
        return PageResponse.from(page.map(customerMapper::toResponse));
    }

    public CustomerProfileResponse getProfile(UUID id) {
        Customer customer = findCustomer(id);
        List<AddressResponse> addresses = addressRepository.findAllByCustomerId(id).stream()
                .map(addressMapper::toResponse)
                .toList();
        return customerMapper.toProfileResponse(customer, addresses);
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID id, UpdateCustomerRequest request) {
        Customer customer = findCustomer(id);
        requireActive(customer);

        customer.updateProfile(request.firstName(), request.lastName(), request.phone());

        // The entity is managed, so dirty checking would save it at commit anyway. We flush now
        // so @LastModifiedDate and @Version are updated BEFORE we build the response;
        // otherwise the client would receive the old updatedAt value.
        Customer updated = customerRepository.saveAndFlush(customer);
        return customerMapper.toResponse(updated);
    }

    /**
     * Soft delete. Order history references this customer, so the row is kept and marked
     * INACTIVE. Idempotent: deactivating an inactive customer is a no-op, which keeps
     * DELETE idempotent as HTTP expects.
     */
    @Transactional
    public void deactivateCustomer(UUID id) {
        Customer customer = findCustomer(id);
        if (customer.isActive()) {
            customer.deactivate();
            log.info("Deactivated customer id={}", id);
        }
    }

    private Customer findCustomer(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
    }

    private static void requireActive(Customer customer) {
        if (!customer.isActive()) {
            throw new InvalidResourceStateException(
                    "Customer '%s' is inactive and cannot be modified".formatted(customer.getId()));
        }
    }

    /** Locale.ROOT avoids locale-specific surprises (e.g. Turkish dotless i) when lower-casing. */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
