package com.ecommerce.user.service;

import com.ecommerce.user.dto.AddressRequest;
import com.ecommerce.user.dto.AddressResponse;
import com.ecommerce.user.entity.Address;
import com.ecommerce.user.entity.AddressType;
import com.ecommerce.user.entity.Customer;
import com.ecommerce.user.exception.InvalidResourceStateException;
import com.ecommerce.user.exception.ResourceNotFoundException;
import com.ecommerce.user.mapper.AddressMapper;
import com.ecommerce.user.repository.AddressRepository;
import com.ecommerce.user.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static com.ecommerce.user.service.CustomerServiceTest.customerWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private AddressRepository addressRepository;

    private AddressService addressService;

    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        addressService = new AddressService(customerRepository, addressRepository, new AddressMapper());
    }

    @Test
    void addAddress_forActiveCustomer_savesAndReturnsIt() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customerWithId(customerId)));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = addressService.addAddress(customerId, torontoAddress());

        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.city()).isEqualTo("Toronto");
        assertThat(response.addressType()).isEqualTo(AddressType.SHIPPING);
    }

    @Test
    void addAddress_unknownCustomer_throwsNotFound() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.addAddress(customerId, torontoAddress()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(addressRepository, never()).save(any());
    }

    @Test
    void addAddress_inactiveCustomer_isRejected() {
        Customer inactive = customerWithId(customerId);
        inactive.deactivate();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> addressService.addAddress(customerId, torontoAddress()))
                .isInstanceOf(InvalidResourceStateException.class);
        verify(addressRepository, never()).save(any());
    }

    @Test
    void getAddresses_unknownCustomer_throwsNotFoundRatherThanEmptyList() {
        when(customerRepository.existsById(customerId)).thenReturn(false);

        assertThatThrownBy(() -> addressService.getAddresses(customerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAddress_belongingToAnotherCustomer_throwsNotFound() {
        UUID addressId = UUID.randomUUID();
        when(addressRepository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.getAddress(customerId, addressId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(addressId.toString());
    }

    @Test
    void deleteAddress_deletesTheOwnedAddress() {
        UUID addressId = UUID.randomUUID();
        Customer customer = customerWithId(customerId);
        Address address = new Address(customer);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(addressRepository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.of(address));

        addressService.deleteAddress(customerId, addressId);

        verify(addressRepository).delete(address);
    }

    private static AddressRequest torontoAddress() {
        return new AddressRequest("100 King St W", null, "Toronto", "ON", "M5X 1A9", "CA", AddressType.SHIPPING);
    }
}
