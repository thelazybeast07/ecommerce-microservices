package com.ecommerce.user.service;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.AddressResponse;
import com.ecommerce.user.entity.Address;
import com.ecommerce.user.entity.Customer;
import com.ecommerce.user.exception.InvalidResourceStateException;
import com.ecommerce.user.exception.ResourceNotFoundException;
import com.ecommerce.user.mapper.AddressMapper;
import com.ecommerce.user.repository.AddressRepository;
import com.ecommerce.user.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Address use cases. Rule shared with CustomerService: inactive customers are read-only.
 *
 * <p>Addresses are hard-deleted. That is safe because the order-service copies the shipping
 * address into the order when it is placed, so no order depends on this row continuing to exist.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressService {

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final AddressMapper addressMapper;

    @Transactional
    public AddressResponse addAddress(UUID customerId, AddressRequest request) {
        Customer customer = findActiveCustomer(customerId);
        Address saved = addressRepository.save(addressMapper.toEntity(request, customer));
        return addressMapper.toResponse(saved);
    }

    public List<AddressResponse> getAddresses(UUID customerId) {
        // Without this check an unknown customer would return 200 [] instead of 404.
        if (!customerRepository.existsById(customerId)) {
            throw ResourceNotFoundException.of("Customer", customerId);
        }
        return addressRepository.findAllByCustomerId(customerId).stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    public AddressResponse getAddress(UUID customerId, UUID addressId) {
        return addressMapper.toResponse(findAddress(customerId, addressId));
    }

    @Transactional
    public AddressResponse updateAddress(UUID customerId, UUID addressId, AddressRequest request) {
        findActiveCustomer(customerId);
        Address address = findAddress(customerId, addressId);
        addressMapper.applyRequest(request, address); // dirty checking persists this at commit
        return addressMapper.toResponse(address);
    }

    @Transactional
    public void deleteAddress(UUID customerId, UUID addressId) {
        findActiveCustomer(customerId);
        addressRepository.delete(findAddress(customerId, addressId));
    }

    private Customer findActiveCustomer(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", customerId));
        if (!customer.isActive()) {
            throw new InvalidResourceStateException(
                    "Customer '%s' is inactive and cannot be modified".formatted(customerId));
        }
        return customer;
    }

    private Address findAddress(UUID customerId, UUID addressId) {
        return addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address with id '%s' was not found for customer '%s'".formatted(addressId, customerId)));
    }
}
